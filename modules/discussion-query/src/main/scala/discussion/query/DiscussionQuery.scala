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
  extends BaseFormats with Microservice
{
	def start(implicit
    mq: MQProtocol,
    system: ActorSystem,
    materializer: ActorMaterializer) =
  {
		implicit val executionContext = system.dispatcher

    val collDiscussion = config.mongoClient.getDB("blog")("discussion")

    val ickenham = new Ickenham(new Json4sAdapter)

    val comment = ickenham.compile("comment")
    val commentNew = ickenham.compile("comment-new")
    val commentReply = ickenham.compile("comment-reply")
    val discussion = ickenham.compile("discussion")

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
}

object DiscussionQuery extends App {
	implicit val mq = new Kafka(ProductionKafkaConfig)
	implicit val system = ActorSystem("DiscussionQuery")
	implicit val materializer = ActorMaterializer()

	val route = new DiscussionQuery(ProductionDiscussionQueryConfig).start
	val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8083)
}

