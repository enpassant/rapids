package topic

import common._

import akka.actor._
import akka.kafka._
import akka.kafka.ConsumerMessage._
import akka.kafka.scaladsl._
import akka.pattern.ask
import akka.util.Timeout
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization._
import scala.concurrent.duration._
import salat._

trait DiscussionCommand extends Json
case class StartDiscussion(
	id: String,
	topicId: String,
	url: String,
	title: String
)	extends DiscussionCommand

object TopicCommandApp extends App with BaseFormats {
	val system = ActorSystem("TopicCommandApp")
	import common.TypeHintContext._

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

		val producer = Source.queue[DiscussionCommand](
			256, OverflowStrategy.backpressure
		)
			.map { case cmd @ StartDiscussion(id, topicId, url, title) =>
				val value = grater[StartDiscussion].toPrettyJSON(cmd)
				new ProducerRecord[Array[Byte], String](
					"discussion", id.getBytes(), value)
			}
			.to(Producer.plainSink(producerSettings))
			.run()

		val consumerSettings = ConsumerSettings(
			system,
			new ByteArrayDeserializer,
			new StringDeserializer
		)
			.withBootstrapServers("localhost:9092")
			.withGroupId("service-1")
			//.withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

		val service = system.actorOf(TopicService.props, s"topic-service")
		def process(consumerRecord: ConsumerRecord[Array[Byte], String]) = {
			implicit val timeout = Timeout(1000.milliseconds)
			val key = new String(consumerRecord.key)
			val result = service ? ConsumerData(key, consumerRecord.value)
			result foreach { event =>
				event match {
					case TopicCreated(topicId, url, title) =>
						val id = CommonUtil.uuid
						producer.offer(StartDiscussion(id, topicId, url, title))
				}
			}
			result
		}

		val consumer = Consumer.committableSource(
			consumerSettings,
			Subscriptions.topics("topic")
		)
			.mapAsync(1) {
				msg => process(msg.record).map(_ => msg.committableOffset)
			}
			.batch(
				max = 20,
				first => CommittableOffsetBatch.empty.updated(first)
			) {
					(batch, elem) => batch.updated(elem)
				}
			.mapAsync(3)(_.commitScaladsl())
			.runWith(Sink.ignore)
	}
}

