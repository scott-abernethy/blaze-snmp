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

case class Waiting(id: Int, requester: ActorRef, payload: ByteString, at: Long, attempt: Int)

trait RequestTimeoutAction

case class RequestRetry(id: Int, payload: ByteString) extends RequestTimeoutAction

case class RequestTimeout(id: Int, requester: ActorRef) extends RequestTimeoutAction

trait TargetState {
  val MaxRequestId = (1 << 15) - 1
  val MinRequestId = 1 // TODO use neg numbers?
  var id = MinRequestId - 1
  var waitingOn = Map[Int, Waiting]()
  val Timeout = 5000 // msec
  val MaxRetries = 3

  def nextId(): Int = {
    id = if (id >= MaxRequestId) MinRequestId else id + 1
    id
  }

  def waitOn(id: Int, requester: ActorRef, payload: ByteString, at: Long) {
    waitOn(id, requester, payload, at, 1)
  }

  private def waitOn(id: Int, requester: ActorRef, payload: ByteString, at: Long, attempt: Int) {
    waitingOn = waitingOn + (id -> Waiting(id, requester, payload, at + Timeout, attempt))
  }

  def complete(id: Int): Option[ActorRef] = {
    val ref = waitingOn.get(id).map(_.requester)
    waitingOn = waitingOn - id
    ref
  }

  def timeouts(at: Long): Seq[RequestTimeoutAction] = {
    val (ok, timedOut) = waitingOn.partition(i => i._2.at > at)
    waitingOn = ok
    timedOut.toSeq.map{
      case (_, Waiting(id, requester, _, _, attempt)) if attempt > MaxRetries => RequestTimeout(id, requester)
      case (_, Waiting(id, requester, payload, _, attempt)) => {
        waitOn(id, requester, payload, at, attempt + 1)
        RequestRetry(id, payload)
      }
    }
  }
}