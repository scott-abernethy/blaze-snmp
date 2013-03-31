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
import akka.io.{UdpFF, IO}
import akka.io.UdpFF.{Send, Received, Bound, Bind}
import akka.util.ByteString
import java.net.InetSocketAddress

case class RequestPayload(payload: ByteString, to: InetSocketAddress)
case class ResponsePayload(payload: ByteString)

class ConnectionlessSocketActor extends Actor {

  val log = Logging(context.system, this)
  var conn: ActorRef = context.system.deadLetters

  override def preStart() {
    super.preStart
    val localAddress = new InetSocketAddress(9162)
    log.info("Binding to {}", localAddress)
    implicit val actorSystem = context.system
    IO(UdpFF) ! Bind(self, localAddress)
  }

  def receive = {
    case Bound => {
      log.info("Bound on {}", sender)
      conn = sender
    }
    case msg @ Send(_, _, _) => {
      // TODO or just give this ref to the users directly
      conn ! msg
    }
    case Received(payload, from) => {
      // strategy to connect request & response - by global unique request Id .. by target address & request Id.
      val handler = responseHandler(from)
      log.info("Received from {} piping to {}", from, handler)
      handler ! ResponsePayload(payload)
    }
    case other => {
      unhandled(other)
    }
  }

  def responseHandler(from: InetSocketAddress): ActorRef = {
    context.system.actorFor("/user/RequestHandler/" + SocketHandler.name(from))
  }
}
