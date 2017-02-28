package auth

import common._
import auth._

import akka.actor._
import akka.persistence._
import akka.stream._
import akka.stream.scaladsl._
import org.joda.time.DateTime
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.util.{Try, Success, Failure}

case class Status()

class AuthService() extends Actor {
	import AuthService._

  val receive: Receive = process(Map.empty[String, ActorRef])

  def process(actors: Map[String, ActorRef]): Receive = {
    case message @ ConsumerData(key, value) =>
			val jsonTry = Try(new AuthSerializer().fromString(value))
			jsonTry match {
				case Success(Login(user, password)) =>
          if (user == password) {
  					sender ! LoggedIn(getUserId(user), "token", DateTime.now)
          } else {
  					sender ! WrongMessage(message.toString)
          }
				case Success(json) =>
				case Failure(e) =>
					sender ! WrongMessage(message.toString)
			}
  }

  def getUserId(user: String) = user
}

object AuthService {
	def props() = Props(new AuthService())
}
