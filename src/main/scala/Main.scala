import common._
import blog._
import config._
import discussion._
import org.apache.pekko.actor._
import org.apache.pekko.http.scaladsl.{ConnectionContext, Http}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream._

import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, TrustManagerFactory}
import scala.util.{Failure, Success}

object Main extends App {
  val isTest = args.length > 0 && args(0) == "-t"
  val isDev = args.length > 0 && args(0) == "-d"
  val isProd = !isTest && !isDev

  implicit val system: ActorSystem = ActorSystem("Main")
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: scala.concurrent.ExecutionContextExecutor = system.dispatcher
  implicit val mq: MQProtocol =
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

  // 1. Keystore betöltése a .p12 fájlból
  val password = "ez-egy-hosszu-titkos-jelszo".toCharArray
  val ks = KeyStore.getInstance("PKCS12")
  val fis = new FileInputStream("acme/keystore.p12")
  try { ks.load(fis, password) } finally { fis.close() }

  // 2. Keystor-t kezelő gyárak inicializálása
  val kmf = KeyManagerFactory.getInstance("SunX509")
  kmf.init(ks, password)
  val tmf = TrustManagerFactory.getInstance("SunX509")
  tmf.init(ks)

  // 3. SSLContext létrehozása és átadása a Pekko-nak
  val sslContext = SSLContext.getInstance("TLS")
  sslContext.init(kmf.getKeyManagers, tmf.getTrustManagers, null)

  val httpsContext = ConnectionContext.httpsServer(sslContext)

  val bindingFuture = Http().newServerAt("0.0.0.0", 1443).enableHttps(httpsContext).bindFlow(route)

  bindingFuture.onComplete {
    case Failure(ex) =>
      ex.printStackTrace
      system.terminate()
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
