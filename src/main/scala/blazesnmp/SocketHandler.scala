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

import akka.actor.{ActorRef, Actor}
import akka.event.Logging
import akka.io.UdpFF.{NoAck, Send}
import akka.util.ByteString
import java.net.InetSocketAddress
import scala.concurrent.duration.FiniteDuration

case class SocketConfig(connection: ActorRef, remoteAddress: InetSocketAddress)

/**
 * Timeouts are 1-10 seconds?
 * Is Timeout target dependent, or request dependent, or both?
 * Resizing packets is target dependent ... as they could be on different networks.
 */
class SocketHandler extends Actor with TargetState {
  val log = Logging(context.system, this)
  var target: Option[InetSocketAddress] = None
  var conn: ActorRef = context.system.deadLetters

  override def preStart() {
    super.preStart
    context.system.scheduler.schedule(FiniteDuration(2, "sec"), FiniteDuration(2, "sec"), self, 'Check)(context.dispatcher)
  }
  
  def receive = {
    case SocketConfig(ref, address) => {
      target = Some(address)
      conn = ref
    }
    case GetRequest(target, oids) => {
      val respondTo = sender
      val requestId = nextId
      log.debug("Sending {}", requestId)
      val msg = encodeGetRequest(target.community, requestId, oids)
      send(msg)
      waitOn(requestId, respondTo, msg, System.currentTimeMillis)
    }
    case ResponsePayload(data) => {
      val decoded = BerDecode.getTlv(data.iterator)
      decoded match {
        case SnmpV2Msg(community, PduType.GetResponse, requestId, errorStatus, errorIndex, varbinds) => {
          complete(requestId).foreach(_ ! GetResponse(errorStatus, errorIndex, varbinds.collect{
            case List(id: ObjectIdentifier, variable: Variable) => Varbind(id, variable)
          }))
        }
        case _ => {
          log.warning("Can't fathom {}", decoded)
        }
      }
    }
    case 'Check => {
      timeouts(System.currentTimeMillis).foreach{
        case RequestRetry(id, payload) => {
          log.debug("Retry for request {} on {}", id, target)
          send(payload)
        }
        case RequestTimeout(id, requester) => {
          log.debug("Timeout for request {} on {}", id, target)
          // Send to requester?
        }
      }
    }
    case other => {
      log.warning("Unhandled {} for {}", other, target)
      unhandled(other)
    }
  }

  def encodeGetRequest(community: String, requestId: Int, oids: List[ObjectIdentifier]): ByteString = {
    implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

    val frame = ByteString.newBuilder
    frame ++= Ber.version2c
    frame ++= Ber.tlvs(BerIdentifier.OctetString, ByteString(OctetString.create(community).bytes: _*))

    val pdu = ByteString.newBuilder
    pdu ++= (Ber.tlvs(BerIdentifier.Integer, Ber.int(requestId)))

    pdu ++= Ber.noErrorStatusAndIndex

    pdu ++= Ber.tlvs(BerIdentifier.Sequence, {
      oids.map {
        id =>
          Ber.tlvs(BerIdentifier.Sequence, {
            Ber.tlvs(BerIdentifier.ObjectId, Ber.objectId(id.toList)) ++
              Ber.tlvs(BerIdentifier.Null, ByteString.empty)
          })
      }.reduce(_ ++ _)
    })

    val pduBytes = pdu.result

    frame ++= Ber.tlvs(PduType.GetRequest, pduBytes)

    Ber.tlvs(BerIdentifier.Sequence, frame.result)
  }

  def send(payload: ByteString) {
    for (to <- target) conn ! Send(payload, to, NoAck)
  }
}

object SocketHandler {
  def name(address: InetSocketAddress): String = {
    address.getAddress.getHostAddress + ":" + address.getPort
  }
}
