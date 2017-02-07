package topic

import common._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.duration._

object TopicCommandApp extends App {
	val system = ActorSystem("TopicCommandApp")
	import common.TypeHintContext._

	start(system)
	scala.io.StdIn.readLine()
	system.terminate

	def start(implicit system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

		val producer = Kafka.createProducer[DiscussionCommand]("localhost:9092") {
			case cmd @ StartDiscussion(id, topicId, url, title) =>
				val value = new TopicSerializer().toString(cmd)
				new ProducerRecord[Array[Byte], String](
					"discussion", id.getBytes(), value)
		}

		val service = system.actorOf(TopicService.props, s"topic-service")

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"topic-command",
			"topic")
		{ msg =>
			val consumerRecord = msg.record
			implicit val timeout = Timeout(1000.milliseconds)
			val key = new String(consumerRecord.key)
			val result = service ? common.ConsumerData(key, consumerRecord.value)
			result collect {
				case TopicCreated(topicId, url, title) =>
					val id = common.CommonUtil.uuid
					producer.offer(StartDiscussion(id, topicId, url, title))
					msg.committableOffset
			}
		}
	}
}

