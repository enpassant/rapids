package common

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import scala.concurrent.duration._

case class StatData(time: Long)
case class Stat(reqNum: Long, min: Long, max: Long, avg: Long)
case class IntervalData(reqNum: Long, min: Long, max: Long, sum: Long) {
  def add(time: Long) = {
    IntervalData(
      reqNum + 1,
      if (time < min) time else min,
      if (time > max) time else max,
      sum + time
    )
  }
}

class StatActor(msId: String, producer: SourceQueue[ProducerData[String]])
  extends Actor
{
  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(10000 millis, 10000 millis, self, "tick")

  override def postStop() = tick.cancel()

  val emptyIntervalData = IntervalData(0, Long.MaxValue, 0, 0)

  def receive = collect(emptyIntervalData)

  def collect(intervalData: IntervalData): Receive = {
    case StatData(time) =>
      context become collect(intervalData.add(time))
    case "tick" =>
      val reqNum = intervalData.reqNum
      val stat = Stat(
        reqNum,
        if (reqNum > 0) intervalData.min else 0,
        intervalData.max,
        if (reqNum > 0) intervalData.sum / reqNum else intervalData.sum
      )
      producer offer
        ProducerData("performance", msId, CommonSerializer.toString(stat))
      context become collect(emptyIntervalData)
  }
}

object Performance {
	def props(msId: String, producer: SourceQueue[ProducerData[String]]) =
    Props(new StatActor(msId, producer))

  def stat[T](statActor: ActorRef)(process: => T) = {
    val start = System.nanoTime
    val result = process
    val end = System.nanoTime

    statActor ! StatData(end - start)
    result
  }
}
