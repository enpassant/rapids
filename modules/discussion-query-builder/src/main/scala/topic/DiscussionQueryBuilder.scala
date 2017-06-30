package discussion

import common._
import blog._
import config._

import com.mongodb.casbah.Imports._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.{Try, Success, Failure}

class DiscussionQueryBuilder(config: DiscussionQueryBuilderConfig)
  extends App with Microservice
{
	def start(implicit mq: MQProtocol, system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

    val collDiscussion = config.mongoClient.getDB("blog")("discussion")

		val producer = mq.createProducer[ProducerData[String]]()
    {
			case msg @ ProducerData(topic, id, value) => msg
		}

    val statActor = system.actorOf(
      Performance.props("disc-query-builder", producer))

		val consumer = mq.createConsumer(
			"discussion-query",
			"discussion-event")
		{ msg =>
      Performance.statF(statActor) {
        implicit val timeout = Timeout(1000.milliseconds)
        val key = msg.key
        val jsonTry = Try(BlogSerializer.fromString(msg.value))
        val result = Future { jsonTry match {
          case Success(json) =>
            json match {
              case DiscussionStarted(id, userId, userName, blogId, title) =>
                Try {
                  collDiscussion.insert(
                    MongoDBObject(
                      "_id" -> id,
                      "userId" -> userId,
                      "userName" -> userName,
                      "blogId" -> blogId,
                      "title" -> title,
                      "comments" -> List()
                  ))
                }
              case CommentAdded(id, userId, userName, content, index) =>
                Try {
                  collDiscussion.update(
                    MongoDBObject("_id" -> key),
                    $push("comments" -> MongoDBObject(
                      "commentId" -> id,
                      "userId" -> userId,
                      "userName" -> userName,
                      "content" -> content,
                      "comments" -> List()
                  )))
                  producer.offer(ProducerData(
                    "client-commands", userId, """{"value":"CommentAdded"}"""))
                }
              case msg @
                CommentReplied(id, userId, userName, parentId, content, path) =>
                Try {
                  val pos = path.tail.foldLeft("comments") {
                    (p, i) => s"comments.$i.$p"
                  }
                  collDiscussion.update(
                    MongoDBObject("_id" -> key),
                    $push(pos -> MongoDBObject(
                      "commentId" -> id,
                      "userId" -> userId,
                      "userName" -> userName,
                      "content" -> content,
                      "comments" -> List()
                  )))
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

	implicit val mq = new Kafka(ProductionKafkaConfig)
	implicit val system = ActorSystem("DiscussionQueryBuilder")
	import common.TypeHintContext._

	start
	scala.io.StdIn.readLine()
	system.terminate
}
