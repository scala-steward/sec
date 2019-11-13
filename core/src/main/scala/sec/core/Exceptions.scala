package sec
package core

sealed abstract class EsException(msg: String) extends RuntimeException(msg)

case object AccessDenied                            extends EsException("Access Denied")
case object InvalidTransaction                      extends EsException("Invalid Transaction")
final case class UserNotFound(loginName: String)    extends EsException(s"User '$loginName' was not found.")
final case class StreamDeleted(streamName: String)  extends EsException(s"Event stream '$streamName' is deleted.")
final case class StreamNotFound(streamName: String) extends EsException(s"Event stream '$streamName' was not found.")

final case class PersistentSubscriptionFailed(streamName: String, groupName: String, reason: String)
  extends EsException(s"Subscription group $groupName on stream $streamName failed: '$reason'.")

final case class PersistentSubscriptionExists(streamName: String, groupName: String)
  extends EsException(s"Subscription group $groupName on stream $streamName exists.")

final case class PersistentSubscriptionNotFound(streamName: String, groupName: String)
  extends EsException(s"Subscription group '$groupName' on stream '$streamName' does not exist.")

final case class PersistentSubscriptionDroppedByServer(streamName: String, groupName: String)
  extends EsException(s"Subscription group '$groupName' on stream '$streamName' was dropped.")

final case class PersistentSubscriptionMaximumSubscribersReached(streamName: String, groupName: String)
  extends EsException(s"Maximum subscriptions reached for subscription group '$groupName' on stream '$streamName.'")

final case class WrongExpectedVersion(streamName: String, expected: Option[Long], actual: Option[Long])
  extends EsException(WrongExpectedVersion.msg(streamName, expected, actual))

object WrongExpectedVersion {
  def msg(streamName: String, expected: Option[Long], actual: Option[Long]): String = {
    val exp = expected.getOrElse("<unknown>")
    val act = actual.getOrElse("<unknown>")
    s"WrongExpectedVersion for stream: $streamName, expected version: $exp, actual version: $act"
  }
}
