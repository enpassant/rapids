package blog.query

import common._
import common.web.Directives._
import config._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import com.github.jknack.handlebars.{ Context, Handlebars, Template }
import com.mongodb.casbah.Imports._
import fixiegrips.{ Json4sHelpers, Json4sResolver }
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.mongo.JObjectParser._

class BlogQuery(config: BlogQueryConfig)
  extends App with BaseFormats with Microservice
{
  def start(implicit
    mq: MQProtocol,
    system: ActorSystem,
    materializer: ActorMaterializer) =
  {
    implicit val executionContext = system.dispatcher

    val collBlog = config.mongoClient.getDB("blog")("blog")
    val title = config.title

    val handlebars = new Handlebars().registerHelpers(Json4sHelpers)
    def ctx(obj: Object) =
      Context.newBuilder(obj).resolver(Json4sResolver).build
    val render = (template: Template) => (obj: Object) => template(ctx(obj))

    val blogs = handlebars.compile("blogs")
    val blog = handlebars.compile("blog")
    val blogNew = handlebars.compile("blog-new")
    val blogEdit = handlebars.compile("blog-edit")

    val producer = mq.createProducer[ProducerData[String]]()
    {
      case msg @ ProducerData(topic, id, value) => msg
    }

    val link = CommonSerializer.toString(FunctionLink(0, "/blog", "Blogs"))
    producer.offer(ProducerData("web-app", "blog-query", link))

    val statActor = system.actorOf(Performance.props("blog-query", producer))

    val route =
      pathPrefix("blog") {
        pathEnd {
          completePage(render(blogs), "blogs") {
            val blogs = collBlog.find(
              MongoDBObject(),
              MongoDBObject("content" -> 0)
            )
              .map(o => serialize(o)).toList
            Some(JObject(JField("blogs", blogs), JField("title", title)))
          }
        } ~
        path("new") {
          completePage(render(blogNew), "blog-new") {
            Some(JObject(JField("uuid", CommonUtil.uuid)))
          }
        } ~
        pathPrefix(Segment) { id =>
          pathEnd {
            completePage(render(blog), "blog") {
              collBlog.findOne(MongoDBObject("_id" -> id))
                .map(o => serialize(o))
            }
          } ~
          path("edit") {
            completePage(render(blogEdit), "blog-edit") {
              collBlog.findOne(MongoDBObject("_id" -> id))
                .map(o => serialize(o))
            }
          }
        }
      }

    stat(statActor)(route)
  }

	implicit val mq = new Kafka(ProductionKafkaConfig)
  implicit val system = ActorSystem("BlogQuery")
  implicit val materializer = ActorMaterializer()
  val route = start
  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8083)
}

