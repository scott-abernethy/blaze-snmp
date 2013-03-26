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

import akka.actor.{Actor,ActorRef,ActorSystem,Props}
import akka.event.Logging
import concurrent.duration.FiniteDuration

object HelloWorld {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("blaze")
    val soak = system.actorOf(Props[SoakActor], "soak")
    soak ! 'Start
  }
}

object SnmpWireProtocol {
  
}

object SnmpMessageProtocol {
  
}

class SoakActor extends Actor {
  val log = Logging(context.system, this)
  override def receive = {
    case 'Start => {
      createSocket(Target("10.16.10.77", 161))
      createSocket(Target("10.16.10.86", 161))
      createSocket(Target("10.16.10.87", 161))
      createSocket(Target("10.16.10.101", 161))
      createSocket(Target("10.16.10.102", 161))
      createSocket(Target("10.16.10.103", 161))
      createSocket(Target("10.16.10.104", 161))
      createSocket(Target("10.16.10.199", 161))
      createSocket(Target("10.16.10.202", 161))
      createSocket(Target("10.16.10.203", 161))
      createSocket(Target("10.16.10.204", 161))

      context.system.scheduler.schedule(FiniteDuration(1, "sec"), FiniteDuration(100, "sec"), self, 'Soak)(context.dispatcher)
    }
    case 'Soak => {
      for (socket <- context.children) socket ! 'Test
    }
    case other => {
      log.warning("Unhandled {}", other)
      unhandled(other)
    }
  }

  def createSocket (target: Target){
    val socket = context.actorOf(Props[SocketHandler], target.address)
    socket ! target
  }
}