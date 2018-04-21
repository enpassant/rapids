package testError

import common._
import config.ProductionKafkaConfig

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import scala.concurrent.Future

object TestError extends App with BaseFormats with Microservice {
  def start(implicit
    mq: MQProtocol,
    system: ActorSystem,
    materializer: ActorMaterializer) =
  {
    implicit val executionContext = system.dispatcher

    val consumer = mq.createConsumer("testError", "error") {
      case ConsumerData(key, value) =>
        println(s"$key error message: $value")
        Future { ConsumerData(key, value) }
      case msg =>
        println(s"Error message: $msg")
        Future { msg }
    }
  }

  implicit val mq = new Kafka(ProductionKafkaConfig)
  implicit val system = ActorSystem("Monitor")
  implicit val materializer = ActorMaterializer()
  start

  scala.io.StdIn.readLine()
  system.terminate
}

