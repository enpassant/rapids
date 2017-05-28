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

object Kafka extends MQProtocol {
	def createProducer[A](server: String)
		(mapper: A => ProducerData[String])
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
			.map { input =>
        val msg = mapper(input)
				new ProducerRecord[Array[Byte], String](
					msg.topic, msg.key.getBytes(), msg.value)
      }
			.to(Producer.plainSink(producerSettings))
			.run()
	}

	private def createBaseConsumerSource(
    server: String,
    groupId: String,
    topic: String*)
		(implicit system: ActorSystem) =
	{
		val consumerSettings = ConsumerSettings(
			system,
			new ByteArrayDeserializer,
			new StringDeserializer
		)
			.withBootstrapServers(server)
			.withGroupId(groupId)

		Consumer.committableSource(
			consumerSettings,
			Subscriptions.topics(topic :_*)
		)
	}

	def createConsumerSource[T](server: String, groupId: String, topic: String*)
    (mapper: ConsumerData => Future[T])
		(implicit system: ActorSystem) =
	{
		implicit val executionContext = system.dispatcher

    createBaseConsumerSource(server, groupId, topic :_*)
			.mapAsync(1) { msg =>
        mapper(ConsumerData(new String(msg.record.key), msg.record.value))
          .map((_, msg))
      }
      .mapAsync(1) { msg =>
        msg._2.committableOffset.commitScaladsl() map {
          _ => (new String(msg._2.record.key), msg._1)
        }
      }
      .viaMat(KillSwitches.single)(Keep.right)
  }

	def createConsumer[T](server: String, groupId: String, topic: String*)
    (mapper: ConsumerData => Future[T])
		(implicit system: ActorSystem) =
	{
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher

    createBaseConsumerSource(server, groupId, topic :_*)
      .mapAsync(1) { msg =>
        mapper(ConsumerData(new String(msg.record.key), msg.record.value))
          .map(m => msg.committableOffset)
      }
			.batch(
				max = 20,
				first => CommittableOffsetBatch.empty.updated(first)
			)((batch, elem) => batch.updated(elem))
			.mapAsync(3)(_.commitScaladsl())
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.ignore)(Keep.both)
      .run()
	}
}
