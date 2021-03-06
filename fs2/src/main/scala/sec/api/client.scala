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
package api

import scala.concurrent.duration._
import cats.Endo
import cats.syntax.all._
import cats.data.NonEmptySet
import cats.effect.Resource
import cats.effect.kernel.Async
import com.eventstore.dbclient.proto.gossip.GossipFs2Grpc
import com.eventstore.dbclient.proto.streams.StreamsFs2Grpc
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import io.grpc.{CallOptions, ManagedChannel}
import sec.api.exceptions.{NotLeader, ServerUnavailable}
import sec.api.grpc.convert.convertToEs
import sec.api.grpc.metadata._
import sec.api.retries.RetryConfig

trait EsClient[F[_]] {
  def streams: Streams[F]
  def metaStreams: MetaStreams[F]
  def gossip: Gossip[F]
}

object EsClient {

  def singleNode[F[_]: Async](address: String, port: Int): SingleNodeBuilder[F] =
    singleNode[F](Endpoint(address, port))

  def singleNode[F[_]: Async](endpoint: Endpoint): SingleNodeBuilder[F] =
    SingleNodeBuilder[F](endpoint, None, Options.default, 10.seconds, logger = NoOpLogger.impl[F])

  def cluster[F[_]: Async](seed: NonEmptySet[Endpoint], authority: String): ClusterBuilder[F] =
    ClusterBuilder[F](seed, authority, Options.default, ClusterOptions.default, NoOpLogger.impl[F])

//======================================================================================================================

  private[sec] def apply[F[_]: Async](
    mc: ManagedChannel,
    options: Options,
    requiresLeader: Boolean,
    logger: Logger[F]
  ): Resource[F, EsClient[F]] =
    (mkStreamsFs2Grpc[F](mc), mkGossipFs2Grpc[F](mc)).mapN((s, g) => create[F](s, g, options, requiresLeader, logger))

  private[sec] def create[F[_]: Async](
    streamsFs2Grpc: StreamsFs2Grpc[F, Context],
    gossipFs2Grpc: GossipFs2Grpc[F, Context],
    options: Options,
    requiresLeader: Boolean,
    logger: Logger[F]
  ): EsClient[F] = new EsClient[F] {

    val streams: Streams[F] = Streams(
      streamsFs2Grpc,
      mkContext(options, requiresLeader),
      mkOpts[F](options.operationOptions, logger, "Streams")
    )

    val metaStreams: MetaStreams[F] = MetaStreams[F](streams)

    val gossip: Gossip[F] = Gossip(
      gossipFs2Grpc,
      mkContext(options, requiresLeader),
      mkOpts[F](options.operationOptions, logger, "Gossip")
    )
  }

//======================================================================================================================

  private[sec] def mkContext(o: Options, requiresLeader: Boolean): Option[UserCredentials] => Context =
    uc => Context(o.connectionName, uc.orElse(o.credentials), requiresLeader)

  private[sec] val defaultRetryOn: Throwable => Boolean = {
    case _: ServerUnavailable | _: NotLeader => true
    case _                                   => false
  }

  private[sec] def mkOpts[F[_]](oo: OperationOptions, log: Logger[F], prefix: String): Opts[F] = {
    val rc = RetryConfig(oo.retryDelay, oo.retryMaxDelay, oo.retryBackoffFactor, oo.retryMaxAttempts, None)
    Opts[F](oo.retryEnabled, rc, defaultRetryOn, log.withModifiedString(s => s"$prefix > $s"))
  }

  /// Streams

  private[sec] def mkStreamsFs2Grpc[F[_]: Async](
    mc: ManagedChannel,
    fn: Endo[CallOptions] = identity
  ): Resource[F, StreamsFs2Grpc[F, Context]] =
    StreamsFs2Grpc.clientResource[F, Context](mc, _.toMetadata, fn, convertToEs)

  /// Gossip

  private[sec] def mkGossipFs2Grpc[F[_]: Async](
    mc: ManagedChannel,
    fn: Endo[CallOptions] = identity
  ): Resource[F, GossipFs2Grpc[F, Context]] =
    GossipFs2Grpc.clientResource[F, Context](mc, _.toMetadata, fn, convertToEs)

}
