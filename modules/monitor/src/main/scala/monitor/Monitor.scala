package monitor

import common._
import common.web.Directives._
import config.ProductionKafkaConfig

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{UpgradeToWebSocket, TextMessage}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._
import com.mongodb.casbah.Imports._
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s._
import org.json4s.mongo.JObjectParser._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

object Monitor extends App with BaseFormats with Microservice {
	def start(implicit
    mq: MQProtocol,
    system: ActorSystem,
    materializer: ActorMaterializer) =
  {
		implicit val executionContext = system.dispatcher

    val (wsSourceQueue, wsSource) =
      Source.queue[String](100, OverflowStrategy.backpressure)
      .map(x => TextMessage(x))
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
      .run()

    val monitorActor = system.actorOf(MonitorActor.props(wsSourceQueue))

		val consumer = mq.createConsumer("monitor", "performance") { msg =>
      val jsonTry = Try(CommonSerializer.fromString(msg.value))
      val result = Future { jsonTry match {
        case Success(json) =>
          json match {
            case stat: Stat =>
              monitorActor ! (new String(msg.key), stat)
          }
        }
      }
      result
    }

    val (statActor, producer) = statActorAndProducer(mq, "monitor")

    val link = CommonSerializer.toString(FunctionLink(10, "/monitor", "Monitor"))
    producer.offer(ProducerData("web-app", "monitor", link))

		val route =
      pathPrefix("monitor") {
				path("ws") {
					optionalHeaderValueByType[UpgradeToWebSocket]() {
						case Some(upgrade) =>
							complete(
								upgrade.handleMessagesWithSinkSource(Sink.ignore, wsSource))
						case None =>
							reject(ExpectedWebSocketRequestRejection)
					}
        } ~
				pathEnd {
          getFromResource(s"public/html/monitor.html")
        }
      }

    stat(statActor)(route)
	}

	implicit val mq = new Kafka(ProductionKafkaConfig)
	implicit val system = ActorSystem("Monitor")
	implicit val materializer = ActorMaterializer()
	val bindingFuture = Http().bindAndHandle(start, "0.0.0.0", 8084)
}

