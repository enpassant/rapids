package discussion

import common._
import blog._
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import config._
import org.apache.pekko.actor._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object DiscussionQueryBuilder extends App with Microservice {
  def start(discussionStore: DiscussionStore)
    (implicit mq: MQProtocol, system: ActorSystem) =
  {
    implicit val executionContext = system.dispatcher
    val options = new MutableDataSet()
    val parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()

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
              case commentAdded @ CommentAdded(_, _, userId, _, content, _) =>
                Try {
                  val document = parser.parse(content)
                  val htmlContent = renderer.render(document)
                  discussionStore.addComment(commentAdded, htmlContent)
                  producer.offer(ProducerData(
                    "client-commands", userId, """{"value":"CommentAdded"}"""))
                }
              case commentReplied @ CommentReplied(_, _, userId, _, _, content, _) =>
                Try {
                  val document = parser.parse(content)
                  val htmlContent = renderer.render(document)
                  discussionStore.replayComment(commentReplied, htmlContent)
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
