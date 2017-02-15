package discussion

import common._
import topic._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object DiscussionCommandApp extends App {
	val system = ActorSystem("DiscussionCommandApp")
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

		val service = system.actorOf(DiscussionService.props, s"discussion-service")

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"discussion-command",
			"discussion-command")
		{ msg =>
			val consumerRecord = msg.record
			implicit val timeout = Timeout(3000.milliseconds)
			val key = new String(consumerRecord.key)
			val result = service ? common.ConsumerData(key, consumerRecord.value)
			result collect {
				case event: DiscussionStarted =>
					producer.offer(ProducerData("topic-command", event.topicId, event))
					producer.offer(ProducerData("discussion-event", key, event))
					msg.committableOffset
				case event: DiscussionEvent =>
					producer.offer(ProducerData("discussion-event", key, event))
					msg.committableOffset
				case message: WrongMessage =>
					producer.offer(ProducerData("error", "FATAL", message))
					msg.committableOffset
				case message =>
					msg.committableOffset
			} recover {
        case e: Exception =>
					producer.offer(
            ProducerData("error", "FATAL", WrongMessage(e.toString)))
          msg.committableOffset
			}
		}
    consumer.onComplete {
      case Success(done) =>
      case Failure(throwable) => println(throwable)
    }
	}
}

