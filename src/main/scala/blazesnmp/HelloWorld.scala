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

import akka.actor.{Actor,ActorRef,ActorSystem,Props}
import akka.event.Logging
import akka.pattern.Patterns
import concurrent.duration.FiniteDuration
import java.net.InetSocketAddress
import util.{Success, Failure}

object HelloWorld {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("blaze")
    val soak = system.actorOf(Props[SoakActor], "soak")
    soak ! new SnmpServiceImpl(system)
    soak ! 'Start
  }
}

/**
 * What will the soak test do? Write it in Java, using this lib, and the SNMP4J lib in pv-base. Get and get-next a bunch of objects from 1000s of radios, using 100s of threads. Record perf stats.
 */
class SoakActor extends Actor {
  var service: SnmpService = _
  val log = Logging(context.system, this)
  val targets = List(
    Target(new InetSocketAddress("10.16.10.77", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.86", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.87", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.101", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.102", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.103", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.104", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.199", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.202", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.203", 161), "terminal"),
    Target(new InetSocketAddress("10.16.10.204", 161), "terminal")
  )
  override def receive = {
    case s: SnmpService => {
      service = s
    }
    case 'Start => {
      context.system.scheduler.schedule(FiniteDuration(1, "sec"), FiniteDuration(1, "second"), self, 'Soak)(context.dispatcher)
    }
    case 'Soak => {
      implicit val executionContext = context.dispatcher
      for (t <- targets) {
        val start = System.currentTimeMillis
        service.getRequest(t, List(
          ObjectIdentifier.RadioName,
          ObjectIdentifier.SiteName,
          ObjectIdentifier.SysLocation,
          ObjectIdentifier.SysUpTime
        )).onComplete{
          case Success(GetResponse(errorStatus, errorIndex, varbinds)) => {
            log.info("Got {} in {}", varbinds, System.currentTimeMillis - start)
          }
          case Failure(e) => {
            log.warning("Failed with {}", e)
          }
        }
      }
    }
    case other => {
      log.warning("Unhandled {}", other)
      unhandled(other)
    }
  }
}