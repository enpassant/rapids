package testError

import common._

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import com.typesafe.config.ConfigFactory
import org.json4s._
import org.json4s.mongo.JObjectParser._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

object TestError extends App with BaseFormats with Microservice {
	def start(implicit
    mq: MQProtocol,
    system: ActorSystem,
    materializer: ActorMaterializer) =
  {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load

		val consumer = mq.createConsumer(
      kafkaServer,
			"testError",
			"error")
    {
      case ConsumerData(key, value) =>
        println(s"$key error message: $value")
        Future { ConsumerData(key, value) }
      case msg =>
        println(s"Error message: $msg")
        Future { msg }
    }
	}

  implicit val mq = Kafka
	implicit val system = ActorSystem("Monitor")
	implicit val materializer = ActorMaterializer()
	start

	scala.io.StdIn.readLine()
	system.terminate
}

