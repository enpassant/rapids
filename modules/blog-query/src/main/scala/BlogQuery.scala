package blogQuery

import common._

import akka.actor._
import akka.kafka._
import akka.kafka.scaladsl._
import akka.persistence._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{UpgradeToWebSocket, TextMessage}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling._
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._

object BlogQuery extends App {
	implicit val system = ActorSystem("BlogQuery")
	implicit val materializer = ActorMaterializer()
	val route = start
	val bindingFuture = Http().bindAndHandle(route, "localhost", 8083)

	scala.io.StdIn.readLine()
	system.terminate

	def start(implicit system: ActorSystem, materializer: ActorMaterializer) = {
		implicit val executionContext = system.dispatcher

		val route =
			pathPrefix("query") {
				pathPrefix("blog") {
          pathEnd {
						get {
              complete(
                HttpEntity(
                  ContentTypes.`text/html(UTF-8)`,
                  s"<h1>Blog: query list</h1>"))
						}
          } ~
					path(Segment) { id =>
						get {
              complete(
                HttpEntity(
                  ContentTypes.`text/html(UTF-8)`,
                  s"<h1>Blog: query $id id</h1>"))
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

