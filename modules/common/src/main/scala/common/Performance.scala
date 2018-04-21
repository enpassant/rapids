package common

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

case class StatData(time: Long, success: Boolean = true)
case class Stat(
  reqNum: Long,
  min: Long,
  max: Long,
  avg: Long,
  failed: Long,
  time: Long)
{
  def add(duration: Long, success: Boolean = true) = {
    Stat(
      if (success) reqNum + 1 else reqNum,
      if (duration < min) duration else min,
      if (duration > max) duration else max,
      avg + duration,
      if (success) failed else failed + 1,
      System.nanoTime / 1000000
    )
  }

  def add(stat: Stat) = {
    Stat(
      reqNum + stat.reqNum,
      if (stat.min < min) stat.min else min,
      if (stat.max > max) stat.max else max,
      avg + stat.avg,
      failed + stat.failed,
      stat.time
    )
  }
}

case object Tick

class StatActor(msId: String, producer: SourceQueue[ProducerData[String]])
  extends Actor
{
  import context.dispatcher

  val tick =
    context.system.scheduler.schedule(1000 millis, 1000 millis, self, Tick)

  override def postStop() = tick.cancel()

  val emptyIntervalData = Stat(0, Long.MaxValue, 0, 0, 0, 0)

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
        intervalData.failed,
        System.currentTimeMillis / 1000
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
