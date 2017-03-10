package blog

import common._

import akka.actor._
import akka.persistence._
import akka.stream._
import akka.stream.scaladsl._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.util.{Try, Success, Failure}

object BlogService {
	def props() = Props(new BlogService())
}

class BlogService() extends Actor {
	import BlogService._

  val receive: Receive = process(Map.empty[String, ActorRef])

  def process(blogs: Map[String, ActorRef]): Receive = {
    case message @ ConsumerData(key, value) =>
			val jsonTry = Try(BlogSerializer.fromString(value))
			jsonTry match {
				case Success(json) =>
					val blog = blogs get key getOrElse {
						val actorRef = context.actorOf(BlogActor.props(key), s"blog-$key")
						context become process(blogs + (key -> actorRef))
						actorRef
					}
					blog.tell(json, sender)
				case Failure(e) =>
					sender ! WrongMessage(e + ": " + message.toString)
			}
  }
}

