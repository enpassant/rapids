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

class BlogService() extends Actor with ActorLogging {
	import BlogService._

  val receive: Receive = process(Map.empty[String, ActorRef])

  def process(actors: Map[String, ActorRef]): Receive = {
    case Terminated(actor) =>
      log.info(s"Terminated: $actor")
      context become process(
        actors.filter { case (key, actorRef) => actorRef != actor }
      )
    case message @ ConsumerData(key, value) =>
			val jsonTry = Try(BlogSerializer.fromString(value))
			jsonTry match {
				case Success(json) =>
					val actor = actors get key getOrElse {
						val actorRef = context.actorOf(BlogActor.props(key), s"blog-$key")
            context.watch(actorRef)
						context become process(actors + (key -> actorRef))
						actorRef
					}
					actor.tell(json, sender)
				case Failure(e) =>
					sender ! WrongMessage(e + ": " + message.toString)
			}
  }
}

