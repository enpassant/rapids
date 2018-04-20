import common._

import blog._
import config._
import discussion._

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

  Thread.sleep(10000)

  println("Program has started")

  val blogStore = new BlogStoreDB(ProductionBlogQueryBuilderConfig)
  val discussionStore = new DiscussionStoreDB(
    ProductionDiscussionQueryBuilderConfig)

	val routeWeb = WebApp.start(OauthConfig.get)

  Thread.sleep(2000)

	val routeBlogQuery =
    blog.query.BlogQuery.start(ProductionBlogQueryConfig)
	val routeDiscussionQuery =
    discussion.query.DiscussionQuery.start(ProductionDiscussionQueryConfig)
	val routeMonitor = monitor.Monitor.start
	val route = routeBlogQuery ~ routeDiscussionQuery ~ routeMonitor ~ routeWeb
	BlogCommandApp.start
	DiscussionCommandApp.start
	BlogQueryBuilder.start(blogStore)
	DiscussionQueryBuilder.start(discussionStore)

	val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

  bindingFuture.onComplete {
    case Failure(ex) =>
      ex.printStackTrace
      system.terminate
      println("Failed to bind!")
    case Success(t) =>
      println(s"Successed to bind to $t")
  }

  sys.ShutdownHookThread {
    println("Exiting")
    system.terminate()
  }

  if (isTest) {
    testError.TestError.start
    println("Press ENTER to exit!")
    scala.io.StdIn.readLine()
    system.terminate()
  }
}
