import akka.actor.{ActorRef, Actor}
import akka.event.Logging
import akka.io.UdpConn.{Received, Send, Connected, Connect}
import akka.io.{UdpConn, IO}
import akka.util.ByteString
import concurrent.duration.FiniteDuration
import java.net.InetSocketAddress

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

case class Target(address: String, port: Int)

class SocketHandler extends Actor {
  val log = Logging(context.system, this)
  var target: Option[Target] = None
  var conn: ActorRef = context.system.deadLetters
  var requestId: Int = 0
  def receive = {
    case msg @ Target(address, port) => {
      target = Some(msg)
      implicit val actorSystem = context.system
      IO(UdpConn) ! Connect(self, new InetSocketAddress(address, port))
    }
    case Connected => {
      log.info("Connected to {}", target)
      conn = sender
    }
    case 'Test => {
      val community = "terminal"

      implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

      val frame = ByteString.newBuilder
      frame ++= Ber.version2c
      frame ++= Ber.tlvs(BerIdentifier.OctetString, ByteString(community.toList.map(_.toByte) : _*))

      val pdu = ByteString.newBuilder
      pdu ++= (Ber.tlv(BerIdentifier.Integer, requestId.toByte))
      requestId += 1

      pdu ++= Ber.noErrorStatusAndIndex

      pdu ++= Ber.tlvs(BerIdentifier.Sequence, {
        Ber.tlvs(BerIdentifier.Sequence, {
          Ber.tlvs(BerIdentifier.ObjectId, Ber.objectId(List(1,3,6,1,4,1,2509,8,7,2,1,0))) ++
            Ber.tlvs(BerIdentifier.Null, ByteString.empty)
        }) ++
          Ber.tlvs(BerIdentifier.Sequence, {
            Ber.tlvs(BerIdentifier.ObjectId, Ber.objectId(List(1,3,6,1,4,1,2509,8,7,2,2,0))) ++
              Ber.tlvs(BerIdentifier.Null, ByteString.empty)
          }) ++
          Ber.tlvs(BerIdentifier.Sequence, {
            Ber.tlvs(BerIdentifier.ObjectId, Ber.objectId(List(1,3,6,1,2,1,1,3,0))) ++
              Ber.tlvs(BerIdentifier.Null, ByteString.empty)
          })
      })

      val pduBytes = pdu.result

      frame ++= Ber.tlvs(PduType.GetRequest, pduBytes)
      //.1.3.6.1.2.1.1.3.0  sys uptime
      //.1.3.6.1.2.1.1.5.0  sys name
      //.1.3.6.1.2.1.1.6.0  sys location
      //.1.3.6.1.4.1.2509.8.21.2.1.0  last change index
      //1.3.6.1.4.1.2509.8.7.2.1 site name
      //1.3.6.1.4.1.2509.8.7.2.2 radio name

      val msg = Ber.tlvs(BerIdentifier.Sequence, frame.result)

      log.info("Sending")
      conn ! Send(msg)
    }
    case Received(data) => {
      implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

      val in = data.iterator
      log.info("Received {}", BerDecode.getTlv(in))
      /*in.getByte
      val length = BerDecode.getDefiniteLength(in)
      println("length " + length)
      val version = BerDecode.getTlv(in)
      println("version " + version)
      val community = BerDecode.getTlv(in) // TODO should just skip this, instead of decoding
      println("community " + community)
      val pduType = BerDecode.getInt(1, in)
      println("pdu type " + pduType)
      val pduLength = BerDecode.getDefiniteLength(in)
      println("pdu length " + pduLength)
      val requestId = BerDecode.getTlv(in)
      println("requestId " + requestId)
      val errorStatus = BerDecode.getTlv(in)
      println("errorStatus " + errorStatus)
      val errorIndex = BerDecode.getTlv(in)
      println("errorIndex " + errorIndex)
      val remains = BerDecode.getTlv(in)
      println("remains " + remains)
      */
      // seq
      // len
      //   int
      //   1
      //   version
      //   octetstring -- skip
      //   len -- skip
      //   community -- skip
      //   pdutype
      //   len
      //     int
      //     len
      //     requestId
      //     int
      //     len
      //     error status
      //     int
      //     len
      //     error index
      //     seq
      //     len
      //       seq
      //       len
      //         oid
      //         len
      //         oid value
      //         type
      //         len
      //         value
      //       ...


      /*val FrameDecoder = for {
        frameLenBytes <- akka.actor.IO.take(4)
		frameLen = frameLenBytes.iterator.getInt
		frame <- akka.actor.IO.take(frameLen)
      } yield {
		val in = frame.iterator

		val n = in.getInt
		val m = in.getInt

		val a = Array.newBuilder[Short]
		val b = Array.newBuilder[Long]

		for (i <- 1 to n) {
		  a += in.getShort
		  b += in.getInt
		}

		val data = Array.ofDim[Double](m)
		in.getDoubles(data)

		(a.result, b.result, data)
      }
      FrameDecoder(data)*/
    }
    case other => {
      log.warning("Unhandled {} for {}", other, target)
      unhandled(other)
    }
  }
}
