package common

import org.apache.pekko.actor._
import org.apache.pekko.persistence._
import scala.concurrent.duration._

abstract class CommandActor extends Actor with PersistentActor {
  def restartTimeout() = context.setReceiveTimeout(10.minutes)

  restartTimeout()

  def processCommand: Receive

  val receiveCommand: Receive = {
    case ReceiveTimeout =>
      self ! PoisonPill
    case command =>
      restartTimeout()
      processCommand(command)
  }
}
