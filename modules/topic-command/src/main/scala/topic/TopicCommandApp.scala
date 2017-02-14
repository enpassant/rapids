package topic

import common._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TopicCommandApp extends App {
	val system = ActorSystem("TopicCommandApp")
	import common.TypeHintContext._

	start(system)
	scala.io.StdIn.readLine()
	system.terminate

	def start(implicit system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

		val producer = Kafka.createProducer[ProducerData[TopicMessage]](
      "localhost:9092")
    {
			case ProducerData(topic, id, event) =>
				val value = new TopicSerializer().toString(event)
				new ProducerRecord[Array[Byte], String](
					topic, id.getBytes(), value)
		}

		val service = system.actorOf(TopicService.props, s"topic-service")

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"topic-command",
			"topic-command")
		{ msg =>
			val consumerRecord = msg.record
			implicit val timeout = Timeout(1000.milliseconds)
			val key = new String(consumerRecord.key)
			val result = service ? common.ConsumerData(key, consumerRecord.value)
			result collect {
				case event @ TopicCreated(topicId, title, content) =>
					//val id = common.CommonUtil.uuid
					val id = s"disc-$topicId"
					producer.offer(
            ProducerData("topic-event", topicId, event))
					producer.offer(
            ProducerData(
              "discussion-command", id, StartDiscussion(id, topicId, title)))
					msg.committableOffset
				case message =>
					msg.committableOffset
			}
		}
    consumer.onComplete {
      case Success(done) =>
      case Failure(throwable) => println(throwable)
    }
	}
}

