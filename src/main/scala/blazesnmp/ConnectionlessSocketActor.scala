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

import akka.actor.{UnhandledMessage, ActorKilledException, ActorRef, Actor}
import akka.event.Logging
import akka.io.{UdpFF, IO}
import akka.io.UdpFF._
import akka.util.ByteString
import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicInteger
import collection.immutable.Queue
import akka.io.UdpFF.Received
import akka.io.UdpFF.Bind
import scala.concurrent.duration.FiniteDuration

case class RequestPayload(payload: ByteString, to: InetSocketAddress)
case class ResponsePayload(payload: ByteString)

/**
 * Use multiple sockets at once, round robin, or assigned randomly to target worker.
 */
class ConnectionlessSocketActor extends Actor {

  val log = Logging(context.system, this)
  var conn: Option[ActorRef] = None
  var buffer = Seq[Send]()
  var out = 0
  val MaxOut = 2

  override def preStart() {
    super.preStart()
    val localAddress = new InetSocketAddress(ConnectionlessSocketActor.nextPort.incrementAndGet())
//    val localAddress = new InetSocketAddress(InetAddress.getByName("10.16.104.8"), ConnectionlessSocketActor.nextPort.incrementAndGet())
    log.debug("Binding to {}", localAddress)
    implicit val actorSystem = context.system
    IO(UdpFF) ! Bind(self, localAddress)
    context.system.scheduler.schedule(FiniteDuration(5, "seconds"), FiniteDuration(5, "seconds"), self, 'Drain)(context.dispatcher)
  }

  override def postStop() {
    super.postStop()
    implicit val actorSystem = context.system
    IO(UdpFF) ! Unbind
  }

  def receive = {
    case Bound => {
      log.debug("Bound on {}", sender)
      conn = Some(sender)
    }
    case RequestPayload(payload, target) => {
      val msg = Send(payload, target, WantAck)
      if (out < MaxOut && conn.isDefined) {
        conn.foreach(_ ! msg)
        out = out + 1
      }
      else {
        buffer = msg +: buffer
      }
    }
    case CommandFailed(msg: Send) => {
      log.info("Command failed, resending...")
      out = out - 1
      drain()
      buffer = msg +: buffer
    }
    case WantAck => {
      out = out - 1
      drain()
    }
    case 'Drain => {
      drain()
    }
    case Received(payload, from) => {
      // strategy to connect request & response - by global unique request Id .. by target address & request Id.
      val handler = responseHandler(from)
      log.debug("Received from {} piping to {}", from, handler)
      handler ! ResponsePayload(payload)
    }
  }

  def drain() {
    if (out < MaxOut && !buffer.isEmpty) {
      val (msg, tail) = (buffer.head, buffer.tail)
      buffer = tail
      conn.foreach(_ ! msg)
      out = out + 1
    }
  }

  def responseHandler(from: InetSocketAddress): ActorRef = {
    context.system.actorFor("/user/RequestHandler/" + SocketHandler.name(from))
  }
}

object ConnectionlessSocketActor {
  val nextPort = new AtomicInteger(9162)
}

case object WantAck {
}