package auth

import common._
import auth._

import akka.actor._
import akka.persistence._
import akka.stream._
import akka.stream.scaladsl._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.util.{Try, Success, Failure}

case class Status()

class AuthService() extends Actor {
	import AuthService._

  val receive: Receive = process(Map.empty[String, ActorRef])

  def process(actors: Map[String, ActorRef]): Receive = {
    case Login(user, password) =>
      if (user == password) {
        val userId = getUserId(user)
        val validTo = System.currentTimeMillis + 5 * 60 * 1000
        val tokenTry = CommonUtil.encode("secret", s"$userId.$validTo")
        val result = tokenTry match {
          case Success(token) =>
            LoggedIn(userId, token, validTo)
          case Failure(e) =>
            WrongMessage(e.toString)
        }

        sender ! result
      } else {
        sender ! WrongMessage("Wrong credentials")
      }
    case json =>
      sender ! None
  }

  def getUserId(user: String) = user
}

object AuthService {
	def props() = Props(new AuthService())
}
