package discussion

import common._
import blog._
import com.mongodb.casbah.Imports._
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.{Try, Success, Failure}

object DiscussionQueryBuilder extends App {
	def start(implicit system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load
    val uri = config.getString("discussion.query.builder.mongodb.uri")
    val mongoClient = MongoClient(MongoClientURI(uri))
    val collDiscussion = mongoClient.getDB("blog")("discussion")

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"discussion-query",
			"discussion-event")
		{ msg =>
			val consumerRecord = msg.record
			implicit val timeout = Timeout(1000.milliseconds)
			val key = new String(consumerRecord.key)
			val jsonTry = Try(new BlogSerializer().fromString(consumerRecord.value))
			val result = Future { jsonTry match {
				case Success(json) =>
          json match {
            case DiscussionStarted(id, blogId, title) =>
              Try {
                collDiscussion.insert(
                  MongoDBObject(
                    "_id" -> id,
                    "blogId" -> blogId,
                    "title" -> title,
                    "comments" -> List()
                ))
              }
            case CommentAdded(id, title, content, index) =>
              Try {
                collDiscussion.update(
                  MongoDBObject("_id" -> key),
                  $push("comments" -> MongoDBObject(
                    "commentId" -> id,
                    "title" -> title,
                    "content" -> content,
                    "comments" -> List()
                )))
              }
            case CommentReplied(id, parentId, title, content, path) =>
              Try {
                val pos = path.tail.foldLeft("comments") {
                  (p, i) => s"comments.$i.$p"
                }
                collDiscussion.update(
                  MongoDBObject("_id" -> key),
                  $push(pos -> MongoDBObject(
                    "commentId" -> id,
                    "title" -> title,
                    "content" -> content,
                    "comments" -> List()
                )))
              }
            case _ => 1
          }
				case Failure(e) =>
          println("Wrong json format: " + e)
					e
			} }
      result map { _ => msg.committableOffset }
		}
    consumer.onComplete {
      case Success(done) =>
      case Failure(throwable) => println(throwable)
    }
	}

	val system = ActorSystem("DiscussionQueryBuilder")
	import common.TypeHintContext._

	start(system)
	scala.io.StdIn.readLine()
	system.terminate
}
