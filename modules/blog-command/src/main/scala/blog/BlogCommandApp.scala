package blog

import common._
import config.ProductionKafkaConfig

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object BlogCommandApp extends App with Microservice {
	def start(implicit mq: MQProtocol, system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

		val producer = mq.createProducer[ProducerData[BlogMessage]]()
    {
			case ProducerData(topic, id, event) =>
				val value = BlogSerializer.toString(event)
				ProducerData(topic, id, value)
		}

		val producerStat = mq.createProducer[ProducerData[String]]()
    {
			case msg @ ProducerData(topic, id, value) => msg
		}

		val service = system.actorOf(BlogService.props, s"blog-service")

    val statActor = system.actorOf(
      Performance.props("blog-command", producerStat))

		val consumer = mq.createConsumer(
			"blog-command",
			"blog-command")
		{ msg =>
      Performance.statF(statActor) {
        implicit val timeout = Timeout(3000.milliseconds)
        val result = service ? common.ConsumerData(msg.key, msg.value)
        result collect {
          case event @ BlogCreated(blogId, userId, userName, title, content) =>
            //val id = common.CommonUtil.uuid
            val id = s"disc-$blogId"
            producer.offer(
              ProducerData("blog-event", blogId, event))
            producer.offer(
              ProducerData(
                "discussion-command",
                id,
                StartDiscussion(id, blogId, title, userId, userName)))
          case event @ BlogModified(blogId, userId, userName, title, content) =>
            producer.offer(
              ProducerData("blog-event", blogId, event))
          case event @ DiscussionStarted(id, userId, userName, blogId, title) =>
            producer.offer(
              ProducerData("blog-event", blogId, event))
          case message: WrongMessage =>
            producer.offer(ProducerData("error", "FATAL", message))
          case message =>
        } recover {
          case e: Exception =>
            producer.offer(
              ProducerData("error", "FATAL", WrongMessage(e.toString)))
        }
      }
		}
    consumer._2.onComplete {
      case Success(done) =>
      case Failure(throwable) => println(throwable)
    }
	}

	implicit val mq = new Kafka(ProductionKafkaConfig)
	implicit val system = ActorSystem("BlogCommandApp")
	import common.TypeHintContext._

	start
	scala.io.StdIn.readLine()
	system.terminate
}

