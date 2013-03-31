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

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import akka.util.ByteString

class RequestStateManagerSuite extends FunSuite with ShouldMatchers {
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
        waitingOn = Waiting(3, p3.ref, ByteString.empty, 98000l, 1) :: Waiting(2, p2.ref, ByteString.empty, 96000l, 1) :: Waiting(1, p1.ref, ByteString.empty, 90000l, 4) :: Nil
      }
      x.timeouts(97000l) should equal(RequestRetry(2, ByteString.empty) :: RequestTimeout(1, p1.ref) :: Nil)
      x.waitingOn should equal(Waiting(2, p2.ref, ByteString.empty, 97000l + 5000l, 2) :: Waiting(3, p3.ref, ByteString.empty, 98000l, 1) :: Nil)

      x.timeouts(1000l) should equal(Nil)
      x.waitingOn should equal(Waiting(2, p2.ref, ByteString.empty, 97000l + 5000l, 2) :: Waiting(3, p3.ref, ByteString.empty, 98000l, 1) :: Nil)

      x.timeouts(120000l) should equal(RequestRetry(2, ByteString.empty) :: RequestRetry(3, ByteString.empty) :: Nil)
      x.waitingOn should equal(Waiting(3, p3.ref, ByteString.empty, 125000l, 2) :: Waiting(2, p2.ref, ByteString.empty, 125000l, 3) :: Nil)

      x.timeouts(130000l) should equal(RequestRetry(3, ByteString.empty) :: RequestRetry(2, ByteString.empty) :: Nil)
      x.waitingOn should equal(Waiting(2, p2.ref, ByteString.empty, 135000l, 4) :: Waiting(3, p3.ref, ByteString.empty, 135000l, 3) :: Nil)

      x.timeouts(144000l) should equal(RequestTimeout(2, p2.ref) :: RequestRetry(3, ByteString.empty) :: Nil)
      x.waitingOn should equal(Waiting(3, p3.ref, ByteString.empty, 149000l, 4) :: Nil)
    }
    finally {
      actorSystem.shutdown
    }
  }
}