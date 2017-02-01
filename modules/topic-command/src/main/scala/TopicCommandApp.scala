import common._

import akka.actor._
import akka.kafka._
import akka.kafka.scaladsl._
import akka.pattern.ask
import akka.util.Timeout
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization._
import scala.concurrent.duration._

object TopicCommandApp extends App with BaseFormats {
	val system = ActorSystem("TopicCommandApp")

	start(system)
	scala.io.StdIn.readLine()
	system.terminate

	def start(implicit system: ActorSystem) = {
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher

		val consumerSettings = ConsumerSettings(
			system,
			new ByteArrayDeserializer,
			new StringDeserializer
		)
			.withBootstrapServers("localhost:9092")
			.withGroupId("service-1")
			.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

		val partition = 0
		val fromOffset = 0L
		val subscription = Subscriptions.assignmentWithOffset(
			new TopicPartition("topic", partition) -> fromOffset
		)

		val service = system.actorOf(TopicService.props, s"topic-service")
		def process(consumerRecord: ConsumerRecord[Array[Byte], String]) = {
			implicit val timeout = Timeout(100.milliseconds)
			val key = new String(consumerRecord.key)
			println(s"Process $key topic with value: ${consumerRecord.value}")
			service ? ConsumerMessage(key, consumerRecord.value)
		}

		val consumer =
			Consumer.plainSource(consumerSettings, subscription)
				.mapAsync(1)(process)
				.runWith(Sink.ignore)
	}
}

