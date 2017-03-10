package blog.query

import common._
import common.web.Directives._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import com.github.jknack.handlebars.{ Context, Handlebars, Template }
import com.mongodb.casbah.Imports._
import com.typesafe.config.ConfigFactory
import fixiegrips.{ Json4sHelpers, Json4sResolver }
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.mongo.JObjectParser._

object BlogQuery extends App with BaseFormats {
	def start(implicit system: ActorSystem, materializer: ActorMaterializer) = {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load
    val uri = config.getString("blog.query.mongodb.uri")
    val mongoClient = MongoClient(MongoClientURI(uri))
    val collBlog = mongoClient.getDB("blog")("blog")

    val handlebars = new Handlebars().registerHelpers(Json4sHelpers)
    def ctx(obj: Object) =
      Context.newBuilder(obj).resolver(Json4sResolver).build
    val render = (template: Template) => (obj: Object) => template(ctx(obj))

    val blogs = handlebars.compile("blogs")
    val blog = handlebars.compile("blog")
    val blogNew = handlebars.compile("blog-new")

		val producer = Kafka.createProducer[ProducerData[String]]("localhost:9092")
    {
			case ProducerData(topic, id, value) =>
				new ProducerRecord[Array[Byte], String](
					topic, id.getBytes(), value)
		}

    val link = CommonSerializer.toString(FunctionLink(0, "/blog", "Blogs"))
    producer.offer(ProducerData("web-app", "blog-query", link))

		val route =
      pathPrefix("blog") {
        pathEnd {
          completePage(render(blogs), "blogs") {
            val blogs = collBlog.find(
              MongoDBObject(),
              MongoDBObject("content" -> 0)
            )
              .map(o => serialize(o)).toList
            Some(JObject(JField("blogs", blogs)))
          }
        } ~
        path("new") {
          completePage(render(blogNew), "blog-new") {
            Some(JObject(JField("uuid", CommonUtil.uuid)))
          }
        } ~
        path(Segment) { id =>
          completePage(render(blog), "blog") {
            collBlog.findOne(MongoDBObject("_id" -> id))
              .map(o => serialize(o))
          }
        }
      }

		route
	}

	implicit val system = ActorSystem("BlogQuery")
	implicit val materializer = ActorMaterializer()
	val route = start
	val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8083)

	scala.io.StdIn.readLine()
	system.terminate
}

