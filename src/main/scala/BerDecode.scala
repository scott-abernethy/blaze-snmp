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

import akka.util.{ByteIterator,ByteString}

object BerDecode {
  def getTlv(in: ByteIterator): Any = {
    in.getByte match {
      case BerIdentifier.Integer => {
        val length = getDefiniteLength(in)
        getInt(length, in)
      }
      case BerIdentifier.TimeTicks => {
        val length = getDefiniteLength(in)
        getInt(length, in)
      }
      case BerIdentifier.OctetString => {
        val length = getDefiniteLength(in)
        getOctetString(length, in)
      }
      case BerIdentifier.Sequence => {
        val length = getDefiniteLength(in)
        val content = Array.ofDim[Byte](length)
        in.getBytes(content)
        val seq = ByteString(content)
        getSeqOfTlv(seq.iterator)
      }
      case BerIdentifier.ObjectId => {
        val length = getDefiniteLength(in)
        val content = Array.ofDim[Byte](length)
        in.getBytes(content)
        content.toList
      }
      case PduType.GetResponse => {
        val length = getDefiniteLength(in)
        val content = Array.ofDim[Byte](length)
        in.getBytes(content)
        val seq = ByteString(content)
        ("GetResponse", getSeqOfTlv(seq.iterator))
      }
      case _ => null
    }
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
    if ((first & msb) == msb) {
      val more = (first ^ msb).toInt
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
    val parts = for (i <- List.range(0, length).reverse) yield (unsignedByte(in.getByte) << (i * 8))
    parts.foldRight(0)(_ + _)
  }
  
  def getOctetString(length: Int, in: ByteIterator): String = {
    // TODO check bounds?
    //val s: String = in.take(length).toList.map(b => b.toChar).mkString
    //s
    val cs = for (i <- List.range(0, length)) yield in.getByte.toChar
    cs.mkString
  }
  
  // decode a seq as a list of objects... that have type and value.
  
}