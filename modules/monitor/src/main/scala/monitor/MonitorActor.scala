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
    context.system.scheduler.schedule(5000 millis, 5000 millis, self, Tick)

  override def postStop() = tick.cancel()

  var stats = Map.empty[String, Stat]

  val emptyIntervalData = Stat(0, Long.MaxValue, 0, 0, 0)

  def receive = collect(Map.empty[String, Stat])

  def collect(stats: Map[String, Stat]): Receive = {
    case (key: String, stat: Stat) =>
      context become collect(stats + (key -> stat))
    case Tick =>
      producer offer CommonSerializer.toString(stats)
  }
}

object MonitorActor {
  import scala.concurrent.ExecutionContext.Implicits.global

	def props(producer: SourceQueue[String]) =
    Props(new MonitorActor(producer))
}
