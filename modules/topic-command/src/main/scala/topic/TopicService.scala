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

	implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(
			classOf[CreateTopic],
			classOf[TopicCommand],
			classOf[TopicEvent]
		))
	} ++ org.json4s.ext.JodaTimeSerializers.all

  val receive: Receive = process(Map.empty[String, ActorRef])

  def process(topics: Map[String, ActorRef]): Receive = {
    case message @ ConsumerData(key, value) =>
			val jsonTry = Try(parse(value).extract[TopicCommand])
			jsonTry match {
				case Success(json) =>
					val topic = topics get key getOrElse {
						val actorRef = context.actorOf(TopicActor.props(key), s"topic-$key")
						context become process(topics + (key -> actorRef))
						actorRef
					}
					topic.tell(json, sender)
				case Failure(e) =>
					sender ! WrongCommand(message)
			}
  }
}

