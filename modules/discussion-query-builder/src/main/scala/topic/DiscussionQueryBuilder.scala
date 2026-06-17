package discussion

import common._
import blog._
import config._
import org.apache.pekko.actor._
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

object DiscussionQueryBuilder extends App with Microservice {
  def start(discussionStore: DiscussionStore)
    (implicit mq: MQProtocol, system: ActorSystem) =
  {
    implicit val executionContext = system.dispatcher

    val (statActor, producer) = statActorAndProducer(mq, "disc-query-builder")

    val consumer = mq.createConsumer("discussion-query", "discussion-event")
    { msg =>
      Performance.statF(statActor) {
        val jsonTry = Try(BlogSerializer.fromString(msg.value))
        val result = Future { jsonTry match {
          case Success(json) =>
            json match {
              case discussionStarted: DiscussionStarted =>
                Try { discussionStore.insert(discussionStarted) }
              case commentAdded @ CommentAdded(_, _, userId, _, _, _) =>
                Try {
                  discussionStore.addComment(commentAdded)
                  producer.offer(ProducerData(
                    "client-commands", userId, """{"value":"CommentAdded"}"""))
                }
              case commentReplied @ CommentReplied(_, _, userId, _, _, _, _) =>
                Try {
                  discussionStore.replayComment(commentReplied)
                  producer.offer(ProducerData(
                    "client-commands", userId, """{"value":"CommentReplied"}"""))
                }
              case _ => 1
            }
          case Failure(e) =>
            println("Wrong json format: " + e)
            e
        } }
        result
      }
    }
    consumer._2.onComplete {
      case Success(done) =>
      case Failure(throwable) => println(throwable)
    }
  }

  val discussionStore = new DiscussionStoreDB(
    ProductionDiscussionQueryBuilderConfig)

  implicit val mq: Kafka = new Kafka(ProductionKafkaConfig)
  implicit val system: ActorSystem = ActorSystem("DiscussionQueryBuilder")

  start(discussionStore)
}
