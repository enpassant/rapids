import common._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream._

object Main extends App {
	implicit val system = ActorSystem("Main")
	implicit val materializer = ActorMaterializer()

	val routeWeb = WebApp.start
	val routeWebSocket = WebSocketApp.start
	val route = routeWeb ~ routeWebSocket
	topic.TopicCommandApp.start(system)
	discussion.DiscussionCommandApp.start(system)
	topic.TopicQueryBuilder.start(system)
	discussion.DiscussionQueryBuilder.start(system)

	val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

	scala.io.StdIn.readLine()
	system.terminate
}
