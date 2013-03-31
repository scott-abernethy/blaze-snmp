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

import akka.actor.{Props, ActorRef, Actor}
import akka.event.Logging

case class Target(address: String, port: Int)

case class GetRequest(target: Target, community: String, oids: List[ObjectIdentifier])

case class GetNextRequest(target: Target, community: String, oids: List[ObjectIdentifier])

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
  def receive = {
    case msg @ GetRequest(target, community, oids) => {
      targets.getOrElse(target, createSocket(target)) ! msg
    }
    case other => {
      log.warning("Unhandled {}", other)
      unhandled(other)
    }
  }

  def createSocket(target: Target): ActorRef = {
    val socket = context.actorOf(Props[SocketHandler], target.address)
    socket ! self
    socket ! target
    targets = targets + (target -> socket)
    socket
  }
}