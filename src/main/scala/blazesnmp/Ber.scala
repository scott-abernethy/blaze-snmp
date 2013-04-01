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

import akka.util.{ByteStringBuilder, ByteString}

// asn1 ber

object Ber {
  def definiteLength(octets: Int): ByteString = {
    if (octets < 0) {
      throw new UnsupportedOperationException
    }
    else if (octets < 128) {
      ByteString(octets.toByte)
    }
    else {
      seqBytes(octets) match {
        case Nil => ByteString()
        case x :: xs => ByteString(((129 + xs.length).toByte :: (x :: xs).reverse) : _*)
      }
    }
  }
  
  def seqBytes(int: Int): List[Byte] = {
    if (int < 0) throw new UnsupportedOperationException
    (int >> 8) match {
      case 0 => (int % 256).toByte :: Nil
      case rem => (int % 256).toByte :: seqBytes(rem)
    }
  }
  
  def tlv(valueType: Byte, value: => Byte): ByteString = {
    tlvs(valueType, ByteString(value))
  }
  
  def tlvs(valueType: Byte, value: => ByteString): ByteString = {
    val bs = ByteString.newBuilder
    bs.putByte(valueType)
    bs ++= (definiteLength(value.length))
    if (value.length > 0) {
      bs ++= (value)
    }
    bs.result
  }

  def objectId(oid: List[Int]): ByteString = {
    // TODO the prefix 1.3 has a short form (1 byte) .. first two are (x * 40) + y
    // TODO cache oid as bytestrings?
    //7 bits
    //1 unless
    //0 end
    oid match {
      case 1 :: 3 :: tail => {
        val bs = ByteString.newBuilder
        bs.putByte(Snmp.IsoOrg)
        for (i <- tail) {
          bs ++= overflowingInt(i)
        }
        bs.result
      }
      case _ => {
        throw new IllegalArgumentException("Todo")
      }
    }
  }
  
  def int(value: Int): ByteString =
  {
    // Taken from SNMP4J
    var integer = value
    var intsize = 4

    /*
     * Truncate "unnecessary" bytes off of the most significant end of this
     * 2's complement integer.  There should be no sequence of 9
     * consecutive 1's or 0's at the most significant end of the
     * integer.
     */
    var mask = 0x1FF << ((8 * 3) - 1)
    /* mask is 0xFF800000 on a big-endian machine */
    while((((integer & mask) == 0) || ((integer & mask) == mask))
          && intsize > 1){
      intsize = intsize - 1
      integer = integer << 8
    }
    mask = 0xFF << (8 * 3)
    /* mask is 0xFF000000 on a big-endian machine */
    val bs = ByteString.newBuilder
    while (intsize > 0){
      bs.putByte(((integer & mask) >> (8 * 3)).toByte)
      integer = integer << 8
      intsize = intsize - 1
    }
    bs.result
  }

  def overflowingInt(value: Int): List[Byte] = {
    overflowingInt(value, true)
  }

  private def overflowingInt(value: Int, first: Boolean): List[Byte] = {
    if (value < 0) {
      throw new IllegalArgumentException
    }
    else if (value < 128) {
      if (first) List(value.toByte) else List((value | 128).toByte)
    }
    else {
      overflowingInt(value >> 7, false) ::: overflowingInt(extractBits(value, 7), first)
    }
  }

  def extractBits(x: Int, numBits: Int): Int = {
    if (numBits < 1) {
        0;
    }
    else if (numBits > 32) {
        x;
    }
    else {
      val mask = (1 << numBits) - 1;
      x & mask;
    }
  }
  
  lazy val version2c: ByteString = {
    val bs = ByteString.newBuilder
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(1.toByte)
    bs.putByte(Version.V2c)
    bs.result
  }
  
  lazy val noErrorStatusAndIndex: ByteString = {
    val bs = ByteString.newBuilder
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(1.toByte)
    bs.putByte(0) // error status
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(1.toByte)
    bs.putByte(0) // error index
    bs.result
  }
}

object PduType {
  val GetRequest: Byte = 0xa0.toByte
  val GetResponse: Byte = 0xa2.toByte
}
object Version {
  val V2c: Byte = 0x1
}
object BerIdentifier {
  // Universal
  val Boolean: Byte = 0x1
  val Integer: Byte = 0x2
  val BitString: Byte = 0x3
  val OctetString: Byte = 0x4
  val Null: Byte = 0x5
  val ObjectId: Byte = 0x6
  val Sequence: Byte = 0x30
  val TimeTicks: Byte = 0x43
}