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

import akka.util.{ByteIterator,ByteString}

object BerDecode {

  def getTlv(in: ByteIterator): Any = {
    val t = in.getByte
    val length = getDefiniteLength(in)
    t match {
      case BerIdentifier.Integer => {
        getInt(length, in)
      }
      case BerIdentifier.TimeTicks => {
        new TimeTicks(getInt(length, in))
      }
      case BerIdentifier.OctetString => {
        getOctetString(length, in)
      }
      case BerIdentifier.Sequence => {
        getSeqOfTlv(getIterator(in, length))
      }
      case BerIdentifier.ObjectId => {
        val content = Array.ofDim[Byte](length)
        in.getBytes(content)
        new ObjectIdentifier(getObjectId(content.toList))
      }
      case PduType.GetResponse => {
        (PduType.GetResponse, getSeqOfTlv(getIterator(in, length)))
      }
      case _ => {
        null
      }
    }
  }

  def getIterator(in: ByteIterator, length: Int): ByteIterator = {
    val copy = in.clone
    in.drop(length)
    copy.take(length)
  }

  def getSeqOfTlv(in: ByteIterator): List[Any] = {
    val head = getTlv(in)
    if (head == null) {
      Nil
    }
    else if (in.hasNext) {
      head :: getSeqOfTlv(in)
    }
    else {
      List(head)
    }
  }
  
  lazy val msb = 128.toByte
  
  def getDefiniteLength(in: ByteIterator): Int = {
    val first = in.getByte
    if (first < 0) {
      val more = (first ^ msb)
      getInt(more, in)
    }
    else {
      first.toInt
    }
  }
  
  def unsignedByte(byte: Byte): Int = {
    0xff & byte.asInstanceOf[Int]
  }
  
  def getInt(length: Int, in: ByteIterator): Int = {
    // TODO check bounds?
    val parts = for (i <- (length - 1) to (0, -1)) yield (unsignedByte(in.getByte) << (i * 8))
    parts.foldRight(0)(_ + _)
  }
  
  def getOctetString(length: Int, in: ByteIterator): OctetString = {
    // TODO check bounds?
    // Use Array here? That's what the Akka io docs use...
    val cs = for (_ <- 0 until length) yield in.getByte
    new OctetString(cs)
  }

  def getObjectId(bytes: Seq[Byte]): Seq[Int] = {
    bytes match {
      case Snmp.IsoOrg +: tail => {
        1 +: 3 +: getListOfOverflowingInt(tail, Nil)
      }
      case _ => {
        throw new IllegalArgumentException("Todo")
      }
    }
  }

  def getListOfOverflowingInt(bytes: Seq[Byte], overflow: Seq[Int]): Seq[Int] = {
    def sumValue(lsb: Int, msbs: Seq[Int]) = {
      lsb + List.range(0, msbs.size).
        map( i => msbs(i) << ((i + 1) * 7)).
        fold(0)(_ + _)
    }
    bytes match {
      case IncompleteOverflowingInt(value) :: xs => {
        getListOfOverflowingInt(xs, value +: overflow)
      }
      case CompleteOverflowingInt(value) :: xs => {
        sumValue(value, overflow) +: getListOfOverflowingInt(xs, Nil)
      }
      case _ => {
        Nil
      }
    }
  }

  object IncompleteOverflowingInt {
    def unapply(byte: Byte): Option[Int] = {
      Some(byte).filter(b => (b & msb) == msb).map(_ ^ msb)
    }
  }

  object CompleteOverflowingInt {
    def unapply(byte: Byte): Option[Int] = {
      Some(byte).filter(b => (b & msb) != msb).map(_.toInt)
    }
  }

  // TODO tidy up mess of list bytes or bytestring or array or iterator .. use same everywhere.

  // decode a seq as a list of objects... that have type and value.
  
}