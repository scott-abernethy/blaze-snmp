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

import akka.actor.Actor
import collection.immutable.Queue

case class Timeout(at: Long, id: Int)

class TimeoutActor extends Actor {

  val outstanding = Queue.empty[Timeout]

  def receive = {
    case msg @ Timeout(at, id) => {
      // Assume messages come in with at in order, so we don't have to sort.

    }
    case other => {
      unhandled(other)
    }
  }
}
