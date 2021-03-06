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

import java.nio.file.Path

import cats.effect._
import cats.syntax.all._
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts.forClient
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder.{forAddress, forTarget}
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext

private[sec] object netty {

  def mkBuilder[F[_]: Sync](p: ChannelBuilderParams): F[NettyChannelBuilder] = {

    def mkSslContext(chainPath: Path): F[SslContext] = Sync[F].delay {
      forClient().trustManager(chainPath.toFile).build()
    }

    val ncb: NettyChannelBuilder =
      p.targetOrEndpoint.fold(forTarget, ep => forAddress(ep.address, ep.port))

    p.mode match {
      case ConnectionMode.Insecure  => ncb.usePlaintext().pure[F]
      case ConnectionMode.Secure(c) => mkSslContext(c).map(ncb.useTransportSecurity().sslContext)
    }

  }

}
