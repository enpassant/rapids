package monitor

import common._

import akka.actor._
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream._
import akka.stream.scaladsl._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

class MonitorActor(producer: SourceQueue[String]) extends Actor {
  import context.dispatcher

  val tick =
    context.system.scheduler.schedule(2000 millis, 2000 millis, self, Tick)

  override def postStop() = tick.cancel()

  def receive = collect(Map.empty[String, Stat])

  def collect(stats: Map[String, Stat]): Receive = {
    case (key: String, stat: Stat) =>
      val updatedStat =
        if (stats contains key) stats(key) add stat
        else stat
      context become collect(stats + (key -> updatedStat))
    case Tick =>
      producer offer CommonSerializer.toString(stats)
      val emptyStats = stats map {
        case (k, s) => k -> Stat(0, 0, 0, 0, 0, 0)
      }
      context become collect(emptyStats)
  }
}

object MonitorActor {
  import scala.concurrent.ExecutionContext.Implicits.global

	def props(producer: SourceQueue[String]) =
    Props(new MonitorActor(producer))
}
