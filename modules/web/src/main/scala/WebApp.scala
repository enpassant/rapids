import common._

import akka.actor._
import akka.kafka._
import akka.kafka.scaladsl._
import akka.persistence._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
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
	implicit val system = ActorSystem("WebApp")
	implicit val materializer = ActorMaterializer()
	implicit val executionContext = system.dispatcher

  val partition = 0
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
				topic, partition, key.getBytes(), value)
		}
		.to(Producer.plainSink(producerSettings))
		.run()

	val route =
		path("commands") {
			post {
				entity(as[String]) { command =>
					command match {
						case "shutdown" =>
							system.terminate
							complete(
								HttpEntity(
									ContentTypes.`text/plain(UTF-8)`,
									"System shutting down"))
					}
				}
			}
		} ~
		pathPrefix("commands") {
			pathPrefix(Segment) { topic =>
				path(Segment) { id =>
					post {
						entity(as[String]) { message =>
							producer.offer((topic, 0, id, message))
							complete(
								HttpEntity(
									ContentTypes.`text/html(UTF-8)`,
									s"<h1>Topic: $topic</h1>"))
						}
					}
				}
			}
		}

	val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
}

