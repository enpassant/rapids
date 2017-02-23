import common._

import akka.actor._
import akka.kafka._
import akka.kafka.scaladsl._
import akka.persistence._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{UpgradeToWebSocket, TextMessage}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization._
import scala.concurrent.Future

object WebSocketApp extends App {
	def start(implicit system: ActorSystem, materializer: ActorMaterializer) = {
		implicit val executionContext = system.dispatcher

		val consumerSettings = ConsumerSettings(
			system,
			new ByteArrayDeserializer,
			new StringDeserializer
		)
			.withBootstrapServers("localhost:9092")
			.withGroupId("webapp-1")
			.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

		val fromOffset = 0L
		val partition = 0
		val subscription = Subscriptions.assignmentWithOffset(
			new TopicPartition("client-commands", partition) -> fromOffset
		)
		def process(consumerRecord: ConsumerRecord[Array[Byte], String]) = Future {
			TextMessage(consumerRecord.toString)
		}

		def filter(clientId: String)
			(consumerRecord: ConsumerRecord[Array[Byte], String]) =
		{
			new String(consumerRecord.key) == clientId
		}

		def consumer(clientId: String) =
			Consumer.plainSource(consumerSettings, subscription)
				.filter(filter(clientId))
				.mapAsync(1)(process)

		val route =
			pathPrefix("updates") {
				path(Segment) { id =>
					optionalHeaderValueByType[UpgradeToWebSocket]() {
						case Some(upgrade) =>
							complete(
								upgrade.handleMessagesWithSinkSource(Sink.ignore, consumer(id)))
						case None =>
							reject(ExpectedWebSocketRequestRejection)
					}
				}
			}
		route
	}

	implicit val system = ActorSystem("WebSocketApp")
	implicit val materializer = ActorMaterializer()
	val route = start

	val bindingFuture = Http().bindAndHandle(route, "localhost", 8082)

	scala.io.StdIn.readLine()
	system.terminate
}

