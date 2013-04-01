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

import akka.actor.ActorRef
import akka.util.ByteString

trait TargetState {
  val MaxRequestId = (1 << 15) - 1
  val MinRequestId = 1
  var id = MinRequestId - 1
  var waitingOn = List[Waiting]()
  
  def nextId(): Int = {
    id = if (id >= MaxRequestId) MinRequestId else id + 1
    id
  }
  def waitOn(id: Int, requester: ActorRef, payload: ByteString, at: Long) {
    waitOn(id, requester, payload, at, 1)
  }
  private def waitOn(id: Int, requester: ActorRef, payload: ByteString, at: Long, attempt: Int) {
    waitingOn = Waiting(id, requester, payload, at + 5000, attempt) :: waitingOn
  }
  def complete(id: Int): Option[ActorRef] = {
    val (remaining, cancelled) = waitingOn.span(id != _.id)
    waitingOn = remaining
    cancelled.headOption.map(_.requester)
  }
  def timeouts(at: Long): List[Any] = {
    val (ok, timedOut) = waitingOn.partition(_.at > at)
    waitingOn = ok
    timedOut.map{
      case Waiting(id, requester, _, _, attempt) if attempt > 3 => RequestTimeout(id, requester)
      case Waiting(id, requester, payload, _, attempt) => {
        waitOn(id, requester, payload, at, attempt + 1)
        RequestRetry(id, payload)
      }
    }
  }
}

case class Waiting(id: Int, requester: ActorRef, payload: ByteString, at: Long, attempt: Int)

case class RequestRetry(id: Int, payload: ByteString)

case class RequestTimeout(id: Int, requester: ActorRef)