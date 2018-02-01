package common

import akka.actor._
import akka.persistence._
import com.mongodb.casbah.commons.Imports._
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
