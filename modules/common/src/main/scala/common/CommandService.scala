package common

import akka.actor._
import akka.persistence._
import akka.stream._
import akka.stream.scaladsl._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.util.{Try, Success, Failure}

import blog._

object CommandService {
	def props(
    fromString: String => AnyRef,
    name: String,
    props: String => Props
  ) = Props(new CommandService(fromString, name, props))
}

class CommandService(
  val fromString: String => AnyRef,
  val name: String,
  val props: String => Props)
extends Actor with ActorLogging
{
  val receive: Receive = process(Map.empty[String, ActorRef])

  def process(actors: Map[String, ActorRef]): Receive = {
    case Terminated(actor) =>
      log.info(s"Terminated: $actor")
      context become process(
        actors.filter { case (key, actorRef) => actorRef != actor }
      )
    case message @ ConsumerData(key, value) =>
			val jsonTry = Try(fromString(value))
			jsonTry match {
				case Success(json) =>
					val actor = actors get key getOrElse {
						val actorRef = context.actorOf(props(key), s"$name-$key")
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
