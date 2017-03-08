package blog

import common._
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

object BlogQueryBuilder extends App {
	def start(implicit system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load
    val uri = config.getString("blog.query.builder.mongodb.uri")
    val mongoClient = MongoClient(MongoClientURI(uri))
    val collection = mongoClient.getDB("blog")("blog")

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"blog-query",
			"blog-event")
		{ msg =>
			val consumerRecord = msg.record
			implicit val timeout = Timeout(1000.milliseconds)
			val key = new String(consumerRecord.key)
			val jsonTry = Try(new BlogSerializer().fromString(consumerRecord.value))
			val result = Future { jsonTry match {
				case Success(json) =>
          json match {
            case BlogCreated(id, userId, userName, title, content) =>
              collection.insert(
                MongoDBObject(
                  "_id" -> id,
                  "userId" -> userId,
                  "userName" -> userName,
                  "title" -> title,
                  "content" -> content,
                  "discussions" -> Seq()))
            case DiscussionStarted(id, blogId, title) =>
              collection.update(
                MongoDBObject("_id" -> blogId),
                $push("discussions" ->
                  MongoDBObject("id" -> id, "title" -> title)))
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

	val system = ActorSystem("BlogQueryBuilder")
	import common.TypeHintContext._

	start(system)
	scala.io.StdIn.readLine()
	system.terminate
}
