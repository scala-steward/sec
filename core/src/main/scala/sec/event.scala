/*
 * Copyright 2020 Scala EventStoreDB Client
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sec

import java.time.ZonedDateTime
import java.util.UUID

import cats.syntax.all._
import scodec.bits.ByteVector
import sec.utilities.{guardNonEmpty, guardNotStartsWith}

//======================================================================================================================

/** A persisted event in EventStoreDB. There are two variants:
  *
  *   - [[EventRecord]] An event in an event stream.
  *   - [[ResolvedEvent]] A special event that contains a link and a linked event record.
  *
  * @tparam P
  *   Tells whether the event is retrieved from the global stream [[PositionInfo.Global]] or from an individual stream
  *   [[PositionInfo.Local]].
  */
sealed trait Event[P <: PositionInfo]
object Event {

  implicit final class EventOps[P <: PositionInfo](val e: Event[P]) extends AnyVal {

    def fold[A](f: EventRecord[P] => A, g: ResolvedEvent[P] => A): A = e match {
      case er: EventRecord[P]   => f(er)
      case re: ResolvedEvent[P] => g(re)
    }

    /** The stream identifier of the stream the event belongs to.
      */
    def streamId: StreamId = e.fold(_.streamId, _.event.streamId)

    /** The stream position of the event in its stream.
      */
    def streamPosition: StreamPosition.Exact =
      e.fold(_.position.streamPosition, _.event.position.streamPosition)

    /** The payload of the event.
      */
    def eventData: EventData = e.fold(_.eventData, _.event.eventData)

    /** The actual event record of this event. There are two options. Either the record points to a normal event or a
      * resolved event.
      */
    def record: EventRecord[P] = e.fold(identity, _.link)

    /** The creation date of the event in [[java.time.ZonedDateTime]].
      */
    def created: ZonedDateTime = e.fold(_.created, _.event.created)

    def render: String = fold(EventRecord.render, ResolvedEvent.render)
  }

  implicit final class AllEventOps(val e: AllEvent) extends AnyVal {

    /** The position of the event in the global stream.
      */
    def logPosition: LogPosition.Exact = e.fold(_.position.log, _.event.position.log)
  }

}

/** An event persisted in an event stream.
  *
  * @param streamId
  *   the stream identifier of the stream the event belongs to.
  * @param position
  *   the position information about of the event.
  * @param eventData
  *   the payload of the event.
  * @param created
  *   the creation date of the event in [[java.time.ZonedDateTime]].
  */
final case class EventRecord[P <: PositionInfo](
  streamId: StreamId,
  position: P,
  eventData: EventData,
  created: ZonedDateTime
) extends Event[P]

object EventRecord {

  def render[P <: PositionInfo](er: EventRecord[P]): String =
    s"""
       |EventRecord(
       |  streamId = ${er.streamId.render},
       |  eventId  = ${er.eventData.eventId},
       |  type     = ${er.eventData.eventType.render},
       |  position = ${er.position.renderPosition},
       |  data     = ${er.eventData.renderData},
       |  metadata = ${er.eventData.renderMetadata},
       |  created  = ${er.created}
       |)
       |""".stripMargin

}

/** Represents a [[EventType.LinkTo]] event that points to another event. Resolved events are common when reading or
  * subscribing to system prefixed streams, for instance category streams like `$ce-` or `$et-`.
  *
  * @param event
  *   the original and linked to event record.
  * @param link
  *   the link event to the resolved event.
  */
final case class ResolvedEvent[P <: PositionInfo](
  event: EventRecord[P],
  link: EventRecord[P]
) extends Event[P]

object ResolvedEvent {

  def render[P <: PositionInfo](re: ResolvedEvent[P]): String =
    s"""
       |ResolvedEvent(
       |  event = ${EventRecord.render(re.event)},
       |  link  = ${EventRecord.render(re.link)}
       |)
       |""".stripMargin

}

//======================================================================================================================

/** Event type for an [[Event]]. There are two event type variants:
  *
  *   - [[EventType.System]] Type used for reserverd internal system events.
  *   - [[EventType.Normal]] Type used by users.
  *
  * @see
  *   [[https://ahjohannessen.github.io/sec/docs/types#eventtype]] for more information about event type usage.
  */
sealed trait EventType
object EventType {

  sealed abstract case class System(name: String) extends EventType
  private[sec] object System {
    def unsafe(name: String): System = new System(name) {}
  }

  sealed abstract case class Normal(name: String) extends EventType
  private[sec] object Normal {
    def unsafe(name: String): Normal = new Normal(name) {}
  }

  ///

  final val StreamDeleted: System   = System.unsafe("streamDeleted")
  final val StatsCollected: System  = System.unsafe("statsCollected")
  final val LinkTo                  = System.unsafe(">")
  final val StreamReference: System = System.unsafe("@")
  final val StreamMetadata: System  = System.unsafe("metadata")
  final val Settings: System        = System.unsafe("settings")

  /** @param name
    *   Constructs an event type for [[EventData]]. Provided value is validated for non-empty and not starting with the
    *   system reserved prefix `$`.
    */
  def apply(name: String): Either[InvalidInput, Normal] =
    normal(name).leftMap(InvalidInput(_))

  ///

  private[sec] val guardNonEmptyName: String => Attempt[String] = guardNonEmpty("Event type name")

  private[sec] def system(name: String): Attempt[System] =
    guardNonEmptyName(name) >>= guardNotStartsWith(systemPrefix) >>= (System.unsafe(_).asRight)

  private[sec] def normal(name: String): Attempt[Normal] =
    guardNonEmptyName(name) >>= guardNotStartsWith(systemPrefix) >>= (Normal.unsafe(_).asRight)

  ///

  private[sec] val eventTypeToString: EventType => String = {
    case System(n) => s"$systemPrefix$n"
    case Normal(n) => n
  }

  private[sec] val stringToEventType: String => Attempt[EventType] = {
    case sd if sd.startsWith(systemPrefix) => system(sd.substring(systemPrefixLength))
    case ud                                => normal(ud)
  }

  final private[sec] val systemPrefix: String    = "$"
  final private[sec] val systemPrefixLength: Int = systemPrefix.length

  implicit final class EventTypeOps(val et: EventType) extends AnyVal {
    def stringValue: String = eventTypeToString(et)
    def render: String      = stringValue
  }

}

//======================================================================================================================

/** Event payload for an event. This is the actual data that you persist in EventStoreDB.
  *
  * @param eventType
  *   the [[EventType]] for the event.
  * @param eventId
  *   unique identifier for the event.
  * @param data
  *   a [[scodec.bits.ByteVector]] of encoded data.
  * @param metadata
  *   a [[scodec.bits.ByteVector]] of encoded metadata.
  * @param contentType
  *   the [[ContentType]] of encoded data and metadata.
  */
final case class EventData(
  eventType: EventType,
  eventId: UUID,
  data: ByteVector,
  metadata: ByteVector,
  contentType: ContentType
)

object EventData {

  /** Constructor for [[EventData]] when metadata is not required.
    *
    * @param eventType
    *   the [[EventType]] for the event.
    * @param eventId
    *   unique identifier for the event.
    * @param data
    *   a [[scodec.bits.ByteVector]] of encoded data.
    * @param contentType
    *   the [[ContentType]] of encoded data and metadata.
    */
  def apply(
    eventType: EventType,
    eventId: UUID,
    data: ByteVector,
    contentType: ContentType
  ): EventData =
    EventData(eventType, eventId, data, ByteVector.empty, contentType)

  /** Constructor for [[EventData]] when the event type is a string and metadata is not required. Returns either
    * [[InvalidInput]] or [[EventType]].
    *
    * @param eventType
    *   string value for [[EventType]].
    * @param eventId
    *   unique identifier for the event.
    * @param data
    *   a [[scodec.bits.ByteVector]] of encoded data.
    * @param contentType
    *   the [[ContentType]] of encoded data and metadata.
    */
  def apply(
    eventType: String,
    eventId: UUID,
    data: ByteVector,
    contentType: ContentType
  ): Either[InvalidInput, EventData] =
    EventType(eventType).map(EventData(_, eventId, data, ByteVector.empty, contentType))

  /** Constructor for [[EventData]] when the event type is a string.
    *
    * @param eventType
    *   string value for [[EventType]].
    * @param eventId
    *   unique identifier for the event.
    * @param data
    *   a [[scodec.bits.ByteVector]] of encoded data.
    * @param metadata
    *   a [[scodec.bits.ByteVector]] of encoded metadata.
    * @param contentType
    *   the [[ContentType]] of encoded data and metadata.
    */
  def apply(
    eventType: String,
    eventId: UUID,
    data: ByteVector,
    metadata: ByteVector,
    contentType: ContentType
  ): Either[InvalidInput, EventData] =
    EventType(eventType).map(EventData(_, eventId, data, metadata, contentType))

  ///

  implicit final private[sec] class EventDataOps(val ed: EventData) extends AnyVal {
    def renderData: String     = render(ed.data, ed.contentType)
    def renderMetadata: String = render(ed.metadata, ed.contentType)
  }

  private[sec] def render(bv: ByteVector, ct: ContentType): String =
    if (bv.isEmpty || ct.isBinary) s"${ct.render}(${renderByteVector(bv)})"
    else s"${bv.decodeUtf8.getOrElse("Failed decoding utf8")}"

  private[sec] def renderByteVector(bv: ByteVector): String = {
    if (bv.isEmpty) s"empty"
    else if (bv.size < 32) s"${bv.size} bytes, 0x${bv.toHex}"
    else s"${bv.size} bytes, #${bv.hashCode}"
  }

}

//======================================================================================================================

/** Content type for [[EventData]]. There are two variants:
  *
  *   - [[ContentType.Json]] used when event data and metadata are encoded as JSON.
  *   - [[ContentType.Binary]] used when event data and metadata are encoded as binary. This can for instance be
  *     [[https://developers.google.com/protocol-buffers]] or utf8 encoded data like [[EventType.LinkTo]] events.
  */

sealed trait ContentType
object ContentType {

  case object Binary extends ContentType
  case object Json extends ContentType

  implicit final class ContentTypeOps(val ct: ContentType) extends AnyVal {

    def fold[A](binary: => A, json: => A): A = ct match {
      case Binary => binary
      case Json   => json
    }

    def isJson: Boolean   = ct.fold(false, true)
    def isBinary: Boolean = !isJson
    def render: String    = fold("Binary", "Json")

  }

}

//======================================================================================================================
