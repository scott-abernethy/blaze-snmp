package blazesnmp

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

import blazesnmp.Ber
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import Ber._

class BerSuite extends FunSuite with ShouldMatchers {
  
  /* This encoding is always used if the encoding is primitive or the encoding is constructed and data is immediately available. Depending on the actual length of the content the length octets are encoded using either a short form or a long form. Both forms store numeric data as unsigned binary integers in big-endian encoding.
In the short form, the length octets consist of a single octet in which 
bits 7 to 1 encode the number of octets in the contents octets (which may be zero).
Bit 8 of the length octet is zero to indicate that this is the short form.

Example: L = 38 can be encoded as 00100110

In contrast to the short form, the long form length octets consist of an initial
 octet and one or more subsequent octets. According to the X.690 standard [1]
  the initial length octet shall be encoded as follows:
bit 8 shall be one;
bits 7 to 1 shall encode the number of subsequent octets in the length octets, 
as an unsigned binary integer with bit 7 as the most significant bit;
the value 111111112 shall not be used.
All bits of the subsequent octets form the encoding of an unsigned binary
 integer equal to the number of octets in the contents octets.
Example: L = 435 can be encoded as 
         10000010  // long form with two subsequent length octets
         00000001
         10110011  // both octets together form the binary string
          0000000110110011
[edit]
  */
  
  test("definite length short form") {
    definiteLength(0) should equal (List(0.toByte))
    definiteLength(38) should equal (List(38.toByte))
    definiteLength(1) should equal (List(1.toByte))
    definiteLength(127) should equal (List(127.toByte))
  }
  
  test("definite length long form") {
    definiteLength(435) should equal (List(130.toByte, 1.toByte, 179.toByte))
    definiteLength(128) should equal (List(129.toByte, 128.toByte))
  }
  
  test("seq bytes") {
    seqBytes(0) should equal (List(0.toByte))
    seqBytes(1) should equal (List(1.toByte))
    seqBytes(128) should equal (List(128.toByte))
    seqBytes(255) should equal (List(255.toByte))
    seqBytes(435) should equal (List(179.toByte, 1.toByte))
  }

  test("overflowing int") {
    overflowingInt(0) should equal(List(0.toByte))
    overflowingInt(1) should equal(List(1.toByte))
    overflowingInt(127) should equal(List(127.toByte))
    overflowingInt(128) should equal(List(129.toByte, 0.toByte))
    overflowingInt(129) should equal(List(129.toByte, 1.toByte))
    overflowingInt(2509) should equal(List(0x93.toByte, 0x4d.toByte))
    overflowingInt(30000) should equal(List(0x81.toByte, 0xea.toByte, 0x30.toByte)) // 111010100110000 -> 10000001, 11101010, 00110000
  }

  test("encode int") {
    int(1) should equal(List(1.toByte))
    int(15) should equal(List(15.toByte))
    int(127) should equal(List(127.toByte))
    int(128) should equal(List(0.toByte, 128.toByte))
    int(130) should equal(List(0.toByte, 130.toByte))
    int(255) should equal(List(0.toByte, 255.toByte))
    int(256) should equal(List(1.toByte, 0.toByte))
    int(-129) should equal(List(0xff.toByte, 0x7f.toByte))
  }
}