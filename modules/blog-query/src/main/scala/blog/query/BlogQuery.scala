package blog.query

import common._
import common.web.Directives._
import config._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import com.github.enpassant.ickenham._
import com.github.enpassant.ickenham.adapter.Json4sAdapter
import com.mongodb.casbah.Imports._
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

    val ickenham = new Ickenham(new Json4sAdapter)
    val assemble = (template: String, templates: Map[String, Vector[Tag]]) =>
      ickenham.assemble(template, templates)

    val templates = ickenham.compiles("blog", "blogs", "blog-new", "blog-edit")

    val blogs = assemble("blogs", templates)
    val blog = assemble("blog", templates)
    val blogNew = assemble("blog-new", templates)
    val blogEdit = assemble("blog-edit", templates)

    val (statActor, producer) = statActorAndProducer(mq, "blog-query")

    val link = CommonSerializer.toString(FunctionLink(0, "/blog", "Blogs"))
    producer.offer(ProducerData("web-app", "blog-query", link))

    val route =
      pathPrefix("blog") {
        pathEnd {
          completePage(blogs, "blogs") {
            val blogs = collBlog.find(
              MongoDBObject(),
              MongoDBObject("content" -> 0)
            )
              .map(o => serialize(o)).toList
            Some(JObject(JField("blogs", blogs), JField("title", title)))
          }
        } ~
        path("new") {
          completePage(blogNew, "blog-new") {
            Some(JObject(JField("uuid", CommonUtil.uuid)))
          }
        } ~
        pathPrefix(Segment) { id =>
          pathEnd {
            completePage(blog, "blog") {
              collBlog.findOne(MongoDBObject("_id" -> id))
                .map(o => serialize(o))
            }
          } ~
          path("edit") {
            completePage(blogEdit, "blog-edit") {
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
  val bindingFuture = Http().bindAndHandle(start, "0.0.0.0", 8083)
}

