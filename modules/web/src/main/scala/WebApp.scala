import auth._
import common._
import common.web.Directives._

import akka.actor._
import akka.kafka._
import akka.kafka.ConsumerMessage._
import akka.kafka.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{UpgradeToWebSocket, TextMessage}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.producer.ProducerRecord
import scala.concurrent.Future

object WebApp extends App {
	def start(implicit system: ActorSystem, materializer: ActorMaterializer) = {
		implicit val executionContext = system.dispatcher

		val producer = Kafka.createProducer[ProducerData[String]](
      "localhost:9092")
    {
			case ProducerData(topic, id, value) =>
				new ProducerRecord[Array[Byte], String](
					topic, id.getBytes(), value)
		}

		lazy val consumerSource = Kafka.createConsumerSource(
			"localhost:9092",
			"webapp",
			"client-commands")
		{
      msg => Future {
        (TextMessage(msg.record.value), msg)
      }
    }.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right).run()

		def consumer(clientId: String) = consumerSource.filter {
      msg => msg._1 == clientId
    }.map(_._2)

		val route =
			pathPrefix("commands") {
				pathPrefix(Segment) { topic =>
					path(Segment) { id =>
            post {
              authenticates(getUser) { loggedIn =>
                respondWithHeader(RawHeader("X-Token", loggedIn.token)) {
                  entity(as[String]) { message =>
                    onSuccess {
                      if (loggedIn.created > 0) {
                        val msgLogged = new AuthSerializer().toString(loggedIn)
                        producer.offer(
                          ProducerData("user", loggedIn.userId, msgLogged))
                      }
                      producer.offer(
                        ProducerData("user", loggedIn.userId, message))
                      producer.offer(
                        ProducerData(s"$topic-command", id, message))
                    }
                    {
                      reply =>
                        complete(s"Succesfully send command to $topic topic")
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
			path("") {
				getFromResource(s"public/html/index.html")
			} ~
			path("""([^/]+\.html).*""".r) { path =>
				getFromResource(s"public/html/$path")
			} ~
			path(Remaining) { path =>
				getFromResource(s"public/$path")
			}

		encodeResponse { route }
	}

  def getUser(id: String) = {
    User(id, id.capitalize,
      if (scala.util.Random.nextBoolean) "admin" else "user", "checker")
  }

	implicit val system = ActorSystem("WebApp")
	implicit val materializer = ActorMaterializer()
	val route = start
	val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8081)

	scala.io.StdIn.readLine()
	system.terminate
}
