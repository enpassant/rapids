package blog

import common._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.mongodb.casbah.Imports._
import com.typesafe.config.ConfigFactory
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.options.MutableDataSet;
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.{Try, Success, Failure}

object BlogQueryBuilder extends App with Microservice {
	def start(implicit mq: MQProtocol, system: ActorSystem) = {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load
    val uri = config.getString("blog.query.builder.mongodb.uri")
    val mongoClient = MongoClient(MongoClientURI(uri))
    val collection = mongoClient.getDB("blog")("blog")

    val options = new MutableDataSet()
    val parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()

		val producer = mq.createProducer[ProducerData[String]](kafkaServer)
    {
			case msg @ ProducerData(topic, id, value) => msg
		}

    val statActor = system.actorOf(
      Performance.props("blog-query-builder", producer))

		val consumer = mq.createConsumer(
      kafkaServer,
			"blog-query",
			"blog-event")
		{ msg =>
      Performance.statF(statActor) {
        implicit val timeout = Timeout(1000.milliseconds)
        val jsonTry = Try(BlogSerializer.fromString(msg.value))
        val result = Future { jsonTry match {
          case Success(json) =>
            json match {
              case BlogCreated(id, userId, userName, title, content) =>
                val document = parser.parse(content)
                val htmlContent = renderer.render(document)
                collection.insert(
                  MongoDBObject(
                    "_id" -> id,
                    "userId" -> userId,
                    "userName" -> userName,
                    "title" -> title,
                    "content" -> content,
                    "htmlContent" -> htmlContent,
                    "discussions" -> Seq()))
              case DiscussionStarted(id, userId, userName, blogId, title) =>
                collection.update(
                  MongoDBObject("_id" -> blogId),
                  $push("discussions" ->
                    MongoDBObject(
                      "id" -> id,
                      "userId" -> userId,
                      "userName" -> userName,
                      "title" -> title)))
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

  implicit val mq = Kafka
	implicit val system = ActorSystem("DiscussionQueryBuilder")
	import common.TypeHintContext._

	start
	scala.io.StdIn.readLine()
	system.terminate
}
