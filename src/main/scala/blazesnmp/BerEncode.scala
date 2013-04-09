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

object BerEncode {

  implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

  def definiteLength(octets: Int): ByteString = {
    val bs = ByteString.newBuilder
    putDefiniteLength(octets)(bs)
    bs.result
  }

  def putDefiniteLength(octets: Int)(builder: ByteStringBuilder) {
    if (octets < 0) {
      throw new UnsupportedOperationException
    }
    else if (octets < 128) {
      builder.putByte(octets.toByte)
    }
    else {
      putSeqBytes(octets)(builder)
    }
  }

  private def putSeqBytes(int: Int)(builder: ByteStringBuilder) = {
    // Recurse down, with count, put count first, unwind putting ints.
    // TODO merge with put int below
    def put0(rem: Int, length: Int) {
      (rem >> 8) match {
        case 0 => {
          // Don't recurse further. Write length
          builder.putByte((128 + length).toByte)
        }
        case value => {
          // Down we go
          put0(value, length + 1)
        }
      }
      builder.putByte((rem % 256).toByte)
    }
    put0(int, 1)
  }
  
//  def tlv(valueType: Byte, value: => Byte): ByteString = {
//    tlvs(valueType, ByteString(value))
//  }
//
//  def tlvs(valueType: Byte, value: => ByteString): ByteString = {
//    val bs = ByteString.newBuilder
//    bs.putByte(valueType)
//    bs ++= (definiteLength(value.length))
//    if (value.length > 0) {
//      bs ++= (value)
//    }
//    bs.result
//  }

  def putTlv(valueType: Byte, value: ByteStringBuilder => Unit)(builder: ByteStringBuilder) {
    builder.putByte(valueType)
    val bs = ByteString.newBuilder
    value(bs)
    val valueBytes = bs.result
    putDefiniteLength(valueBytes.length)(builder)
    builder ++= valueBytes
  }

  /*
  all the puts need to, either
  a. return a length and a function that will do the write
  b. write length and value
  NOTE Seq could work well for this because it is prepend .. so given value, prepend length and prepend type.
   */

  def objectId(oid: Seq[Int]): ByteString = {
    val bs = ByteString.newBuilder
    putObjectId(oid)(bs)
    bs.result()
  }

  def putObjectId(oid: Seq[Int])(builder: ByteStringBuilder) {
    // first two are (x * 40) + y
    // 7 bits, 1 unless, 0 end
    oid match {
      case 1 +: 3 +: tail => {
        builder.putByte(Snmp.IsoOrg)
        for (i <- tail) {
          putOverflowingInt(i)(builder)
        }
      }
      case _ => {
        throw new IllegalArgumentException("Todo")
      }
    }
  }
  
  def int(value: Int): ByteString = {
    val bs = ByteString.newBuilder
    putInt(value)(bs)
    bs.result
  }

  def putInt(value: Int)(builder: ByteStringBuilder) {
    // Taken from SNMP4J, attribute.
    var integer = value
    var size = 4

    /*
     * Truncate "unnecessary" bytes off of the most significant end of this
     * 2's complement integer.  There should be no sequence of 9
     * consecutive 1's or 0's at the most significant end of the
     * integer.
     */
    var mask = 0x1FF << ((8 * 3) - 1)
    /* mask is 0xFF800000 on a big-endian machine */
    while((((integer & mask) == 0) || ((integer & mask) == mask))
          && size > 1){
      size = size - 1
      integer = integer << 8
    }
    mask = 0xFF << (8 * 3)
    /* mask is 0xFF000000 on a big-endian machine */
    while (size > 0){
      builder.putByte(((integer & mask) >> (8 * 3)).toByte)
      integer = integer << 8
      size = size - 1
    }
  }

  def overflowingInt(value: Int): ByteString = {
    val bs = ByteString.newBuilder
    putOverflowingInt(value)(bs)
    bs.result
  }

  def putOverflowingInt(value: Int)(builder: ByteStringBuilder) {
    def put0(value: Int, first: Boolean) {
      if (value < 0) {
        throw new IllegalArgumentException
      }
      else if (value < 128) {
        if (first) builder.putByte(value.toByte) else builder.putByte((value | 128).toByte)
      }
      else {
        put0(value >> 7, false)
        put0(extractBits(value, 7), first)
      }
    }
    put0(value, true)
  }

  def extractBits(x: Int, numBits: Int): Int = {
    if (numBits < 1) {
      0
    }
    else if (numBits > 32) {
      x
    }
    else {
      val mask = (1 << numBits) - 1
      x & mask
    }
  }
  
  val version2c: ByteString = {
    val bs = ByteString.newBuilder
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(1.toByte)
    bs.putByte(Version.V2c)
    bs.result.compact
  }
  
  val noErrorStatusAndIndex: ByteString = {
    val bs = ByteString.newBuilder
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(1.toByte)
    bs.putByte(0) // error status
    bs.putByte(BerIdentifier.Integer)
    bs.putByte(1.toByte)
    bs.putByte(0) // error index
    bs.result.compact
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