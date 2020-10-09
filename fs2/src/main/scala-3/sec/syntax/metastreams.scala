/*
 * Copyright 2020 Alex Henning Johannessen
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
package syntax

import scala.concurrent.duration.FiniteDuration
import cats.syntax.all._
import sec.api._
import sec.api.MetaStreams._
import EventNumber.Exact
import StreamId.Id

//====================================================================================================================

trait MetaStreamsSyntax {

  extension [F[_]: ErrorM](ms: MetaStreams[F]) {

    def getMaxAge(id: Id): F[Option[ReadResult[MaxAge]]] =
      ms.getMaxAge(id, None)

    def setMaxAge(id: Id, expectedRevision: StreamRevision, age: FiniteDuration): F[WriteResult] =
      setMaxAgeF(id, expectedRevision, age, None)

    def setMaxAge(id: Id, expectedRevision: StreamRevision, age: FiniteDuration, uc: UserCredentials): F[WriteResult] =
      setMaxAgeF(id, expectedRevision, age, uc.some)
      
    private def setMaxAgeF(id: Id, er: StreamRevision, m: FiniteDuration, uc: Option[UserCredentials]): F[WriteResult] =
      MaxAge.of[F](m) >>= (ms.setMaxAge(id, er, _, uc))

    def unsetMaxAge(id: Id, expectedRevision: StreamRevision): F[WriteResult] =
      ms.unsetMaxAge(id, expectedRevision, None)

    def getMaxCount(id: Id): F[Option[ReadResult[MaxCount]]] =
      ms.getMaxCount(id, None)

    def setMaxCount(id: Id, expectedRevision: StreamRevision, count: Int): F[WriteResult] =
      setMaxCountF(id, expectedRevision, count, None)

    def setMaxCount(id: Id, expectedRevision: StreamRevision, count: Int, uc: UserCredentials): F[WriteResult] =
      setMaxCountF(id, expectedRevision, count, uc.some)
      
    private def setMaxCountF(id: Id, er: StreamRevision, count: Int, uc: Option[UserCredentials]): F[WriteResult] =
      MaxCount.of[F](count) >>= (ms.setMaxCount(id, er, _, uc))

    def unsetMaxCount(id: Id, expectedRevision: StreamRevision): F[WriteResult] =
      ms.unsetMaxCount(id, expectedRevision, None)

    def getCacheControl(id: Id): F[Option[ReadResult[CacheControl]]] =
      ms.getCacheControl(id, None)

    def setCacheControl(id: Id, expectedRevision: StreamRevision, cacheControl: FiniteDuration): F[WriteResult] =
      setCacheControlF(id, expectedRevision, cacheControl, None)

    def setCacheControl(id: Id, expectedRevision: StreamRevision, cacheControl: FiniteDuration, uc: UserCredentials): F[WriteResult] =
      setCacheControlF(id, expectedRevision, cacheControl, uc.some)
      
    private def setCacheControlF(id: Id, er: StreamRevision, cacheControl: FiniteDuration, uc: Option[UserCredentials]): F[WriteResult] =
      CacheControl.of[F](cacheControl) >>= (ms.setCacheControl(id, er, _, uc))

    def unsetCacheControl(id: Id, expectedRevision: StreamRevision): F[WriteResult] =
      ms.unsetCacheControl(id, expectedRevision, None)

    def getAcl(id: Id): F[Option[ReadResult[StreamAcl]]] =
      ms.getAcl(id, None)

    def setAcl(id: Id, expectedRevision: StreamRevision, acl: StreamAcl): F[WriteResult] =
      ms.setAcl(id, expectedRevision, acl, None)

    def unsetAcl(id: Id, expectedRevision: StreamRevision): F[WriteResult] =
      ms.unsetAcl(id, expectedRevision, None)

    def getTruncateBefore(id: Id): F[Option[ReadResult[Exact]]] =
      ms.getTruncateBefore(id, None)

    def setTruncateBefore(id: Id, expectedRevision: StreamRevision, truncateBefore: Long): F[WriteResult] =
      setTruncateBeforeF(id, expectedRevision, truncateBefore, None)

    def setTruncateBefore(id: Id, expectedRevision: StreamRevision, truncateBefore: Long, uc: UserCredentials): F[WriteResult] =
      setTruncateBeforeF(id, expectedRevision, truncateBefore, uc.some)
      
    private def setTruncateBeforeF(id: Id, er: StreamRevision, tb: Long, uc: Option[UserCredentials]): F[WriteResult] =
      EventNumber.Exact.of[F](tb) >>= (ms.setTruncateBefore(id, er, _, uc))

    def unsetTruncateBefore(id: Id, expectedRevision: StreamRevision): F[WriteResult] =
      ms.unsetTruncateBefore(id, expectedRevision, None)

    private[sec] def getMetadata(id: Id): F[Option[MetaResult]] =
      ms.getMetadata(id, None)

    private[sec] def setMetadata(id: Id, expectedRevision: StreamRevision, data: StreamMetadata): F[WriteResult] =
      ms.setMetadata(id, expectedRevision, data, None)

    private[sec] def removeMetadata(id: Id, expectedRevision: StreamRevision): F[WriteResult] =
      ms.unsetMetadata(id, expectedRevision, None)
  }

}

//====================================================================================================================