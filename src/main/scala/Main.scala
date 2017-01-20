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
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._

object Main extends App with BaseFormats {
	implicit val system = ActorSystem("User")
	//import scala.concurrent.ExecutionContext.Implicits.global
	implicit val materializer = ActorMaterializer()
	implicit val executionContext = system.dispatcher

  //val producerSettings = ProducerSettings(
		//system,
		//new ByteArraySerializer,
		//new StringSerializer)
		//.withBootstrapServers("localhost:9092")

	//val done = Source(1 to 100)
		//.map(_.toString)
		//.map { elem =>
			//new ProducerRecord[Array[Byte], String]("UserCommand", elem)
		//}
		//.runWith(Producer.plainSink(producerSettings))

  //val consumerSettings = ConsumerSettings(
		//system,
		//new ByteArrayDeserializer,
		//new StringDeserializer)
		//.withBootstrapServers("localhost:9092")
		//.withGroupId("service-1")
		//.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

	//done.onComplete {
		//done => system.terminate
	//}

	val user = system.actorOf(User.props("12"))
	user ! Cmd("CreateUser")
	user ! Cmd("ChangeNick")
	user ! "print"
	user ! "snap"
	user ! Shutdown
	user ! Cmd("ChangeEmail")

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
				entity(as[Cmd]) { command =>
					command match {
						case Cmd("shutdown") =>
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
