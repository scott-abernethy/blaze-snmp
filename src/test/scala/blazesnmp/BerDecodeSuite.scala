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

package blazesnmp

import blazesnmp.{Snmp, OctetString, BerDecode, BerIdentifier}
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import akka.util.ByteString

class BerDecodeSuite extends FunSuite with ShouldMatchers {

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

  test("get definite length short form") {
    BerDecode.getDefiniteLength(ByteString(0.toByte).iterator) should equal(0)
    BerDecode.getDefiniteLength(ByteString(1.toByte).iterator) should equal(1)
    BerDecode.getDefiniteLength(ByteString(46.toByte).iterator) should equal(46)
    BerDecode.getDefiniteLength(ByteString(127.toByte).iterator) should equal(127)
  }
  
  test("get definite length long form") {
    val bs = ByteString.newBuilder
    bs.putByte(130.toByte)
    bs.putByte(1.toByte)
    bs.putByte(179.toByte)
    BerDecode.getDefiniteLength(bs.result.iterator) should equal(435)
  }
  
  test("get tlv single byte") {
    val bs = ByteString.newBuilder
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(1.toByte)
    bs.putByte(5.toByte)
    BerDecode.getTlv(bs.result.iterator) should equal(5)
  }

  test("get tlv int") {
    val bs = ByteString.newBuilder
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(2.toByte)
    bs.putByte(1.toByte)
    bs.putByte(179.toByte)
    BerDecode.getTlv(bs.result.iterator) should equal(435)
  }
  
  test("get tlv string") {
    val bs = ByteString.newBuilder
    bs.putByte(BerIdentifier.OctetString)
    bs.putByte(6)
    bs.putByte(0x28.toByte)
    bs.putByte(0x6e.toByte)
    bs.putByte(0x6f.toByte)
    bs.putByte(0x6e.toByte)
    bs.putByte(0x65.toByte)
    bs.putByte(0x29.toByte)
    BerDecode.getTlv(bs.result.iterator) should equal(OctetString.create("(none)"))
  }
  
  test("get seq of tlv") {
    val bs = ByteString.newBuilder
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(2.toByte)
    bs.putByte(1.toByte)
    bs.putByte(179.toByte)
    bs.putByte(BerIdentifier.OctetString)
    bs.putByte(6)
    bs.putByte(0x28.toByte)
    bs.putByte(0x6e.toByte)
    bs.putByte(0x6f.toByte)
    bs.putByte(0x6e.toByte)
    bs.putByte(0x65.toByte)
    bs.putByte(0x29.toByte)
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(1.toByte)
    bs.putByte(54.toByte)
    BerDecode.getSeqOfTlv(bs.result.iterator) should equal(List(435, OctetString.create("(none)"), 54))
  }

  test("get tlv seq of stuff") {
    val bs = ByteString.newBuilder
    bs.putByte(0) // skipped later
    bs.putByte(0) // skipped later
    bs.putByte(0) // skipped later
    bs.putByte(BerIdentifier.Sequence)
    bs.putByte(18)
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(2.toByte)
    bs.putByte(1.toByte)
    bs.putByte(179.toByte)
    bs.putByte(BerIdentifier.OctetString)
    bs.putByte(6)
    bs.putByte(0x28.toByte)
    bs.putByte(0x6e.toByte)
    bs.putByte(0x6f.toByte)
    bs.putByte(0x6e.toByte)
    bs.putByte(0x65.toByte)
    bs.putByte(0x29.toByte)
    bs.putByte(BerIdentifier.ObjectId)
    bs.putByte(4)
    bs.putByte(Snmp.IsoOrg)
    bs.putByte(0x01.toByte)
    bs.putByte(0x09.toByte)
    bs.putByte(4)
    bs.putByte(7) // off the end
    bs.putByte(8) // off the end
    val iterator = bs.result.iterator
    iterator.getByte
    iterator.getByte
    iterator.getByte
    BerDecode.getTlv(iterator) should equal(List(435, OctetString.create("(none)"), ObjectIdentifier(Seq(1,3,1,9,4))))
    iterator.getByte should equal(7.toByte)
    iterator.getByte should equal(8.toByte)
  }

  test("get simple object id") {
    val a = Array.newBuilder[Byte]
    a += Snmp.IsoOrg
    a += 0x01.toByte
    a += 0x09.toByte
    a += 0x2b.toByte
    a += 0x09.toByte
    a += 127.toByte
    a += 0x01.toByte
    a += 0x00.toByte
    BerDecode.getObjectId(a.result.toList) should equal(List(1,3,1,9,43,9,127,1,0))
  }

  test("get tlv object id") {
    val bs = ByteString.newBuilder
    bs.putByte(BerIdentifier.ObjectId)
    bs.putByte(8)
    bs.putByte(Snmp.IsoOrg)
    bs.putByte(0x01.toByte)
    bs.putByte(0x09.toByte)
    bs.putByte(0x2b.toByte)
    bs.putByte(0x09.toByte)
    bs.putByte(127.toByte)
    bs.putByte(0x01.toByte)
    bs.putByte(0x00.toByte)
    bs.putByte(BerIdentifier.ObjectId)
    bs.putByte(8)
    bs.putByte(Snmp.IsoOrg)
    bs.putByte(0x01.toByte)
    bs.putByte(0x09.toByte)
    bs.putByte(0x2b.toByte)
    bs.putByte(0x09.toByte)
    bs.putByte(127.toByte)
    bs.putByte(0x01.toByte)
    bs.putByte(0x02.toByte)
    val iterator = bs.result.iterator
    BerDecode.getTlv(iterator) should equal(ObjectIdentifier(List(1,3,1,9,43,9,127,1,0)))
    BerDecode.getTlv(iterator) should equal(ObjectIdentifier(List(1,3,1,9,43,9,127,1,2)))
  }

  test("get object id including overflow int") {
    val a = Array.newBuilder[Byte]
    a += Snmp.IsoOrg
    a += 0x01.toByte
    a += 0x93.toByte
    a += 0x4d.toByte
    a += 0x09.toByte
    BerDecode.getObjectId(a.result.toList) should equal(List(1,3,1,2509,9))
  }
}