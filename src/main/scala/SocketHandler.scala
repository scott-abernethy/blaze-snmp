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

import akka.actor.{ActorRef, Actor}
import akka.event.Logging
import akka.io.UdpConn.{Received, Send, Connected, Connect}
import akka.io.{UdpConn, IO}
import akka.util.ByteString
import java.net.InetSocketAddress
import scala.concurrent.duration.FiniteDuration

/**
 * Timeouts are 1-10 seconds?
 * Is Timeout target dependent, or request dependent, or both?
 * Resizing packets is target dependent ... as they could be on different networks.
 */
class SocketHandler extends Actor with TargetState {
  val log = Logging(context.system, this)
  var target: Option[Target] = None
  var conn: ActorRef = context.system.deadLetters
  var requester: ActorRef = context.system.deadLetters

  override def preStart() {
    super.preStart
    context.system.scheduler.schedule(FiniteDuration(1, "sec"), FiniteDuration(1, "sec"), self, 'Check)(context.dispatcher)
  }
  
  def receive = {
    case msg @ Target(address, port) => {
      target = Some(msg)
      implicit val actorSystem = context.system
      IO(UdpConn) ! Connect(self, new InetSocketAddress(address, port))
    }
    case ref: ActorRef => {
      requester = ref
    }
    case Connected => {
      log.info("Connected to {}", target)
      conn = sender
    }
    case GetRequest(_, community, oids) => {
      val requestId = nextId
      
      implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

      val frame = ByteString.newBuilder
      frame ++= Ber.version2c
      frame ++= Ber.tlvs(BerIdentifier.OctetString, ByteString(OctetString.create(community).bytes : _*))

      val pdu = ByteString.newBuilder
      pdu ++= (Ber.tlvs(BerIdentifier.Integer, Ber.int(requestId)))

      pdu ++= Ber.noErrorStatusAndIndex

      pdu ++= Ber.tlvs(BerIdentifier.Sequence, {
        oids.map{ id =>
          Ber.tlvs(BerIdentifier.Sequence, {
            Ber.tlvs(BerIdentifier.ObjectId, Ber.objectId(id.toList)) ++
              Ber.tlvs(BerIdentifier.Null, ByteString.empty)
          })
        }.reduce(_ ++ _)
      })

      val pduBytes = pdu.result

      frame ++= Ber.tlvs(PduType.GetRequest, pduBytes)

      val msg = Ber.tlvs(BerIdentifier.Sequence, frame.result)

      log.info("Sending {}", requestId)
      conn ! Send(msg)
      waitOn(requestId, msg, System.currentTimeMillis)
    }
    case Received(data) => {
      implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

      val in = data.iterator
      val decoded = BerDecode.getTlv(in)

      decoded match {
        case SnmpV2Msg(community, pduType, requestId, errorStatus, errorIndex, varbinds) => {
//          for (requester <- requests.get(requestId)) {
//            log.info("Response to {} of {}", requestId, varbinds)
//          }
//          requests = requests - requestId
          requester ! (community, pduType, requestId, errorStatus, errorIndex, varbinds)
          cancel(requestId)          
        }
        case _ => {
          log.warning("Can't fathom {}", decoded)
        }
      }

      //List(1, terminal, (GetResponse,List(1, 0, 0, List(List(List(1, 3, 6, 1, 4, 1, 2509, 8, 7, 2, 2, 0), INUe 204), List(List(1, 3, 6, 1, 4, 1, 2509, 8, 7, 2, 1, 0), Downstairs NMS Equipment Room), List(List(1, 3, 6, 1, 2, 1, 1, 3, 0), 112943164)))))
    }
    case 'Check => {
      timeouts(System.currentTimeMillis).foreach{
        case RequestRetry(id, payload) => {
          log.info("Retry for request {} on {}", id, target)
          conn ! Send(payload)
        }
        case RequestTimeout(id) => {
          log.warning("Timeout for request {} on {}", id, target)
        }
      }
    }
    case other => {
      log.warning("Unhandled {} for {}", other, target)
      unhandled(other)
    }
  }

  object SnmpV2Msg {
    def unapply(in: Any): Option[(String, Byte, Int, Int, Int, Any)] = {
      in match {
        case List(1, community: String, (pduType: Byte, List(requestId: Int, errorStatus: Int, errorIndex: Int, varbinds))) => Some(community, pduType, requestId, errorStatus, errorIndex, varbinds)
        case _ => None
      }
    }
  }
}
