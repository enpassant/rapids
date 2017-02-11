package topic

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

object TopicQueryBuilder extends App {
	val system = ActorSystem("TopicQueryBuilder")
	import common.TypeHintContext._

	start(system)
	scala.io.StdIn.readLine()
	system.terminate

	def start(implicit system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load
    val uri = config.getString("topic.query.builder.mongodb.uri")
    val mongoClient = MongoClient(MongoClientURI(uri))
    val collection = mongoClient.getDB("topic")("topic")

		val consumer = Kafka.createConsumer(
			"localhost:9092",
			"topic-query",
			"topic")
		{ msg =>
			val consumerRecord = msg.record
			implicit val timeout = Timeout(1000.milliseconds)
			val key = new String(consumerRecord.key)
			val jsonTry = Try(new TopicSerializer().fromString(consumerRecord.value))
      println(jsonTry)
			val result = Future { jsonTry match {
				case Success(json) =>
          println(json)
          json match {
            case TopicCreated(id, title, content) =>
              collection.insert(
                MongoDBObject("_id"->id, "title"->title, "content"->content))
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

