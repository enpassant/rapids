package common

import akka.actor._
import akka.kafka._
import akka.kafka.ConsumerMessage._
import akka.kafka.scaladsl._
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._
import scala.concurrent.Future

object Kafka {
	def createProducer[A](server: String)
		(mapper: A => ProducerRecord[Array[Byte], String])
		(implicit system: ActorSystem) =
	{
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher

		val producerSettings = ProducerSettings(
			system,
			new ByteArraySerializer,
			new StringSerializer)
			.withBootstrapServers(server)

		Source.queue[A](256, OverflowStrategy.backpressure)
			.map(mapper)
			.to(Producer.plainSink(producerSettings))
			.run()
	}

	def createConsumer(server: String, groupId: String, topic: String)
		(mapper:
			CommittableMessage[Array[Byte], String] => Future[CommittableOffset])
		(implicit system: ActorSystem) =
	{
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher

		val consumerSettings = ConsumerSettings(
			system,
			new ByteArrayDeserializer,
			new StringDeserializer
		)
			.withBootstrapServers(server)
			.withGroupId(groupId)

		Consumer.committableSource(
			consumerSettings,
			Subscriptions.topics(topic)
		)
			.mapAsync(1)(mapper)
			.batch(
				max = 20,
				first => CommittableOffsetBatch.empty.updated(first)
			)((batch, elem) => batch.updated(elem))
			.mapAsync(3)(_.commitScaladsl())
			.runWith(Sink.ignore)
	}
}
