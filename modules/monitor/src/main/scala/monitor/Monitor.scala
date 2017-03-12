package monitor

import common._
import common.web.Directives._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{UpgradeToWebSocket, TextMessage}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._
import com.github.jknack.handlebars.{ Context, Handlebars, Template }
import com.mongodb.casbah.Imports._
import com.typesafe.config.ConfigFactory
import fixiegrips.{ Json4sHelpers, Json4sResolver }
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s._
import org.json4s.mongo.JObjectParser._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

object Monitor extends App with BaseFormats {
	def start(implicit system: ActorSystem, materializer: ActorMaterializer) = {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load

    val handlebars = new Handlebars().registerHelpers(Json4sHelpers)
    def ctx(obj: Object) =
      Context.newBuilder(obj).resolver(Json4sResolver).build
    val render = (template: Template) => (obj: Object) => template(ctx(obj))

    val monitor = handlebars.compile("monitor")

    var stats = Map.empty[String, Stat]
    //var stats = List.empty[Stat]

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"monitor",
			"performance")
		{ msg =>
      val jsonTry = Try(CommonSerializer.fromString(msg.record.value))
      val result = Future { jsonTry match {
        case Success(json) =>
          json match {
            case stat: Stat =>
              stats = stats + (new String(msg.record.key) -> stat)
              //stats = stats :+ stat
          }
        }
      }
      result map { _ => msg.committableOffset }
    }

		val producer = Kafka.createProducer[ProducerData[String]]("localhost:9092")
    {
			case ProducerData(topic, id, value) =>
				new ProducerRecord[Array[Byte], String](
					topic, id.getBytes(), value)
		}

    val wsSource = Source.tick(1.seconds, 1.seconds, TextMessage("teszt"))

    val link = CommonSerializer.toString(FunctionLink(10, "/monitor", "Monitor"))
    producer.offer(ProducerData("web-app", "monitor", link))

    val statActor = system.actorOf(Performance.props("monitor", producer))

		val route =
      pathPrefix("wsmonitor") {
				pathEnd {
					optionalHeaderValueByType[UpgradeToWebSocket]() {
						case Some(upgrade) =>
							complete(
								upgrade.handleMessagesWithSinkSource(Sink.ignore, wsSource))
						case None =>
							reject(ExpectedWebSocketRequestRejection)
					}
				}
      } ~
      path("monitor") {
        getFromResource(s"public/html/monitor.html")
      }

    stat(statActor)(route)
	}

	implicit val system = ActorSystem("Monitor")
	implicit val materializer = ActorMaterializer()
	val route = start
	val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8084)

	scala.io.StdIn.readLine()
	system.terminate
}

