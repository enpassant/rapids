package discussion

import common._
import topic._

import akka.actor._
import akka.persistence._
import akka.stream._
import akka.stream.scaladsl._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.util.{Try, Success, Failure}

object DiscussionService {
	def props() = Props(new DiscussionService())
}

class DiscussionService() extends Actor {
	import DiscussionService._

	implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(
			classOf[StartDiscussion],
			classOf[TopicCommand],
			classOf[TopicEvent]
		))
	} ++ org.json4s.ext.JodaTimeSerializers.all

  val receive: Receive = process(Map.empty[String, ActorRef])

  def process(actors: Map[String, ActorRef]): Receive = {
    case message @ ConsumerData(key, value) =>
			val jsonTry = Try(parse(value).extract[DiscussionCommand])
			jsonTry match {
				case Success(json) =>
					val actor = actors get key getOrElse {
						val actorRef =
							context.actorOf(DiscussionActor.props(key), s"discussion-$key")
						context become process(actors + (key -> actorRef))
						actorRef
					}
					actor.tell(json, sender)
				case Failure(e) =>
					sender ! WrongCommand(message)
			}
  }
}
