package blazesnmp

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

import akka.actor.{UnhandledMessage, ActorKilledException, ActorRef, Actor}
import akka.event.Logging
import akka.io.{UdpFF, IO}
import akka.io.UdpFF._
import akka.util.ByteString
import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicInteger
import collection.immutable.Queue
import akka.io.UdpFF.Received
import blazesnmp.ResponsePayload
import akka.io.UdpFF.Bind
import concurrent.duration.FiniteDuration

case class RequestPayload(payload: ByteString, to: InetSocketAddress)
case class ResponsePayload(payload: ByteString)

/**
 * Use multiple sockets at once, round robin, or assigned randomly to target worker.
 */
class ConnectionlessSocketActor extends Actor {

  val log = Logging(context.system, this)
  var conn: Option[ActorRef] = None
  var buffer = Queue[Send]()
//  var buffering = false

  override def preStart() {
    super.preStart
    val localAddress = new InetSocketAddress(ConnectionlessSocketActor.nextPort.incrementAndGet())
//    val localAddress = new InetSocketAddress(InetAddress.getByName("10.16.104.8"), ConnectionlessSocketActor.nextPort.incrementAndGet())
    log.debug("Binding to {}", localAddress)
    implicit val actorSystem = context.system
    IO(UdpFF) ! Bind(self, localAddress)
  }

  def receive = {
    case Bound => {
      log.debug("Bound on {}", sender)
      conn = Some(sender)
    }
    case msg @ Send(payload, target, NoAck) => {
//      if (/*!buffering && */conn.isDefined) {
        conn.foreach(_ ! msg)
//        buffering = true
//      }
//      else {
//        buffer = buffer.enqueue(msg)
//      }
    }
    case CommandFailed(msg @ Send(_, _, _)) => {
      log.info("fail")
//      buffering = false
//      buffer = buffer.enqueue(msg)
//      drain()
      conn.foreach(_ ! msg)
    }
    case WantAck => {
      log.info("ok")
//      buffering = false
//      drain()
    }
//    case 'Drain => {
//      drain()
//    }
    case Received(payload, from) => {
      // strategy to connect request & response - by global unique request Id .. by target address & request Id.
      val handler = responseHandler(from)
      log.debug("Received from {} piping to {}", from, handler)
      handler ! ResponsePayload(payload)
    }
    case other => {
      log.warning("Unhandled {}", other)
      unhandled(other)
    }
  }

//  def drain() {
//    if (!buffering && !buffer.isEmpty && conn.isDefined) {
//      val (msg, tail) = buffer.dequeue
//      buffer = tail
//      conn.foreach(_ ! msg)
//      buffering = true
//    }
//  }

  def responseHandler(from: InetSocketAddress): ActorRef = {
    context.system.actorFor("/user/RequestHandler/" + SocketHandler.name(from))
  }
}

object ConnectionlessSocketActor {
  val nextPort = new AtomicInteger(9162)
}

case object WantAck {
}