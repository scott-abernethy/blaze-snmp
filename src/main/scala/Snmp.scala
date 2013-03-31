import java.util.Date

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

class Snmp {

}

object Snmp {
  val IsoOrg: Byte = 0x2b.toByte
}

trait Variable

case class TimeTicks(value: Int) extends Variable {
  override def toString = "TimeTicks(" + value + ")"

  def toMillis: Long = value * 100
}

case class Varbind(id: ObjectIdentifier, value: Variable)



object SnmpV2Msg {
  def unapply(in: Any): Option[(String, Byte, Int, Int, Int, List[Any])] = {
    in match {
      case List(1, community: OctetString, (pduType: Byte, List(requestId: Int, errorStatus: Int, errorIndex: Int, varbinds: List[Any]))) => Some(community.toString, pduType, requestId, errorStatus, errorIndex, varbinds)
      case _ => None
    }
  }
}