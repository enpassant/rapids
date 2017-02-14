package discussion

import common._
import topic._
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
	val system = ActorSystem("DiscussionQueryBuilder")
	import common.TypeHintContext._

	start(system)
	scala.io.StdIn.readLine()
	system.terminate

	def start(implicit system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load
    val uri = config.getString("discussion.query.builder.mongodb.uri")
    val mongoClient = MongoClient(MongoClientURI(uri))
    val collection = mongoClient.getDB("topic")("comment")

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"discussion-query",
			"discussion-event")
		{ msg =>
			val consumerRecord = msg.record
			implicit val timeout = Timeout(1000.milliseconds)
			val key = new String(consumerRecord.key)
			val jsonTry = Try(new TopicSerializer().fromString(consumerRecord.value))
			val result = Future { jsonTry match {
				case Success(json) =>
          json match {
            case CommentAdded(id, title, content) =>
              Try {
                collection.insert(
                  MongoDBObject(
                    "_id" -> id,
                    "discussionId" -> key,
                    "title" -> title,
                    "content" -> content,
                    "path" -> ("," + id + ",")))
              }
            case CommentReplied(id, parentId, title, content) =>
              Try {
                val parentComment = collection.findOne(
                  MongoDBObject("_id"->parentId))
                parentComment map { parent =>
                  val parentPath = parent.getAsOrElse("path", ",")
                  collection.insert(
                    MongoDBObject(
                      "_id" -> id,
                      "discussionId" -> key,
                      "title" -> title,
                      "content" -> content,
                      "path" -> (parentPath + id + ",")))
                }
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
}

