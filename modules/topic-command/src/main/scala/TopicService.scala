import common.Json

import akka.actor._
import akka.kafka._
import akka.kafka.scaladsl._
import akka.persistence._
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.util.{Try, Success, Failure}

object TopicService {
	def props() = Props(new TopicService())
}

case class ConsumerMessage(key: String, value: String)
case class WrongCommand(consumerMessage: ConsumerMessage)

class TopicService() extends Actor {
	import TopicService._

	implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(
			classOf[TopicCommand],
			classOf[TopicEvent]
		))
	} ++ org.json4s.ext.JodaTimeSerializers.all

  val receive: Receive = process(Map.empty[String, ActorRef])

  def process(topics: Map[String, ActorRef]): Receive = {
    case message @ ConsumerMessage(key, value) =>
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
