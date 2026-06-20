package discussion.query

import common._
import common.web.Directives._
import config._
import org.apache.pekko.actor._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream._
import com.github.enpassant.ickenham._
import com.github.enpassant.ickenham.adapter.Json4sAdapter
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._

import scala.concurrent.Await
import scala.concurrent.duration._
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods
import org.json4s.mongo.JObjectParser._

object DiscussionQuery
  extends App with BaseFormats with Microservice
{
  def start(config: DiscussionQueryConfig)
    (implicit
      mq: MQProtocol,
      system: ActorSystem,
      materializer: Materializer) =
  {
    val collDiscussion = config.mongoClient.getDatabase("blog")
      .getCollection("discussion")

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
              Await.result(
                collDiscussion.find(equal("_id", id)).first().toFuture(),
                10.seconds
              ) match {
                case null => None
                case o => Some(JsonMethods.parse(o.toJson))
                  .map(o => transformDateTimeToStr(o))
              }
            }
          }
        }
      }

    stat(statActorAndProducer(mq, "discussion-query")._1)(route)
  }

  implicit val mq: Kafka = new Kafka(ProductionKafkaConfig)
  implicit val system: ActorSystem = ActorSystem("DiscussionQuery")

  val route = DiscussionQuery.start(ProductionDiscussionQueryConfig)
  val bindingFuture = Http().newServerAt("0.0.0.0", 8083).bindFlow(route)
}

