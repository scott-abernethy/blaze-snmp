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

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import akka.util.ByteString

class BerDecodeSuite extends FunSuite with ShouldMatchers {
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
    BerDecode.getTlv(bs.result.iterator) should equal("(none)")
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
    BerDecode.getSeqOfTlv(bs.result.iterator) should equal(List(435, "(none)", 54))
  }

}