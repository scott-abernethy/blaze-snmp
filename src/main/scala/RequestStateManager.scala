import akka.util.ByteString
import scala.collection.immutable.Queue

trait RequestStateManager {
  var id = Map.empty[Target, TargetState]
  
  def add(target: Target, request: Any): Int = {
    id.getOrElse(target, createNewTarget(target)).nextId
  }
  
  private def createNewTarget(target: Target): TargetState = {
    val t = new TargetState{}
    id = id + (target -> t)
    t
  }
  
  def clear(requestId: Int) {
    
  }
  
  // or use cyclic array buffer?
}

trait TargetState {
  val MaxRequestId = (1 << 15) - 1
  val MinRequestId = 1
  var id = MinRequestId - 1
  var waitingOn = List[Waiting]()
  
  def nextId(): Int = {
    id = if (id >= MaxRequestId) MinRequestId else id + 1
    id
  }
  def waitOn(id: Int, payload: ByteString, at: Long) {
    waitOn(id, payload, at, 1)
  }
  private def waitOn(id: Int, payload: ByteString, at: Long, attempt: Int) {
    waitingOn = Waiting(id, payload, at + 5000, attempt) :: waitingOn
  }
  def cancel(id: Int) {
    waitingOn = waitingOn.filter(id != _.id)
  }
  def timeouts(at: Long): List[Any] = {
    val (ok, timedOut) = waitingOn.partition(_.at > at)
    waitingOn = ok
    timedOut.map{
      case Waiting(id, _, _, attempt) if attempt > 3 => RequestTimeout(id)
      case Waiting(id, payload, _, attempt) => {
        waitOn(id, payload, at, attempt + 1)
        RequestRetry(id, payload)
      }
    }
  }
}

case class Waiting(id: Int, payload: ByteString, at: Long, attempt: Int)

case class RequestRetry(id: Int, payload: ByteString)

case class RequestTimeout(id: Int)