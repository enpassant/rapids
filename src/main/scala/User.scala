import akka.actor._
import akka.kafka._
import akka.kafka.scaladsl._
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._

object User extends App {
	implicit val system = ActorSystem("User")
	implicit val materializer = ActorMaterializer()
	import scala.concurrent.ExecutionContext.Implicits.global

  val producerSettings = ProducerSettings(
		system,
		new ByteArraySerializer,
		new StringSerializer)
		.withBootstrapServers("localhost:9092")

	val done = Source(1 to 100)
		.map(_.toString)
		.map { elem =>
			new ProducerRecord[Array[Byte], String]("UserCommand", elem)
		}
		.runWith(Producer.plainSink(producerSettings))

  //val consumerSettings = ConsumerSettings(
		//system,
		//new ByteArrayDeserializer,
		//new StringDeserializer)
		//.withBootstrapServers("localhost:9092")
		//.withGroupId("service-1")
		//.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

	done.onComplete {
		done => system.terminate
	}
}

