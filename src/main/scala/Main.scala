import common._

import config._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream._
import scala.util.{Failure, Success}

object Main extends App {
  val isTest = args.length > 0 && args(0) == "-t"
	implicit val system = ActorSystem("Main")
	implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val mq =
    if (isTest) new MQTest(system) else new Kafka(ProductionKafkaConfig)

  println("Program has started")

	val routeWeb = WebApp.start
	val routeBlogQuery =
    new blog.query.BlogQuery(ProductionBlogQueryConfig).start
	val routeDiscussionQuery =
    new discussion.query.DiscussionQuery(ProductionDiscussionQueryConfig).start
	val routeMonitor = monitor.Monitor.start
	val route = routeBlogQuery ~ routeDiscussionQuery ~ routeMonitor ~ routeWeb
	blog.BlogCommandApp.start
	discussion.DiscussionCommandApp.start
	new blog.BlogQueryBuilder(ProductionBlogQueryBuilderConfig).start
	new discussion.DiscussionQueryBuilder(ProductionDiscussionQueryBuilderConfig)
    .start

	val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

  bindingFuture.onComplete {
    case Failure(ex) =>
      ex.printStackTrace
      system.terminate
      println("Failed to bind!")
    case Success(t) =>
      println(s"Successed to bind to $t")
  }

  if (isTest) {
    testError.TestError.start
    println("Press ENTER to exit!")
    scala.io.StdIn.readLine()
    system.terminate()
  }
}
