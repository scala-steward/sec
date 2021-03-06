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
package syntax

import cats.syntax.all._
import scodec.bits.ByteVector

trait StringSyntax {
  implicit final def syntaxForString(s: String): StringOps = new StringOps(s)
}

final class StringOps(val s: String) extends AnyVal {
  def utf8Bytes[F[_]: ErrorA]: F[ByteVector] = ByteVector.encodeUtf8(s).liftTo[F]
}
