package blogQuery

import common._

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

object BlogQuery extends App with BaseFormats {
	implicit val system = ActorSystem("BlogQuery")
	implicit val materializer = ActorMaterializer()
	val route = start
	val bindingFuture = Http().bindAndHandle(route, "localhost", 8083)

	scala.io.StdIn.readLine()
	system.terminate

	def start(implicit system: ActorSystem, materializer: ActorMaterializer) = {
		implicit val executionContext = system.dispatcher

    val config = ConfigFactory.load
    val uri = config.getString("blog.query.mongodb.uri")
    val mongoClient = MongoClient(MongoClientURI(uri))
    val collBlog = mongoClient.getDB("blog")("blog")
    val handlebars = new Handlebars().registerHelpers(Json4sHelpers)
    def ctx(obj: Object) =
      Context.newBuilder(obj).resolver(Json4sResolver).build

    val strBlogs = """
      |{{#each blogs}}
      |<h1>
      |  <a href="/query/blog/{{_id}}">{{title}}</a>
      |</h1>
      |{{/each}}""".stripMargin
    val templateBlogs = handlebars.compileInline(strBlogs)

    val strBlog = """
      |<h1>
      |  {{title}}
      |</h1>
      |{{content}}""".stripMargin
    val templateBlog = handlebars.compileInline(strBlog)

		val route =
			pathPrefix("query") {
				pathPrefix("blog") {
          pathEnd {
						get {
              val blogs = collBlog.find(
                MongoDBObject(),
                MongoDBObject("content" -> 0)
              )
                .map(o => serialize(o)).toList
              val blogsObj = JObject(JField("blogs", blogs))
              complete(
                HttpEntity(
                  ContentTypes.`text/html(UTF-8)`,
                  templateBlogs(ctx(blogsObj)))
              ) ~
              complete(blogs) ~
              complete( HttpEntity( `text/html+xml`, strBlogs))
						}
          } ~
					path(Segment) { id =>
						get {
              rejectEmptyResponse {
                val blogOption = collBlog.findOne(
                  MongoDBObject("_id" -> id)
                )
                  .map(o => serialize(o))
                complete(
                  blogOption map { blog =>
                    HttpEntity(
                      ContentTypes.`text/html(UTF-8)`,
                      templateBlog(ctx(blog)))
                  }
                ) ~
                complete(blogOption) ~
                complete(HttpEntity(`text/html+xml`, strBlog))
              }
						}
					}
				}
			} ~
			path("system") {
				post {
					entity(as[String]) {
						case "shutdown" =>
							system.terminate
							complete(
								HttpEntity(
									ContentTypes.`text/plain(UTF-8)`,
									"System shut down"))
						}
				}
			} ~
			path("") {
				getFromResource(s"public/html/index.html")
			} ~
			path("""([^/]+\.html).*""".r) { path =>
				getFromResource(s"public/html/$path")
			} ~
			path(Remaining) { path =>
				getFromResource(s"public/$path")
			}

		route
	}
}

