package blog.query

import common._
import common.web.Directives._
import config._

import org.apache.pekko.actor._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream._
import com.github.enpassant.ickenham._
import com.github.enpassant.ickenham.adapter.Json4sAdapter
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import scala.concurrent.Await
import scala.concurrent.duration._
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.mongo.JObjectParser._

object BlogQuery extends App with BaseFormats with Microservice {
  def start(config: BlogQueryConfig)
    (implicit
      mq: MQProtocol,
      system: ActorSystem,
      materializer: ActorMaterializer) =
  {
    implicit val executionContext = system.dispatcher

    val collBlog = config.mongoClient.getDatabase("blog")
      .getCollection("blog")
    val title = config.title

    val ickenham = new Ickenham(new Json4sAdapter)

    val blogs = ickenham.compile("blogs")
    val blog = ickenham.compile("blog")
    val blogNew = ickenham.compile("blog-new")
    val blogEdit = ickenham.compile("blog-edit")

    val (statActor, producer) = statActorAndProducer(mq, "blog-query")

    val link = CommonSerializer.toString(FunctionLink(0, "/blog", "Blogs"))
    producer.offer(ProducerData("web-app", "blog-query", link))

    val route =
      pathPrefix("blog") {
        pathEnd {
          completePage(blogs, "blogs") {
            val blogs = Await.result(
              collBlog.find()
                .projection(exclude("content"))
                .toFuture(),
              10.seconds
            ).map(o => serialize(o)).toList
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
              Await.result(
                collBlog.find(equal("_id", id)).first().toFuture(),
                10.seconds
              ) match {
                case null => None
                case o => Some(serialize(o))
              }
            }
          } ~
          path("edit") {
            completePage(blogEdit, "blog-edit") {
              Await.result(
                collBlog.find(equal("_id", id)).first().toFuture(),
                10.seconds
              ) match {
                case null => None
                case o => Some(serialize(o))
              }
            }
          }
        }
      }

    stat(statActor)(route)
  }

  implicit val mq = new Kafka(ProductionKafkaConfig)
  implicit val system = ActorSystem("BlogQuery")
  implicit val materializer = ActorMaterializer()

  val route = BlogQuery.start(ProductionBlogQueryConfig)
  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8082)
}

