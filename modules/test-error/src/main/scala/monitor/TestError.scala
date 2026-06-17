package testError

import common._
import config.ProductionKafkaConfig

import org.apache.pekko.actor._
import org.apache.pekko.stream._
import scala.concurrent.Future

object TestError extends App with BaseFormats with Microservice {
  def start(implicit
    mq: MQProtocol,
    system: ActorSystem) =
  {
    implicit val executionContext = system.dispatcher

    val _ = mq.createConsumer("testError", "error") {
      case ConsumerData(key, value) =>
        println(s"$key error message: $value")
        Future { ConsumerData(key, value) }
      case msg =>
        println(s"Error message: $msg")
        Future { msg }
    }
  }

  implicit val mq: Kafka = new Kafka(ProductionKafkaConfig)
  implicit val system: ActorSystem = ActorSystem("Monitor")
  implicit val materializer: Materializer = Materializer(system)
  start

  scala.io.StdIn.readLine()
  system.terminate()
}

