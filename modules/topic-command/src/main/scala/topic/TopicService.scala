package topic

import common._

import akka.actor._
import akka.persistence._
import akka.stream._
import akka.stream.scaladsl._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.util.{Try, Success, Failure}

object TopicService {
	def props() = Props(new TopicService())
}

class TopicService() extends Actor {
	import TopicService._

  val receive: Receive = process(Map.empty[String, ActorRef])

  def process(topics: Map[String, ActorRef]): Receive = {
    case message @ ConsumerData(key, value) =>
			val jsonTry = Try(new TopicSerializer().fromString(value))
			jsonTry match {
				case Success(json) =>
					val topic = topics get key getOrElse {
						val actorRef = context.actorOf(TopicActor.props(key), s"topic-$key")
						context become process(topics + (key -> actorRef))
						actorRef
					}
					topic.tell(json, sender)
				case Failure(e) =>
					sender ! WrongMessage(message.toString)
			}
  }
}

