package discussion

import common._
import topic._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.duration._

object DiscussionCommandApp extends App {
	val system = ActorSystem("DiscussionCommandApp")
	import common.TypeHintContext._

	start(system)
	scala.io.StdIn.readLine()
	system.terminate

	def start(implicit system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

		val producer = Kafka.createProducer[DiscussionEvent]("localhost:9092") {
			case  event: DiscussionEvent =>
				val value = new TopicSerializer().toString(event)
				new ProducerRecord[Array[Byte], String](
					"discussion", event.id.getBytes(), value)
		}

		val service = system.actorOf(DiscussionService.props, s"discussion-service")

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"discussion-command",
			"discussion")
		{ msg =>
			val consumerRecord = msg.record
			implicit val timeout = Timeout(1000.milliseconds)
			val key = new String(consumerRecord.key)
			val result = service ? common.ConsumerData(key, consumerRecord.value)
			result collect {
				case event: DiscussionEvent =>
					producer.offer(event)
					msg.committableOffset
			}
		}
	}
}

