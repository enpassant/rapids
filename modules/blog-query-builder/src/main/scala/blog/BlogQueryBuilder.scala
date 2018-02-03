package blog

import common._
import config._

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.mongodb.casbah.Imports._
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.options.MutableDataSet;
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.{Try, Success, Failure}

object BlogQueryBuilder extends App with Microservice {
	def start(blogStore: BlogStore)
    (implicit mq: MQProtocol, system: ActorSystem) =
  {
		implicit val executionContext = system.dispatcher

    val options = new MutableDataSet()
    val parser = Parser.builder(options).build()
    val renderer = HtmlRenderer.builder(options).build()

    val (statActor, producer) = statActorAndProducer(mq, "blog-query-builder")

		val consumer = mq.createConsumer(
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
                blogStore.insert(
                  id,
                  userId,
                  userName,
                  title,
                  content,
                  htmlContent)
                producer.offer(ProducerData(
                  "client-commands", userId, """{"value":"BlogCreated"}"""))
              case BlogModified(id, userId, userName, title, content) =>
                if (blogStore.existsBlog(id)) {
                  val document = parser.parse(content)
                  val htmlContent = renderer.render(document)
                  blogStore.update(id, title, content, htmlContent)
                }
                producer.offer(ProducerData(
                  "client-commands", userId, """{"value":"BlogModified"}"""))
              case DiscussionStarted(id, userId, userName, blogId, title) =>
                blogStore.addDiscussion(blogId, id, userId, userName, title)
                producer.offer(ProducerData(
                  "client-commands", userId, """{"value":"DiscussionStarted"}"""))
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

  val blogStore = new BlogStoreDB(ProductionBlogQueryBuilderConfig)

	implicit val mq = new Kafka(ProductionKafkaConfig)
	implicit val system = ActorSystem("DiscussionQueryBuilder")

	start(blogStore)
	scala.io.StdIn.readLine()
	system.terminate
}
