package topic

import akka.actor.{ ActorRef, ActorSystem }
import akka.serialization._
import com.typesafe.config.ConfigFactory
import org.json4s.jackson.Serialization.{ read, writePretty }
import org.json4s.{ DefaultFormats, Formats, jackson, Serialization }
import org.json4s._
import org.joda.time.DateTime

sealed trait TopicMessage extends Serializable
trait TopicCommand extends TopicMessage
case class CreateTopic(url: String, title: String) extends TopicCommand

trait TopicEvent extends TopicMessage
case class TopicCreated(id: String, url: String, title: String)
	extends TopicEvent

case class Discussion(id: String, title: String)
case class Topic(
	url: String = "",
	title: String = "",
	discussions: List[Discussion] = Nil
) extends TopicMessage
{
  def updated(evt: TopicEvent): Topic = evt match {
		case TopicCreated(id, url, title) =>
			copy(url = url, title = title)
	}
}

sealed trait DiscussionCommand extends TopicMessage
case class StartDiscussion(
	id: String,
	topicId: String,
	url: String,
	title: String
)	extends DiscussionCommand

class TopicSerializer extends common.JsonSerializer {
	def identifier = 0xfeca

	implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(
			classOf[Topic],
			classOf[CreateTopic],
			classOf[TopicCreated],
			classOf[StartDiscussion]
		))
	} ++ org.json4s.ext.JodaTimeSerializers.all
}
