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
import com.typesafe.config._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._
import scala.concurrent.Future
import scala.util.Try

object ClientApp extends App {
	def start() = {
		val config = ConfigFactory.load()
			.withValue(
				"akka.remote.netty.tcp.port",
				ConfigValueFactory.fromAnyRef("2553"))
		implicit val system = ActorSystem("ClientApp", config)
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher

		val partition = 0
		val consumerSettings = ConsumerSettings(
			system,
			new ByteArrayDeserializer,
			new StringDeserializer
		)
			.withBootstrapServers("localhost:9092")
			.withGroupId("clientapp-1")
			.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

		val fromOffset = 0L
		val subscription = Subscriptions.assignmentWithOffset(
			new TopicPartition("client-commands", partition) -> fromOffset
		)

		def process(consumerRecord: ConsumerRecord[Array[Byte], String]) = {
			val key = new String(consumerRecord.key)
			ClientCommand(key, consumerRecord.value)
		}

		val clientActor = system.actorOf(Props(new ClientActor()))

		val consumer =
			Consumer.plainSource(consumerSettings, subscription)
				.map(process)
				.runWith(Sink.actorRef(clientActor, Completed))

		val line = scala.io.StdIn.readLine()
		system.terminate
	}

	start
}

case object Completed
case class ClientCommand(key: String, value: String)

class ClientActor extends Actor {
	def receive = processClient(Map.empty[String, ActorRef])

	def processClient(clients: Map[String, ActorRef]): Receive = {
		case ClientCommand(key, value) =>
			val jsonTry = Try(parse(value))
			jsonTry.foreach { json =>
				val tpe = compact(render(json \ "_t"))
				println(tpe)
				if (tpe == "Login") {
					val url = compact(render(json \ "url"))
					val actorRef = context.actorSelection(url) ! GetActorRef(key)
				}
			}

		case GetActorRef(key) =>
			println(s"Add login actorRef for id $key client")
			context become processClient(clients + (key -> sender()))

		case msg => println(msg)
	}
}
