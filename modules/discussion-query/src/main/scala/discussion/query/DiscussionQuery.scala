package discussion.query

import common._
import common.web.Directives._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import com.github.jknack.handlebars.{ Context, Handlebars }
import com.mongodb.casbah.Imports._
import com.typesafe.config.ConfigFactory
import fixiegrips.{ Json4sHelpers, Json4sResolver }
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.mongo.JObjectParser._

object DiscussionQuery extends App with BaseFormats {
	def start(implicit system: ActorSystem, materializer: ActorMaterializer) = {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load
    val uri = config.getString("discussion.query.mongodb.uri")
    val mongoClient = MongoClient(MongoClientURI(uri))
    val collDiscussion = mongoClient.getDB("blog")("discussion")

    val handlebars = new Handlebars().registerHelpers(Json4sHelpers)
    handlebars.setInfiniteLoops(true)
    def ctx(obj: Object) =
      Context.newBuilder(obj).resolver(Json4sResolver).build

    val comment = handlebars.compile("comment")
    val discussion = handlebars.compile("discussion")
    val renderDiscussion = (obj: Object) => discussion(ctx(obj))

		val route =
			pathPrefix("query") {
				pathPrefix("discussion") {
					path(Segment) { id =>
            completePage(renderDiscussion, "discussion") {
              collDiscussion.findOne(MongoDBObject("_id" -> id))
                .map(o => serialize(o))
            }
					}
				}
			}

		route
	}

	implicit val system = ActorSystem("DiscussionQuery")
	implicit val materializer = ActorMaterializer()
	val route = start
	val bindingFuture = Http().bindAndHandle(route, "localhost", 8083)

	scala.io.StdIn.readLine()
	system.terminate
}

