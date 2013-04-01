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

import akka.actor.{ActorSystem, Props}
import akka.pattern.Patterns
import concurrent.Future

trait SnmpService {
  def getRequest(target: Target, varbinds: List[ObjectIdentifier]): Future[GetResponse]
}

class SnmpServiceImpl(system: ActorSystem) extends SnmpService {
  val handler = system.actorOf(Props[RequestHandler], "RequestHandler")

  def getRequest(target: Target, varbinds: List[ObjectIdentifier]) = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Patterns.ask(handler, GetRequest(target, varbinds), 20000l).collect{
      case valid: GetResponse => valid
    }
  }
}
