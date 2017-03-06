import common._
import auth._

import akka.actor._
import akka.kafka._
import akka.kafka.ConsumerMessage._
import akka.kafka.scaladsl._
import akka.persistence._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{UpgradeToWebSocket, TextMessage}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.unmarshalling._
import akka.stream._
import akka.stream.scaladsl._
import java.util.Base64
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

case class Payload(sub: String, exp: String)

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

    def authenticateJwt(credentials: Credentials): Option[LoggedIn] = {
      credentials match {
        case Credentials.Provided(id) =>
          println(id)
          val encoder = Base64.getEncoder()
          val decoder = Base64.getDecoder()
          val parts = id.split('.')
          if (parts.length == 3) {
            val header = parts(0)
            val tokenTry = CommonUtil.encode("secret", s"$header.${parts(1)}")
            tokenTry match {
              case Success(t) if encoder.encodeToString(t) == parts(2) =>
                implicit val formats = DefaultFormats
                val json = parse(new String(decoder.decode(parts(1))))
                  .extract[Payload]
                val exp = json.exp.toLong
                if (System.currentTimeMillis / 1000 <= exp) {
                  val userId = json.sub
                  val validTo = System.currentTimeMillis / 1000 + 5 * 60
                  val payload = encoder.encodeToString(
                    s"""{"sub":"${userId}","exp":"${validTo}"}""".getBytes)
                  val tokenTry2 = CommonUtil.encode("secret", s"$header.$payload")
                  tokenTry2 match {
                    case Success(t) =>
                      val token = encoder.encodeToString(t)
                      Some(LoggedIn(userId, s"Bearer $header.$payload.$token", validTo))
                    case Failure(e) =>
                      None
                  }
                } else {
                  None
                }
              case Success(token) =>
                println(s"$token =?= ${parts(2)}")
                None
              case Failure(e) =>
                None
            }
          } else {
            None
          }
        case _ => None
      }
    }

    def authenticate(credentials: Credentials): Option[LoggedIn] = {
      credentials match {
        case p @ Credentials.Provided(id) if p.verify(id) =>
          val userId = id
          val validTo = System.currentTimeMillis / 1000 + 5 * 60
          val encoder = Base64.getEncoder()
          val header = encoder.encodeToString(
            s"""{"typ":"JWT","alg":"HS256"}""".getBytes)
          val payload = encoder.encodeToString(
            s"""{"sub":"${userId}","exp":"${validTo}"}""".getBytes)
          val tokenTry = CommonUtil.encode("secret", s"$header.$payload")
          tokenTry match {
            case Success(t) =>
              val token = encoder.encodeToString(t)
              Some(LoggedIn(userId, s"Bearer $header.$payload.$token", validTo))
            case Failure(e) =>
              None
          }
        case _ => None
      }
    }

    val authenticates =
      authenticateOAuth2("rapids-oauth", authenticateJwt) |
        authenticateBasic(realm = "rapids", authenticate)

		val route =
			pathPrefix("commands") {
				pathPrefix(Segment) { topic =>
					path(Segment) { id =>
            post {
              authenticates { loggedIn =>
                respondWithHeader(RawHeader("X-Token", loggedIn.token)) {
                  entity(as[String]) { message =>
                    onSuccess(producer.offer(
                      ProducerData(s"$topic-command", id, message))) {
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

	implicit val system = ActorSystem("WebApp")
	implicit val materializer = ActorMaterializer()
	val route = start
	val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8081)

	scala.io.StdIn.readLine()
	system.terminate
}
