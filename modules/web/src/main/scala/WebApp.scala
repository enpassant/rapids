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
import akka.http.scaladsl.unmarshalling._
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._
import scala.concurrent.Future

object WebApp extends App {
	val system = ActorSystem("WebApp")
	start(system)
	scala.io.StdIn.readLine()
	system.terminate

	def start(implicit system: ActorSystem) = {
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher

		val producerSettings = ProducerSettings(
			system,
			new ByteArraySerializer,
			new StringSerializer)
			.withBootstrapServers("localhost:9092")

		val producer = Source.queue[(String, Int, String, String)](
			256, OverflowStrategy.backpressure
		)
			.map { case (topic, partition, key, value) =>
				new ProducerRecord[Array[Byte], String](
					topic, key.getBytes(), value)
			}
			.to(Producer.plainSink(producerSettings))
			.run()

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
			pathPrefix("commands") {
				pathPrefix(Segment) { topic =>
					path(Segment) { id =>
						post {
							entity(as[String]) { message =>
								onSuccess(producer.offer((topic, 0, id, message))) {
									reply =>
										complete(
											HttpEntity(
												ContentTypes.`text/html(UTF-8)`,
												s"<h1>Topic: $topic</h1>"))
								}
							}
						}
					}
				}
			} ~
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
			} ~
			path("system") {
				post {
					entity(as[String]) {
						case "shutdown" =>
							system.terminate
							complete(
								HttpEntity(
									ContentTypes.`text/plain(UTF-8)`,
									"System shut down"))
						}
				}
			} ~
			path("") {
				getFromResource(s"public/html/index.html")
			} ~
			path("""([^/]+\.html).*""".r) { path =>
				getFromResource(s"public/html/$path")
			} ~
			path(Remaining) { path =>
				getFromResource(s"public/$path")
			}

		val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
	}
}

