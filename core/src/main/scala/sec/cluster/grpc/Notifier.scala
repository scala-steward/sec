package sec
package cluster
package grpc

import scala.jdk.CollectionConverters._
import io.grpc._
import io.grpc.NameResolver
import cats.data.{NonEmptyList => Nel}
import cats.implicits._
import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import fs2.Stream
import fs2.concurrent.SignallingRef
import sec.api.Gossip._

trait Notifier[F[_]] {
  def start(l: Listener[F]): F[Unit]
  def stop: F[Unit]
}

object Notifier {

  object gossip {

    def apply[F[_]: Concurrent](
      seed: Nel[Endpoint],
      np: NodePreference,
      updates: Stream[F, ClusterInfo]
    ): F[Notifier[F]] = {
      for {
        signal    <- SignallingRef[F, Boolean](false)
        endpoints <- Ref[F].of(seed)
      } yield apply(seed, np, updates, endpoints, signal)
    }

    def apply[F[_]](
      seed: Nel[Endpoint],
      np: NodePreference,
      updates: Stream[F, ClusterInfo],
      endpoints: Ref[F, Nel[Endpoint]],
      halt: SignallingRef[F, Boolean]
    )(implicit F: Concurrent[F]): Notifier[F] =
      new Notifier[F] {

        def next(current: Nel[Endpoint], ci: ClusterInfo): F[Nel[Endpoint]] =
          determineNext(seed, np, current, ci.members.toList)

        def start(l: Listener[F]): F[Unit] = {

          val bootstrap = l.onResult(mkResult(seed))

          def update(ci: ClusterInfo): F[Unit] =
            endpoints.get.flatMap { current =>
              next(current, ci) >>= { n =>
                (endpoints.set(n) >> l.onResult(mkResult(n))).whenA(current =!= n)
              }
            }

          val run = updates.evalMap(update).interruptWhen(halt)

          bootstrap *> run.compile.drain.start.void

        }

        val stop: F[Unit] = halt.set(true)

      }

    def determineNext[F[_]: Sync](
      seed: Nel[Endpoint],
      np: NodePreference,
      current: Nel[Endpoint],
      members: List[MemberInfo]
    ): F[Nel[Endpoint]] =
      Nel.fromList(members).fold(seed.pure[F]) { nel =>
        NodePrioritizer.prioritizeNodes[F](nel, np).map(_.map(_.httpEndpoint)) >>= {
          case s @ x :: xs => Nel.of(x, xs ::: current.toList.filterNot(s.contains): _*).pure[F]
          case Nil         => seed.pure[F]
        }
      }

    def mkResult(endpoints: Nel[Endpoint]): NameResolver.ResolutionResult =
      NameResolver.ResolutionResult
        .newBuilder()
        .setAddresses(endpoints.map(_.toEquivalentAddressGroup).toList.asJava)
        .build()

  }

  ///

  object bestNodes {

    def apply[F[_]: Concurrent](np: NodePreference, updates: Stream[F, ClusterInfo]): F[Notifier[F]] =
      SignallingRef[F, Boolean](false).map(apply(np, updates, _))

    def apply[F[_]](
      np: NodePreference,
      updates: Stream[F, ClusterInfo],
      halt: SignallingRef[F, Boolean]
    )(implicit F: Concurrent[F]): Notifier[F] =
      new Notifier[F] {

        def next(ci: ClusterInfo): F[List[MemberInfo]] = determineNext[F](ci, np)

        def start(l: Listener[F]): F[Unit] = {

          def update(ci: ClusterInfo): F[Unit] =
            next(ci) >>= {
              case Nil     => l.onError(noNodes)
              case x :: xs => l.onResult(mkResult(x :: xs))
            }

          val run = updates
            .evalMap(update)
            .interruptWhen(halt)

          run.compile.drain.start.void
        }

        val stop: F[Unit] = halt.set(true)

      }

    def determineNext[F[_]: Sync](ci: ClusterInfo, np: NodePreference): F[List[MemberInfo]] =
      Nel
        .fromList(ci.members.toList)
        .map(NodePrioritizer.prioritizeNodes[F](_, np))
        .getOrElse(List.empty.pure[F])

    def mkResult(ms: List[MemberInfo]): NameResolver.ResolutionResult = {

      def mkEag(m: MemberInfo): EquivalentAddressGroup = {
        val address    = m.httpEndpoint.toInetSocketAddress
        val attributes = Attributes.newBuilder().set(vNodeStateKey, m.state).build()
        new EquivalentAddressGroup(address, attributes)
      }

      NameResolver.ResolutionResult.newBuilder().setAddresses(ms.map(mkEag).asJava).build()
    }

    private val noNodes: Status = Status.UNAVAILABLE.withDescription("No nodes available")

    ///

    val vNodeStateKey: Attributes.Key[VNodeState] =
      Attributes.Key.create[VNodeState]("VNodeState")

  }

}
