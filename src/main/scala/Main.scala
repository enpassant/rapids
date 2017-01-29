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
import org.json4s.{ DefaultFormats, ShortTypeHints }
import scala.concurrent.Future

object Main extends App {
	val system = ActorSystem("Main")

	WebApp.start(system)
	scala.io.StdIn.readLine()
	system.terminate
}

object MainOld extends App with BaseFormats {
	implicit val system = ActorSystem("User")
	//import scala.concurrent.ExecutionContext.Implicits.global
	implicit val materializer = ActorMaterializer()
	implicit val executionContext = system.dispatcher

  implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(classOf[Json], classOf[Evt]))
	} ++ org.json4s.ext.JodaTimeSerializers.all

  val partition = 0
	val producerSettings = ProducerSettings(
		system,
		new ByteArraySerializer,
		new StringSerializer)
		.withBootstrapServers("localhost:9092")

	val done = Source(1 to 100)
		.map(_.toString)
		.map { elem =>
			new ProducerRecord[Array[Byte], String](
				"test1", partition, elem.getBytes(), elem + ". elem")
		}
		.runWith(Producer.plainSink(producerSettings))

	val consumerSettings = ConsumerSettings(
		system,
		new ByteArrayDeserializer,
		new StringDeserializer
	)
		.withBootstrapServers("localhost:9092")
		.withGroupId("service-1")
		.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val fromOffset = 0L
  val subscription = Subscriptions.assignmentWithOffset(
    new TopicPartition("test1", partition) -> fromOffset
  )
	def process(consumerRecord: ConsumerRecord[Array[Byte], String]) = Future {
		println(consumerRecord)
	}

  val done2 =
    Consumer.plainSource(consumerSettings, subscription)
			.mapAsync(1)(process)
      .runWith(Sink.ignore)

	done2.onComplete {
		done => system.terminate
	}

	val user = system.actorOf(User.props("12"))
	user ! Cmd("CreateUser")
	user ! Cmd("ChangeNick")
	user ! Cmd("print")
	user ! Cmd("snap")

	val route =
		path("hello") {
			get {
				complete(
					HttpEntity(
						ContentTypes.`text/html(UTF-8)`,
						"<h1>Say hello to akka-http</h1>"))
			}
		} ~
		path("commands") {
			post {
				formFields('command, 'age.as[Int]) { (command, age) =>
					complete(
						HttpEntity(
							ContentTypes.`text/plain(UTF-8)`,
							s"$command string and $age age are received"))
				} ~
				entity(as[Json]) { command =>
					command match {
						case Cmd("shutdown") =>
							user ! Shutdown
							system.terminate
							complete(
								HttpEntity(
									ContentTypes.`text/plain(UTF-8)`,
									"System shutting down"))
						case cmd: Cmd =>
							user ! cmd
							complete(
								HttpEntity(
									ContentTypes.`text/plain(UTF-8)`,
									"Command execution has started"))
					}
				} ~
				entity(as[String]) { command =>
					command match {
						case "shutdown" =>
							user ! Shutdown
							system.terminate
							complete(
								HttpEntity(
									ContentTypes.`text/plain(UTF-8)`,
									"System shutting down"))
					}
				}
			}
		}

	val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
}
