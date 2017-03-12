import common._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream._

object Main extends App {
	implicit val system = ActorSystem("Main")
	implicit val materializer = ActorMaterializer()

	val routeWeb = WebApp.start
	val routeBlogQuery = blog.query.BlogQuery.start
	val routeDiscussionQuery = discussion.query.DiscussionQuery.start
	val routeMonitor = monitor.Monitor.start
	val route = routeWeb ~ routeBlogQuery ~ routeDiscussionQuery ~ routeMonitor
	blog.BlogCommandApp.start(system)
	discussion.DiscussionCommandApp.start(system)
	blog.BlogQueryBuilder.start(system)
	discussion.DiscussionQueryBuilder.start(system)

	val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

	scala.io.StdIn.readLine()
	system.terminate
}
