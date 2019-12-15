package sec
package core

import java.util.UUID
import java.time.ZonedDateTime
import cats.Show
import cats.implicits._
import scodec.bits.ByteVector

//======================================================================================================================

sealed trait Event
object Event {

  implicit final class EventOps(val e: Event) extends AnyVal {

    def fold[A](f: EventRecord => A, g: ResolvedEvent => A): A = e match {
      case er: EventRecord   => f(er)
      case re: ResolvedEvent => g(re)
    }

    def streamId: String          = e.fold(_.streamId, _.event.streamId)
    def number: EventNumber.Exact = e.fold(_.number, _.event.number)
    def position: Position.Exact  = e.fold(_.position, _.event.position)
    def eventData: EventData      = e.fold(_.eventData, _.event.eventData)
    def record: EventRecord       = e.fold(identity, _.link)
    def created: ZonedDateTime    = e.fold(_.created, _.event.created)
  }

  implicit val showForEvent: Show[Event] = Show.show[Event] {
    case er: EventRecord   => er.show
    case re: ResolvedEvent => re.show
  }

}

final case class EventRecord(
  streamId: String, // Strong types: User & SystemStreams,
  number: EventNumber.Exact,
  position: Position.Exact,
  eventData: EventData,
  created: ZonedDateTime
) extends Event

object EventRecord {

  implicit final class EventRecordOps(val e: EventRecord) extends AnyVal {

    def createLink(eventId: UUID): Attempt[EventData] =
      Either.cond(e.eventData.eventType != EventType.LinkTo, (), "Linking to a link is not supported.") >> {
        Content.binary(s"${e.number.value}@${e.streamId}") >>= { data =>
          EventData(EventType.LinkTo, eventId, data, Content.BinaryEmpty)
        }
      }
  }

  ///

  implicit val showForEventRecord: Show[EventRecord] = Show.show[EventRecord] { er =>
    s"""
       |EventRecord(
       |  streamId = ${er.streamId}, 
       |  number   = ${er.number.show},
       |  position = ${er.position.show},
       |  data     = ${er.eventData.data.show}, 
       |  metadata = ${er.eventData.metadata.show}, 
       |  created  = ${er.created}
       |)
       |""".stripMargin
  }

}

final case class ResolvedEvent(
  event: EventRecord,
  link: EventRecord
) extends Event

object ResolvedEvent {

  implicit val showForResolvedEvent: Show[ResolvedEvent] = Show.show[ResolvedEvent] { re =>
    s"""
         |ResolvedEvent(
         |  event = ${re.event.show},
         |  link  = ${re.link.show}
         |)
         |""".stripMargin
  }

}

//======================================================================================================================

sealed trait EventType
object EventType {

  sealed trait SystemType     extends EventType
  case object StreamDeleted   extends SystemType
  case object StatsCollection extends SystemType
  case object LinkTo          extends SystemType
  case object StreamMetadata  extends SystemType
  case object Settings        extends SystemType
  case object UserCreated     extends SystemType
  case object UserUpdated     extends SystemType
  case object PasswordChanged extends SystemType

  sealed abstract case class SystemDefined(name: String) extends SystemType
  sealed abstract case class UserDefined(name: String)   extends EventType

  def apply(name: String): Attempt[UserDefined] = userDefined(name)

  ///

  private val guardNonEmpty: String => Attempt[String] = name =>
    Either.fromOption(Option(name).filter(_.nonEmpty), "Event type name cannot be empty")

  private val guardNonSystemType: String => Attempt[String] = n =>
    if (n.startsWith("$")) "Event type names starting with $ are reserved to system types".asLeft else n.asRight

  private[sec] def systemDefined(name: String): Attempt[SystemDefined] =
    guardNonEmpty(name) >>= (n => new SystemDefined(n) {}.asRight)

  private[sec] def userDefined(name: String): Attempt[UserDefined] =
    guardNonEmpty(name) >>= guardNonSystemType >>= (n => new UserDefined(n) {}.asRight)

  ///

  private[sec] def toStr: EventType => String = {
    case StreamDeleted    => systemTypes.StreamDeleted
    case StatsCollection  => systemTypes.StatsCollection
    case LinkTo           => systemTypes.LinkTo
    case StreamMetadata   => systemTypes.StreamMetadata
    case Settings         => systemTypes.Settings
    case UserCreated      => systemTypes.UserCreated
    case UserUpdated      => systemTypes.UserUpdated
    case PasswordChanged  => systemTypes.PasswordChanged
    case SystemDefined(t) => t
    case UserDefined(t)   => t
  }

  private[sec] def fromStr: String => Attempt[EventType] = {
    case systemTypes.StreamDeleted   => StreamDeleted.asRight
    case systemTypes.StatsCollection => StatsCollection.asRight
    case systemTypes.LinkTo          => LinkTo.asRight
    case systemTypes.StreamMetadata  => StreamMetadata.asRight
    case systemTypes.Settings        => Settings.asRight
    case systemTypes.UserCreated     => UserCreated.asRight
    case systemTypes.UserUpdated     => UserUpdated.asRight
    case systemTypes.PasswordChanged => PasswordChanged.asRight
    case sd if sd.startsWith("$")    => systemDefined(sd)
    case ud                          => userDefined(ud)
  }

  private object systemTypes {

    final val StreamDeleted: String   = "$streamDeleted"
    final val StatsCollection: String = "$statsCollected"
    final val LinkTo: String          = "$>"
    final val StreamMetadata: String  = "$metadata"
    final val Settings: String        = "$settings"
    final val UserCreated: String     = "$UserCreated"
    final val UserUpdated: String     = "$UserUpdated"
    final val PasswordChanged: String = "$PasswordChanged"

  }

  ///

  implicit val showForEventType: Show[EventType] = Show.show[EventType](toStr)

}

//======================================================================================================================

sealed abstract case class EventData(
  eventType: EventType,
  eventId: UUID,
  data: Content,
  metadata: Content
)

object EventData {

  def apply(eventType: String, eventId: UUID, data: Content): Attempt[EventData] =
    EventData(eventType, eventId, data, Content(ByteVector.empty, data.contentType))

  def apply(eventType: String, eventId: UUID, data: Content, metadata: Content): Attempt[EventData] =
    EventType(eventType) >>= (EventData(_, eventId, data, metadata))

  private[sec] def apply(et: EventType, eventId: UUID, data: Content): Attempt[EventData] =
    EventData(et, eventId, data, Content(ByteVector.empty, data.contentType))

  private[sec] def apply(et: EventType, eventId: UUID, data: Content, metadata: Content): Attempt[EventData] =
    if (data.contentType == metadata.contentType) new EventData(et, eventId, data, metadata) {}.asRight
    else "Different content types for data & metadata is not supported.".asLeft

  private[sec] def json(et: EventType, eventId: UUID, data: ByteVector, metadata: ByteVector): Attempt[EventData] =
    EventData(et, eventId, Content(data, Content.Type.Json), Content(metadata, Content.Type.Json))

  private[sec] def binary(et: EventType, eventId: UUID, data: ByteVector, metadata: ByteVector): Attempt[EventData] =
    EventData(et, eventId, Content(data, Content.Type.Binary), Content(metadata, Content.Type.Binary))

  ///

  implicit class EventDataOps(ed: EventData) {
    def isJson: Boolean = ed.data.contentType.isJson
  }

}

//======================================================================================================================

final case class Content(
  bytes: ByteVector,
  contentType: Content.Type
)

object Content {

  sealed trait Type
  object Type {

    case object Binary extends Type
    case object Json   extends Type

    final implicit class TypeOps(val tpe: Type) extends AnyVal {

      def fold[A](binary: => A, json: => A): A = tpe match {
        case Binary => binary
        case Json   => json
      }

      def isJson: Boolean   = tpe.fold(false, true)
      def isBinary: Boolean = tpe.fold(true, false)
    }

    implicit val showForType: Show[Type] = Show.show[Type] {
      case Binary => "Binary"
      case Json   => "Json"
    }

  }

  ///

  def empty(t: Type): Content = Content(ByteVector.empty, t)
  val BinaryEmpty: Content    = empty(Type.Binary)
  val JsonEmpty: Content      = empty(Type.Json)

  def apply(data: String, ct: Type): Attempt[Content] =
    ByteVector.encodeUtf8(data).map(Content(_, ct)).leftMap(_.getMessage)

  def binary(data: String): Attempt[Content] = Content(data, Type.Binary)
  def json(data: String): Attempt[Content]   = Content(data, Type.Json)

  ///

  implicit val showByteVector: Show[ByteVector] = Show.show[ByteVector] { bv =>
    if (bv.isEmpty) s"empty"
    else if (bv.size < 32) s"${bv.size} bytes, 0x${bv.toHex}"
    else s"${bv.size} bytes, #${bv.hashCode}"
  }

  implicit val showForContent: Show[Content] = Show.show {
    case Content(b, t) if b.isEmpty || t.isBinary => s"${t.show}(${showByteVector.show(b)})"
    case Content(b, t) if t.isJson                => s"${b.decodeUtf8.getOrElse("Failed decoding utf8")}"
  }

}

//======================================================================================================================
