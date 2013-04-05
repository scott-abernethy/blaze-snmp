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

  var encodes = List[Long]()
  var decodes = List[Long]()

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
      val start = System.currentTimeMillis
      val msg = encodeGetRequest(target.community, requestId, oids)
      encodes = (System.currentTimeMillis - start) :: encodes
      log.debug("Sending {}", requestId)
      send(msg)
      waitOn(requestId, respondTo, msg, System.currentTimeMillis)
    }
    case ResponsePayload(data) => {
      val start = System.currentTimeMillis
      val decoded = BerDecode.getTlv(data.iterator)
      decoded match {
        case SnmpV2Msg(community, PduType.GetResponse, requestId, errorStatus, errorIndex, varbinds) => {
          val c = complete(requestId)
          decodes = (System.currentTimeMillis - start) :: decodes
          c.foreach(_ ! GetResponse(errorStatus, errorIndex, varbinds.collect{
            case List(id: ObjectIdentifier, variable: Variable) => Varbind(id, variable)
          }))
        }
        case _ => {
          log.warning("Can't fathom {}", decoded)
        }
      }
    }
    case 'Check => {
//      val eavg = encodes.sum.toDouble / encodes.size
//      val davg = decodes.sum.toDouble / decodes.size
//      log.info(f"cod~ $eavg%3.3f / dec ~ $davg%3.3f")
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

  // Share community cache?
  var communityCache = Map[String, ByteString]()
  var requestOidCache = Map[ObjectIdentifier, ByteString]()

  def addToCommunityCache(community: String): ByteString = {
    val encoded = communityEncoding(community).compact
    communityCache = communityCache + (community -> encoded)
    encoded
  }


  def communityEncoding(community: String): ByteString = {
    Ber.tlvs(BerIdentifier.OctetString, ByteString(OctetString.create(community).bytes: _*))
  }

  def addToRequestOidCache(id: ObjectIdentifier): ByteString = {
    val encoded = requestOidEncoding(id).compact
    requestOidCache = requestOidCache + (id -> encoded)
    encoded
  }

  def requestOidEncoding(id: ObjectIdentifier): ByteString = {
    Ber.tlvs(BerIdentifier.Sequence, {
      Ber.tlvs(BerIdentifier.ObjectId, Ber.objectId(id.toSeq)) ++
        Ber.tlvs(BerIdentifier.Null, ByteString.empty)
    })
  }

  def encodeGetRequest(community: String, requestId: Int, oids: List[ObjectIdentifier]): ByteString = {
    implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

    val frame = ByteString.newBuilder
    frame ++= Ber.version2c
    frame ++= communityCache.getOrElse(community, addToCommunityCache(community))

    val pdu = ByteString.newBuilder
    pdu ++= Ber.tlvs(BerIdentifier.Integer, Ber.int(requestId))
    pdu ++= Ber.noErrorStatusAndIndex
    pdu ++= Ber.tlvs(BerIdentifier.Sequence, {
      oids.map {
//        id => requestOidEncoding(id)
        id => requestOidCache.getOrElse(id, addToRequestOidCache(id))
      }.reduce(_ ++ _)
    })

    frame ++= Ber.tlvs(PduType.GetRequest, pdu.result)

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
