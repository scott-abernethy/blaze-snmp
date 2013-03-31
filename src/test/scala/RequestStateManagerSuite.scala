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
    x.cancel(1)
  }
  
  test("timeouts") {
    val x = new TargetState{
      waitingOn = Waiting(3, ByteString.empty, 98000l, 1) :: Waiting(2, ByteString.empty, 96000l, 1) :: Waiting(1, ByteString.empty, 90000l, 4) :: Nil
    }
    x.timeouts(97000l) should equal(RequestRetry(2, ByteString.empty) :: RequestTimeout(1) :: Nil)
    x.waitingOn should equal(Waiting(2, ByteString.empty, 97000l + 5000l, 2) :: Waiting(3, ByteString.empty, 98000l, 1) :: Nil)
    
    x.timeouts(1000l) should equal(Nil)
    x.waitingOn should equal(Waiting(2, ByteString.empty, 97000l + 5000l, 2) :: Waiting(3, ByteString.empty, 98000l, 1) :: Nil)
    
    x.timeouts(120000l) should equal(RequestRetry(2, ByteString.empty) :: RequestRetry(3, ByteString.empty) :: Nil)
    x.waitingOn should equal(Waiting(3, ByteString.empty, 125000l, 2) :: Waiting(2, ByteString.empty, 125000l, 3) :: Nil)
    
    x.timeouts(130000l) should equal(RequestRetry(3, ByteString.empty) :: RequestRetry(2, ByteString.empty) :: Nil)
    x.waitingOn should equal(Waiting(2, ByteString.empty, 135000l, 4) :: Waiting(3, ByteString.empty, 135000l, 3) :: Nil)
    
    x.timeouts(144000l) should equal(RequestTimeout(2) :: RequestRetry(3, ByteString.empty) :: Nil)
    x.waitingOn should equal(Waiting(3, ByteString.empty, 149000l, 4) :: Nil)
  }
}