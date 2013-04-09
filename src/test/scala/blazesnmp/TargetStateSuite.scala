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

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import akka.util.ByteString

class TargetStateSuite extends FunSuite with ShouldMatchers {
  test("next request id") {
    val x = new TargetState{}
    x.nextId should equal(1)
    x.nextId should equal(2)
    x.nextId should equal(3)
  }
  
  test("cancel request id") {
    val x = new TargetState{}
    x.complete(1)
  }
  
  test("timeouts") {
    implicit val actorSystem = ActorSystem()
    try {
      val p1 = TestProbe()
      val p2 = TestProbe()
      val p3 = TestProbe()
      val x = new TargetState{
        waitingOn = Map(
          3 -> Waiting(3, p3.ref, ByteString.empty, 98000l, 1),
          2 -> Waiting(2, p2.ref, ByteString.empty, 96000l, 1),
          1 -> Waiting(1, p1.ref, ByteString.empty, 90000l, 4))
      }

      {
        val t = x.timeouts(97000l)
        t should have length (2)
        t should contain (RequestRetry(2, ByteString.empty).asInstanceOf[RequestTimeoutAction])
        t should contain (RequestTimeout(1, p1.ref).asInstanceOf[RequestTimeoutAction])
        x.waitingOn should have size (2)
        x.waitingOn should contain (2 -> Waiting(2, p2.ref, ByteString.empty, 97000l + 5000l, 2))
        x.waitingOn should contain (3 -> Waiting(3, p3.ref, ByteString.empty, 98000l, 1))
      }

      {
        val t = x.timeouts(1000l)
        t should have length (0)
        x.waitingOn should have size (2)
        x.waitingOn should contain (2 -> Waiting(2, p2.ref, ByteString.empty, 97000l + 5000l, 2))
        x.waitingOn should contain (3 -> Waiting(3, p3.ref, ByteString.empty, 98000l, 1))
      }

      {
        val t = x.timeouts(120000l)
        t should have length (2)
        t should contain (RequestRetry(2, ByteString.empty).asInstanceOf[RequestTimeoutAction])
        t should contain (RequestRetry(3, ByteString.empty).asInstanceOf[RequestTimeoutAction])
        x.waitingOn should have size (2)
        x.waitingOn should contain (2 -> Waiting(2, p2.ref, ByteString.empty, 125000l, 3))
        x.waitingOn should contain (3 -> Waiting(3, p3.ref, ByteString.empty, 125000l, 2))
      }

      {
        val t = x.timeouts(130000l)
        t should have length (2)
        t should contain (RequestRetry(2, ByteString.empty).asInstanceOf[RequestTimeoutAction])
        t should contain (RequestRetry(3, ByteString.empty).asInstanceOf[RequestTimeoutAction])
        x.waitingOn should have size (2)
        x.waitingOn should contain (2 -> Waiting(2, p2.ref, ByteString.empty, 135000l, 4))
        x.waitingOn should contain (3 -> Waiting(3, p3.ref, ByteString.empty, 135000l, 3))
      }

      {
        val t = x.timeouts(144000l)
        t should have length (2)
        t should contain (RequestTimeout(2, p2.ref).asInstanceOf[RequestTimeoutAction])
        t should contain (RequestRetry(3, ByteString.empty).asInstanceOf[RequestTimeoutAction])
        x.waitingOn should have size (1)
        x.waitingOn should contain (3 -> Waiting(3, p3.ref, ByteString.empty, 149000l, 4))
      }
    }
    finally {
      actorSystem.shutdown
    }
  }
}