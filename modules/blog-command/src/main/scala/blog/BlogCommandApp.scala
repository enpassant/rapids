package blog

import common._
import config.ProductionKafkaConfig

import org.apache.pekko.actor._
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
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

    val (statActor, producerStat) = statActorAndProducer(mq, "blog-command")

    val service = system.actorOf(
      CommandService.props(BlogSerializer.fromString, "blog", BlogActor.props),
      "blog-service")

    val consumer = mq.createConsumer("blog-command", "blog-command") { msg =>
      Performance.statF(statActor) {
        implicit val timeout = Timeout(3000.milliseconds)
        val result = service ? common.ConsumerData(msg.key, msg.value)
        result collect {
          case event @ BlogCreated(blogId, userId, userName, title, content, _) =>
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

  implicit val mq: Kafka = new Kafka(ProductionKafkaConfig)
  implicit val system: ActorSystem = ActorSystem("BlogCommandApp")

  start
}

