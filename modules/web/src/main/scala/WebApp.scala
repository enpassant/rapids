import common._
import blog.BlogSerializer
import common.web.Directives._
import config.{OauthConfig, ProductionKafkaConfig}

import org.apache.pekko.actor._
import org.apache.pekko.kafka._
import org.apache.pekko.kafka.ConsumerMessage._
import org.apache.pekko.kafka.scaladsl._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers._
import org.apache.pekko.http.scaladsl.model.headers.LinkParams._
import org.apache.pekko.http.scaladsl.model.ws.{UpgradeToWebSocket, TextMessage}
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl._
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.concurrent.Future
import scala.collection.SortedSet

object WebApp extends App with Microservice {
  def start(oauthConfig: OauthConfig)
    (implicit mq: MQProtocol,
      system: ActorSystem,
      materializer: ActorMaterializer) =
  {
    implicit val executionContext = system.dispatcher

    val (statActor, producer) = statActorAndProducer(mq, "web-app")

    lazy val consumerSource =
      mq.createConsumerSource("webapp", "client-commands")
    {
      msg => Future(TextMessage(msg.value))
    }.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right).run()

    def consumer(clientId: String) = consumerSource.filter {
      msg => msg._1 == clientId
    }.map(_._2)

    var links = SortedSet.empty[FunctionLink]

    val webAppConsumer = mq.createConsumer("webapp", "web-app") { msg =>
      val json = CommonSerializer.fromString(msg.value)
      json match {
        case link: FunctionLink =>
          println("New link has added: " + link)
          links = links + link
        case _ =>
      }
      Future { true }
    }

    def getLink() = {
      Link(
        links.toList map { functionLink =>
          LinkValue(Uri(functionLink.url), title(functionLink.title))
        }
      )
    }

    val route =
      pathPrefix("commands") {
        pathPrefix(Segment) { topic =>
          path(Segment) { id =>
            post {
              authenticates(getUser) { loggedIn =>
                respondWithHeader(RawHeader("X-Token", loggedIn.token)) {
                  entity(as[String]) { message =>
                    onSuccess {
                      val msgLogged = BlogSerializer.toString(loggedIn)
                      if (loggedIn.created > 0) {
                        producer.offer(
                          ProducerData("user", loggedIn.userId, msgLogged))
                      }
                      producer.offer(
                        ProducerData("user", loggedIn.userId, message))
                      val json = parse(message)
                      val result = json.values match {
                        case m: Map[String, _] @unchecked
                          if m.contains("loggedIn")
                        =>
                          compact(render(
                            json merge
                              JObject(JField("loggedIn", parse(msgLogged)))
                            ))
                        case _ => message
                      }
                      producer.offer(
                        ProducerData(s"$topic-command", id, result))
                    }
                    {
                      reply =>
                        complete(s"Succesfully send command to $topic topic: $reply")
                    }
                  }
                }
              }
            }
          }
        }
      } ~
      pathPrefix("updates") {
        path(Segment) { id =>
          optionalHeaderValueByType[UpgradeToWebSocket]() {
            case Some(upgrade) =>
              complete(
                upgrade.handleMessagesWithSinkSource(Sink.ignore, consumer(id)))
            case None =>
              reject(ExpectedWebSocketRequestRejection)
          }
        }
      } ~
      path("auth" / "callback") {
        auth.GoogleOauth.route(oauthConfig)
      } ~
      path("login") {
        post {
          authenticates(getUser) { loggedIn =>
            respondWithHeader(RawHeader("X-Token", loggedIn.token)) {
              complete("Logged in")
            }
          }
        }
      } ~
      path("") {
        (get | head) {
          respondWithHeader(getLink()) {
            getFromResource(s"public/html/index.html")
          }
        }
      } ~
      path("""([^/]+\.html).*""".r) { path =>
        getFromResource(s"public/html/$path")
      } ~
      path(Remaining) { path =>
        getFromResource(s"public/$path")
      }

    stat(statActor) {
      encodeResponse { route }
    }
  }

  def getUser(id: String) = {
    User(id, id.capitalize,
      if (scala.util.Random.nextBoolean) "admin" else "user", "checker")
  }

  implicit val mq = new Kafka(ProductionKafkaConfig)
  implicit val system = ActorSystem("WebApp")
  implicit val materializer = ActorMaterializer()

  val routeWeb = WebApp.start(OauthConfig.get)
  val bindingFuture = Http().bindAndHandle(routeWeb, "0.0.0.0", 8081)
}
