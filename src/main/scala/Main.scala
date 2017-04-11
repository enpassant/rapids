import common._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream._
import scala.util.{Failure, Success}

object Main extends App {
	implicit val system = ActorSystem("Main")
	implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  println("Program has started")

	val routeWeb = WebApp.start
	val routeBlogQuery = blog.query.BlogQuery.start
	val routeDiscussionQuery = discussion.query.DiscussionQuery.start
	val routeMonitor = monitor.Monitor.start
	val route = routeBlogQuery ~ routeDiscussionQuery ~ routeMonitor ~ routeWeb
	blog.BlogCommandApp.start(system)
	discussion.DiscussionCommandApp.start(system)
	blog.BlogQueryBuilder.start(system)
	discussion.DiscussionQueryBuilder.start(system)

	val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

  bindingFuture.onComplete {
    case Failure(ex) =>
      ex.printStackTrace
      system.terminate
      println("Failed to bind!")
    case Success(t) =>
      println(s"Successed to bind to $t")
  }
}
