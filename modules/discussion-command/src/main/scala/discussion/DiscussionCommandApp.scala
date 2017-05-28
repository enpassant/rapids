package discussion

import common._
import blog._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object DiscussionCommandApp extends App with Microservice {
	def start(implicit mq: MQProtocol, system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

		val producer = mq.createProducer[ProducerData[BlogMessage]](
      kafkaServer)
    {
			case ProducerData(topic, id, event) =>
				val value = BlogSerializer.toString(event)
				ProducerData(topic, id, value)
		}

		val producerStat = mq.createProducer[ProducerData[String]](
      kafkaServer)
    {
			case msg @ ProducerData(topic, id, value) => msg
		}

		val service = system.actorOf(DiscussionService.props, s"discussion-service")

    val statActor = system.actorOf(
      Performance.props("disc-command", producerStat))

		val consumer = mq.createConsumer(
      kafkaServer,
			"discussion-command",
			"discussion-command")
		{ msg =>
      Performance.statF(statActor) {
        implicit val timeout = Timeout(3000.milliseconds)
        val result = service ? common.ConsumerData(msg.key, msg.value)
        result collect {
          case event: DiscussionStarted =>
            producer.offer(ProducerData("blog-command", event.blogId, event))
            producer.offer(ProducerData("discussion-event", msg.key, event))
          case event: DiscussionEvent =>
            producer.offer(ProducerData("discussion-event", msg.key, event))
          case message: WrongMessage =>
            producer.offer(ProducerData("error", "FATAL", message))
          case message =>
        } recover {
          case e: Exception =>
            producer.offer(
              ProducerData("error", "FATAL", WrongMessage(e + " " + e.toString)))
        }
      }
		}
    consumer._2.onComplete {
      case Success(done) =>
      case Failure(throwable) => println(throwable)
    }
	}

  implicit val mq = Kafka
	implicit val system = ActorSystem("DiscussionCommandApp")
	import common.TypeHintContext._

	start
	scala.io.StdIn.readLine()
	system.terminate
}
