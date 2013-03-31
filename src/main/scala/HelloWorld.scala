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

/**
 * What will the soak test do? Write it in Java, using this lib, and the SNMP4J lib in pv-base. Get and get next a bunch of objects from 1000s of radios, using 100s of threads. Record perf stats.
 */
class SoakActor extends Actor {
  val log = Logging(context.system, this)
  val handler = context.actorOf(Props[RequestHandler], "RequestHandler")
  val targets = List(
    "10.16.10.77",
    "10.16.10.86",
//    "10.16.10.87",
//    "10.16.10.101",
//    "10.16.10.102",
//    "10.16.10.103",
//    "10.16.10.104",
//    "10.16.10.199",
//    "10.16.10.202",
//    "10.16.10.203",
    "10.16.10.204"
  )
  override def receive = {
    case 'Start => {
      context.system.scheduler.schedule(FiniteDuration(1, "sec"), FiniteDuration(30, "sec"), self, 'Soak)(context.dispatcher)
    }
    case 'Soak => {
      for (t <- targets) handler ! GetRequest(Target(t, 161), "terminal", List(
        ObjectIdentifier.RadioName,
        ObjectIdentifier.SiteName,
        ObjectIdentifier.SysUpTime
      ))
    }
    case other => {
      log.warning("Unhandled {}", other)
      unhandled(other)
    }
  }
}