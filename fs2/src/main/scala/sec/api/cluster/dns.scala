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
package cluster

import cats.Applicative
import cats.syntax.all._
import cats.effect.Sync
import com.comcast.ip4s._

private[sec] trait EndpointResolver[F[_]] {
  def resolveEndpoints(host: Hostname, port: Port): F[List[Endpoint]]
}

private[sec] object EndpointResolver {

  def noop[F[_]: Applicative]: EndpointResolver[F] = new EndpointResolver[F] {
    def resolveEndpoints(host: Hostname, port: Port): F[List[Endpoint]] = List.empty[Endpoint].pure[F]
  }

  def default[F[_]: Sync]: EndpointResolver[F] = new EndpointResolver[F] {

    def resolveEndpoints(host: Hostname, port: Port): F[List[Endpoint]] =
      host.resolveAll[F].map(_.map(ia => Endpoint(ia.toUriString, port.value)))

  }

}
