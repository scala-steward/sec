package sec
package grpc
package mapping

import java.util.{UUID => JUUID}
import cats.implicits._
import com.eventstore.client.streams._
import sec.core._
import sec.Streams._

private[sec] object Streams {

  /// Outgoing

  val startOfAll    = ReadReq.Options.AllOptions.AllOptions.Start(ReadReq.Empty())
  val startOfStream = ReadReq.Options.StreamOptions.RevisionOptions.Start(ReadReq.Empty())

  val mapPosition: Position => ReadReq.Options.AllOptions.AllOptions = {
    case Position(c, p) => ReadReq.Options.AllOptions.AllOptions.Position(ReadReq.Options.Position(c, p))
  }

  val mapEventNumber: EventNumber => ReadReq.Options.StreamOptions.RevisionOptions = {
    case EventNumber(nr) => ReadReq.Options.StreamOptions.RevisionOptions.Revision(nr)
  }

  val mapAppendRevision: StreamRevision => AppendReq.Options.ExpectedStreamRevision = {
    case StreamRevision.Exact(v)     => AppendReq.Options.ExpectedStreamRevision.Revision(v)
    case StreamRevision.NoStream     => AppendReq.Options.ExpectedStreamRevision.NoStream(AppendReq.Empty())
    case StreamRevision.StreamExists => AppendReq.Options.ExpectedStreamRevision.StreamExists(AppendReq.Empty())
    case StreamRevision.Any          => AppendReq.Options.ExpectedStreamRevision.Any(AppendReq.Empty())
  }

  val mapDeleteRevision: StreamRevision => DeleteReq.Options.ExpectedStreamRevision = {
    case StreamRevision.Exact(v)     => DeleteReq.Options.ExpectedStreamRevision.Revision(v)
    case StreamRevision.NoStream     => DeleteReq.Options.ExpectedStreamRevision.NoStream(DeleteReq.Empty())
    case StreamRevision.StreamExists => DeleteReq.Options.ExpectedStreamRevision.StreamExists(DeleteReq.Empty())
    case StreamRevision.Any          => DeleteReq.Options.ExpectedStreamRevision.Any(DeleteReq.Empty())
  }

  val mapTombstoneRevision: StreamRevision => TombstoneReq.Options.ExpectedStreamRevision = {
    case StreamRevision.Exact(v) => TombstoneReq.Options.ExpectedStreamRevision.Revision(v)
    case StreamRevision.NoStream => TombstoneReq.Options.ExpectedStreamRevision.NoStream(TombstoneReq.Empty())
    case StreamRevision.StreamExists =>
      TombstoneReq.Options.ExpectedStreamRevision.StreamExists(TombstoneReq.Empty())
    case StreamRevision.Any => TombstoneReq.Options.ExpectedStreamRevision.Any(TombstoneReq.Empty())
  }

  val mapDirection: ReadDirection => ReadReq.Options.ReadDirection = {
    case ReadDirection.Forward  => ReadReq.Options.ReadDirection.Forwards
    case ReadDirection.Backward => ReadReq.Options.ReadDirection.Backwards
  }

  val mapReadEventFilter: Option[EventFilter] => ReadReq.Options.FilterOptionsOneof = {

    import ReadReq.Options.FilterOptionsOneof

    def filter(filter: EventFilter): FilterOptionsOneof = {

      val expr = filter.option.fold(
        nel => ReadReq.Options.FilterOptions.Expression().withPrefix(nel.map(_.value).toList),
        reg => ReadReq.Options.FilterOptions.Expression().withRegex(reg.value)
      )

      val window = filter.maxSearchWindow
        .map(ReadReq.Options.FilterOptions.Window.Max)
        .getOrElse(ReadReq.Options.FilterOptions.Window.Count(ReadReq.Empty()))

      val result = filter.kind match {
        case EventFilter.Stream    => ReadReq.Options.FilterOptions().withStreamName(expr).withWindow(window)
        case EventFilter.EventType => ReadReq.Options.FilterOptions().withEventType(expr).withWindow(window)
      }

      FilterOptionsOneof.Filter(result)
    }

    def noFilter: FilterOptionsOneof = FilterOptionsOneof.NoFilter(ReadReq.Empty())

    _.fold(noFilter)(filter)
  }

  val mapUuidString: JUUID => UUID = j => UUID().withString(j.toString)

  /// Incoming

  // TODO: It is possible that event: None and link: Some(l) when event is deleted.
  //       Consider if worth surfacing as event record
  def mkEvent[F[_]: ErrorM](re: ReadResp.ReadEvent): F[Option[Event]] = {

    // Alternative that selects option of link if event is None.
//    (re.event.traverse(mkEventRecord[F]), re.link.traverse(mkEventRecord[F])).mapN { (e, l) =>
//      e.map(er => l.fold[Event](er)(ResolvedEvent(er, _))).getOrElse(l)
//    }

    re.event.traverse(mkEventRecord[F]) >>= { event =>
      re.link.traverse(mkEventRecord[F]).map { link =>
        event.map(er => link.fold[Event](er)(ResolvedEvent(er, _)))
      }
    }
  }

  def mkEventRecord[F[_]: ErrorM](e: ReadResp.ReadEvent.RecordedEvent): F[EventRecord] = {

    import EventData.{binary, json}
    import Constants.Metadata.{Created, IsJson, Type}

    val streamId    = e.streamName
    val eventNumber = EventNumber.Exact(e.streamRevision)
    val position    = Position.Exact(e.commitPosition, e.preparePosition)
    val data        = e.data.toByteVector
    val customMeta  = e.customMetadata.toByteVector
    val eventId     = e.id.require[F]("UUID") >>= mkUUID[F]
    val eventType   = e.metadata.get(Type).require[F](Type)
    val isJson      = e.metadata.get(IsJson).flatMap(_.toBooleanOption).require[F](IsJson)
    val created     = e.metadata.get(Created).flatMap(_.toLongOption).require[F](Created) >>= fromDateTimeBinaryUTC[F]

    val eventData = (eventId, eventType, isJson).mapN { (i, t, j) =>
      j.fold(json(t, i, data, customMeta), binary(t, i, data, customMeta)).leftMap(ProtoResultError).liftTo[F]
    }

    (eventData.flatten, created).mapN((ed, c) => EventRecord(streamId, eventNumber, position, ed, c))

  }

  def mkUUID[F[_]: ErrorA](uuid: UUID): F[JUUID] = {

    val juuid = uuid.value match {
      case UUID.Value.Structured(v) => new JUUID(v.mostSignificantBits, v.leastSignificantBits).asRight
      case UUID.Value.String(v)     => JUUID.fromString(v).asRight
      case UUID.Value.Empty         => "UUID is missing".asLeft
    }

    juuid.leftMap(ProtoResultError).liftTo[F]
  }

  def mkWriteResult[F[_]: ErrorA](ar: AppendResp): F[WriteResult] = {

    val currentRevision = ar.currentRevisionOptions match {
      case AppendResp.CurrentRevisionOptions.CurrentRevision(v) => StreamRevision.Exact(v).asRight
      case AppendResp.CurrentRevisionOptions.NoStream(_)        => "Did not expect NoStream when using NonEmptyList".asLeft
      case AppendResp.CurrentRevisionOptions.Empty              => "CurrentRevisionOptions is missing".asLeft
    }

    currentRevision.map(WriteResult(_)).leftMap(ProtoResultError).liftTo[F]
  }

  def mkDeleteResult[F[_]: ErrorA](dr: DeleteResp): F[DeleteResult] =
    dr.positionOptions.position
      .map(p => DeleteResult(Position.Exact(p.commitPosition, p.preparePosition)))
      .require[F]("DeleteResp.PositionOptions.Position")

  def mkDeleteResult[F[_]: ErrorA](tr: TombstoneResp): F[DeleteResult] =
    tr.positionOptions.position
      .map(p => DeleteResult(Position.Exact(p.commitPosition, p.preparePosition)))
      .require[F]("TombstoneResp.PositionOptions.Position")

}
