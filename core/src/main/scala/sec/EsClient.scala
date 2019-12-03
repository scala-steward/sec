package sec

import cats.effect.{ConcurrentEffect, Resource}
import org.lyranthe.fs2_grpc.java_runtime.implicits._
import com.eventstore.client.streams.StreamsFs2Grpc
import io.grpc.{ManagedChannel, ManagedChannelBuilder, Metadata}
import fs2.Stream
import sec.api._

trait EsClient[F[_]] {
  def streams: Streams[F]
}

object EsClient {

  private[sec] def stream[F[_]: ConcurrentEffect, MCB <: ManagedChannelBuilder[MCB]](
    builder: MCB,
    options: Options
  ): Stream[F, EsClient[F]] =
    Stream.resource(resource[F, MCB](builder, options))

  private[sec] def resource[F[_]: ConcurrentEffect, MCB <: ManagedChannelBuilder[MCB]](
    builder: MCB,
    options: Options
  ): Resource[F, EsClient[F]] =
    builder.resource[F].map(new Impl[F](_, options))

  private final class Impl[F[_]: ConcurrentEffect](mc: ManagedChannel, options: Options) extends EsClient[F] {
    val streams: Streams[F] =
      Streams(StreamsFs2Grpc.client[F, Metadata](mc, identity, identity, grpc.convertToEs), options)
  }

}