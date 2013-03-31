import util.Try

/*
 * Copyright (c) 2013 Scott Abernethy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class ObjectIdentifier(ids: List[Int]) {
  def toList(): List[Int] = ids

  override def toString(): String = {
    ids.mkString(".")
  }
}

object ObjectIdentifier {

  val SysUpTime = create("1.3.6.1.2.1.1.3.0")
  val SysName = create("1.3.6.1.2.1.1.5.0")
  val SysLocation = create("1.3.6.1.2.1.1.6.0")
  val LastChangeIndex = create("1.3.6.1.4.1.2509.8.21.2.1.0")
  val SiteName = create("1.3.6.1.4.1.2509.8.7.2.1.0")
  val RadioName = create("1.3.6.1.4.1.2509.8.7.2.2.0")

  def create(oid: String): ObjectIdentifier = {
    new ObjectIdentifier(
      for {
        bit <- oid.split('.').toList
        id <- Try(bit.toInt).toOption
      } yield id
    )
  }
}