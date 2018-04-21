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
  val isDev = args.length > 0 && args(0) == "-d"
  val isProd = !isTest && !isDev

	implicit val system = ActorSystem("Main")
	implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val mq =
    if (isTest) new MQTest(system)
    else if (isDev) new Kafka(DevelopmentKafkaConfig)
    else new Kafka(ProductionKafkaConfig)

  println("Program has started")

  val blogStore = new BlogStoreDB(
    if (isProd) ProductionBlogQueryBuilderConfig
    else DevelopmentBlogQueryBuilderConfig
  )
  val discussionStore = new DiscussionStoreDB(
    if (isProd) ProductionDiscussionQueryBuilderConfig
    else DevelopmentDiscussionQueryBuilderConfig
  )

	val routeWeb = WebApp.start(OauthConfig.get)

  Thread.sleep(2000)

	val routeBlogQuery =
    blog.query.BlogQuery.start(
      if (isProd) ProductionBlogQueryConfig
      else DevelopmentBlogQueryConfig
    )
	val routeDiscussionQuery =
    discussion.query.DiscussionQuery.start(
      if (isProd) ProductionDiscussionQueryConfig
      else DevelopmentDiscussionQueryConfig
    )
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

  if (!isProd) {
    testError.TestError.start
    println("Press ENTER to exit!")
    scala.io.StdIn.readLine()
    system.terminate()
  }
}
