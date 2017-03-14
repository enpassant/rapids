package common

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

case class StatData(time: Long, success: Boolean = true)
case class Stat(reqNum: Long, min: Long, max: Long, avg: Long, failed: Long) {
  def add(time: Long, success: Boolean = true) = {
    Stat(
      if (success) reqNum + 1 else reqNum,
      if (time < min) time else min,
      if (time > max) time else max,
      avg + time,
      if (success) failed else failed + 1
    )
  }
}

case object Tick

class StatActor(msId: String, producer: SourceQueue[ProducerData[String]])
  extends Actor
{
  import context.dispatcher

  val tick =
    context.system.scheduler.schedule(5000 millis, 5000 millis, self, Tick)

  override def postStop() = tick.cancel()

  val emptyIntervalData = Stat(0, Long.MaxValue, 0, 0, 0)

  def receive = collect(emptyIntervalData)

  def collect(intervalData: Stat): Receive = {
    case StatData(time, success) =>
      context become collect(intervalData.add(time, success))
    case Tick =>
      val reqNum = intervalData.reqNum
      val stat = Stat(
        reqNum,
        if (reqNum > 0) intervalData.min else 0,
        intervalData.max,
        if (reqNum > 0) intervalData.avg / reqNum else intervalData.avg,
        intervalData.failed
      )
      producer offer
        ProducerData("performance", msId, CommonSerializer.toString(stat))
      context become collect(emptyIntervalData)
  }
}

object Performance {
  import scala.concurrent.ExecutionContext.Implicits.global

	def props(msId: String, producer: SourceQueue[ProducerData[String]]) =
    Props(new StatActor(msId, producer))

  def stat[T](statActor: ActorRef)(process: => T) = {
    val start = System.nanoTime
    val result = process
    val end = System.nanoTime

    statActor ! StatData((end - start) / 1000000)
    result
  }

  def statF[T](statActor: ActorRef)(process: => Future[T]) = {
    val start = System.nanoTime
    val result = process map {
      value =>
        val end = System.nanoTime
        statActor ! StatData((end - start) / 1000000)
        value
    }
    result.failed.foreach { e =>
      val end = System.nanoTime
      statActor ! StatData((end - start) / 1000000, false)
    }
    result
  }
}
