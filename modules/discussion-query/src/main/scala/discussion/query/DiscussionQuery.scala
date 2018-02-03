package discussion.query

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

    val handlebars = new Handlebars().registerHelpers(Json4sHelpers)
    handlebars.setInfiniteLoops(true)
    def ctx(obj: Object) =
      Context.newBuilder(obj).resolver(Json4sResolver).build
    val render = (template: Template) => (obj: Object) => template(ctx(obj))

    val comment = handlebars.compile("comment")
    val commentNew = handlebars.compile("comment-new")
    val commentReply = handlebars.compile("comment-reply")
    val discussion = handlebars.compile("discussion")

		val route =
      pathPrefix("discussion") {
        pathPrefix(Segment) { id =>
          path("new") {
            completePage(render(commentNew), "comment-new") {
              Some(JObject(
                JField("_id", id),
                JField("uuid", CommonUtil.uuid)
              ))
            }
          } ~
          pathPrefix("comment") {
            pathPrefix(Segment) { commentId =>
              path("new") {
                completePage(render(commentReply), "comment-reply") {
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
            completePage(render(discussion), "discussion") {
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

