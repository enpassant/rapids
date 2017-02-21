package blog

import common._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object BlogCommandApp extends App {
	def start(implicit system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

		val producer = Kafka.createProducer[ProducerData[BlogMessage]](
      "localhost:9092")
    {
			case ProducerData(topic, id, event) =>
				val value = new BlogSerializer().toString(event)
				new ProducerRecord[Array[Byte], String](
					topic, id.getBytes(), value)
		}

		val service = system.actorOf(BlogService.props, s"blog-service")

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"blog-command",
			"blog-command")
		{ msg =>
			val consumerRecord = msg.record
			implicit val timeout = Timeout(3000.milliseconds)
			val key = new String(consumerRecord.key)
			val result = service ? common.ConsumerData(key, consumerRecord.value)
			result collect {
				case event @ BlogCreated(blogId, title, content) =>
					//val id = common.CommonUtil.uuid
					val id = s"disc-$blogId"
					producer.offer(
            ProducerData("blog-event", blogId, event))
					producer.offer(
            ProducerData(
              "discussion-command", id, StartDiscussion(id, blogId, title)))
					msg.committableOffset
				case event @ DiscussionStarted(id, blogId, title) =>
					producer.offer(
            ProducerData("blog-event", blogId, event))
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

	val system = ActorSystem("BlogCommandApp")
	import common.TypeHintContext._

	start(system)
	scala.io.StdIn.readLine()
	system.terminate
}

