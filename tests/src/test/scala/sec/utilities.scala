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

import scala.concurrent.duration._

import org.specs2.mutable.Specification
import sec.utilities._

class UtilitiesSpec extends Specification {

  "guardNonEmpty" >> {
    guardNonEmpty("x")(null) should beLeft("x cannot be empty")
    guardNonEmpty("x")("") should beLeft("x cannot be empty")
    guardNonEmpty("x")("wohoo") should beRight("wohoo")
  }

  "guardNotStartsWith" >> {
    guardNotStartsWith("$")("$") should beLeft("value must not start with $, but is $")
    guardNotStartsWith("$")("a$") should beRight("a$")
  }

  "format duration" >> {
    format(150.millis) shouldEqual "150.0ms"
    format(1001.millis) shouldEqual "1.001s"
    format(4831.millis) shouldEqual "4.831s"
  }

}
