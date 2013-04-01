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

import akka.actor.{Props, ActorRef, Actor}
import akka.event.Logging
import java.net.InetSocketAddress
import akka.routing.RoundRobinRouter

case class Target(address: InetSocketAddress, community: String)

case class GetRequest(target: Target, oids: List[ObjectIdentifier])

case class GetResponse(errorStatus: Int, errorIndex: Int, varbinds: List[Varbind])

case class GetNextRequest(target: Target, oids: List[ObjectIdentifier])

case class RequestToken(requester: ActorRef, at: Long, id: Long, request: Any)

/**
 * Handles all incoming requests, and decides what sockets to create, when to close, when to reuse, etc.
 * Handles
 * GetRequest in v1 is atomic (all retrieved, or none), where as v2 each varbind can have value or error
 * GetNextRequest
 */
class RequestHandler extends Actor {

  val log = Logging(context.system, this)
  var targets = Map.empty[Target, ActorRef]
  val conn = context.actorOf(Props[ConnectionlessSocketActor], "ConnectionlessSocket")
//  val conn = context.actorOf(Props[ConnectionlessSocketActor].withRouter(RoundRobinRouter(nrOfInstances = 10)))

  def receive = {
    case msg @ GetRequest(target, oids) => {
      targets.getOrElse(target, createSocket(target)) forward msg
    }
    case other => {
      log.warning("Unhandled {}", other)
      unhandled(other)
    }
  }

  def createSocket(target: Target): ActorRef = {
    val address = target.address
    val socket = context.actorOf(Props[SocketHandler], SocketHandler.name(address))
    socket ! SocketConfig(conn, address)
    targets = targets + (target -> socket)
    socket
  }
}