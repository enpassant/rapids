package monitor

import common._
import common.web.Directives._
import config.ProductionKafkaConfig

import org.apache.pekko.actor._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{UpgradeToWebSocket, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.ExpectedWebSocketRequestRejection
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import scala.concurrent.Future
import scala.util.{Try, Success}

object Monitor extends App with BaseFormats with Microservice {
  def start(implicit
    mq: MQProtocol,
    system: ActorSystem,
    materializer: Materializer) =
  {
    implicit val executionContext = system.dispatcher

    val (wsSourceQueue, wsSource) =
      Source.queue[String](100, OverflowStrategy.backpressure)
      .map(x => TextMessage(x))
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
      .run()

    val monitorActor = system.actorOf(MonitorActor.props(wsSourceQueue))

    val _ = mq.createConsumer("monitor", "performance") { msg =>
      val jsonTry = Try(CommonSerializer.fromString(msg.value))
      val result = Future { jsonTry match {
        case Success(json) =>
          json match {
            case stat: Stat =>
              monitorActor ! (new String(msg.key), stat)
            case _ =>
          }
        case _ =>
      } }
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

  implicit val mq: Kafka = new Kafka(ProductionKafkaConfig)
  implicit val system: ActorSystem = ActorSystem("Monitor")
  implicit val materializer: Materializer = Materializer(system)
  val bindingFuture = Http().newServerAt("0.0.0.0", 8084).bindFlow(start)
}

