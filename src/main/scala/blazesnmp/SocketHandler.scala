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
  import BerEncode._

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
      val requestId = nextId()
      //val start = System.currentTimeMillis
      val msg = encodeGetRequest(target.community, requestId, oids)
      //encodes = (System.currentTimeMillis - start) :: encodes
      log.debug("Sending {}", requestId)
      send(msg)
      waitOn(requestId, respondTo, msg, System.currentTimeMillis)
    }
    case ResponsePayload(data) => {
      log.debug("Receiving {}", data)
      //val start = System.currentTimeMillis
      val decoded = BerDecode.getTlv(data.iterator)
      decoded match {
        case SnmpV2Msg(community, PduType.GetResponse, requestId, errorStatus, errorIndex, varbinds) => {
          val c = complete(requestId)
          //decodes = (System.currentTimeMillis - start) :: decodes
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
  }

  // Could pre-build entire request for repeated requests? ... but then i can't programmatically split the request depending on MTU size.
  // Share community cache?
  var communityCache = Map[String, ByteString]()
  var requestOidCache = Map[ObjectIdentifier, ByteString]()

  def addToCommunityCache(community: String): ByteString = {
    val encoded = communityEncoding(community).compact
    communityCache = communityCache + (community -> encoded)
    encoded
  }

  def communityEncoding(community: String): ByteString = {
    val builder = ByteString.newBuilder
    putTlv(BerIdentifier.OctetString, x => {
      for (byte <- OctetString.create(community).bytes) x.putByte(byte)
    })(builder)
    builder.result()
  }

  def addToRequestOidCache(id: ObjectIdentifier): ByteString = {
    val encoded = requestOidEncoding(id).compact
    requestOidCache = requestOidCache + (id -> encoded)
    encoded
  }

  def requestOidEncoding(id: ObjectIdentifier): ByteString = {
    val builder = ByteString.newBuilder
    putTlv(BerIdentifier.Sequence, seq => {
      putTlv(BerIdentifier.ObjectId, putObjectId(id.toSeq) _)(seq)
      putTlv(BerIdentifier.Null, _ => {})(seq)
    })(builder)
    builder.result()
  }

  def encodeGetRequest(community: String, requestId: Int, oids: List[ObjectIdentifier]): ByteString = {
    implicit val byteOrder = java.nio.ByteOrder.BIG_ENDIAN

    val builder = ByteString.newBuilder
    builder.sizeHint(200)

    putTlv(BerIdentifier.Sequence, frame => {
      frame ++= version2c
      frame ++= communityCache.getOrElse(community, addToCommunityCache(community))
      putTlv(PduType.GetRequest, pdu => {
        putTlv(BerIdentifier.Integer, putInt(requestId) _)(pdu)
        pdu ++= noErrorStatusAndIndex
        putTlv(BerIdentifier.Sequence, seq => {
          oids.map {
          id => requestOidCache.getOrElse(id, addToRequestOidCache(id))
          }.foreach( seq ++= _ )
        })(pdu)
      })(frame)
    })(builder)

    builder.result
  }

  def send(payload: ByteString) {
    for (to <- target) conn ! RequestPayload(payload, to)
  }
}

object SocketHandler {
  def name(address: InetSocketAddress): String = {
    address.getAddress.getHostAddress + ":" + address.getPort
  }
}
