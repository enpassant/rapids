package discussion.query

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

class DiscussionQuery(config: DiscussionQueryConfig)
  extends App with BaseFormats with Microservice
{
	def start(implicit
    mq: MQProtocol,
    system: ActorSystem,
    materializer: ActorMaterializer) =
  {
		implicit val executionContext = system.dispatcher

    val collDiscussion = config.mongoClient.getDB("blog")("discussion")

    val ickenham = new Ickenham(new Json4sAdapter)
    val assemble = (template: String, templates: Map[String, Vector[Tag]]) =>
      ickenham.assemble(template, templates)

    val templates = ickenham.compiles(
      "comment",
      "comment-new",
      "comment-reply",
      "discussion")

    val comment = assemble("comment", templates)
    val commentNew = assemble("comment-new", templates)
    val commentReply = assemble("comment-reply", templates)
    val discussion = assemble("discussion", templates)

		val route =
      pathPrefix("discussion") {
        pathPrefix(Segment) { id =>
          path("new") {
            completePage(commentNew, "comment-new") {
              Some(JObject(
                JField("_id", id),
                JField("uuid", CommonUtil.uuid)
              ))
            }
          } ~
          pathPrefix("comment") {
            pathPrefix(Segment) { commentId =>
              path("new") {
                completePage(commentReply, "comment-reply") {
                  Some(JObject(
                    JField("_id", id),
                    JField("commentId", commentId),
                    JField("uuid", CommonUtil.uuid)
                  ))
                }
              }
            }
          } ~
          pathEnd {
            completePage(discussion, "discussion") {
              collDiscussion.findOne(MongoDBObject("_id" -> id))
                .map(o => serialize(o))
            }
          }
        }
      }

    stat(statActorAndProducer(mq, "discussion-query")._1)(route)
	}

	implicit val mq = new Kafka(ProductionKafkaConfig)
	implicit val system = ActorSystem("DiscussionQuery")
	implicit val materializer = ActorMaterializer()
	val bindingFuture = Http().bindAndHandle(start, "0.0.0.0", 8083)

	scala.io.StdIn.readLine()
	system.terminate
}

