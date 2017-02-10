package topic

import akka.actor.{ ActorRef, ActorSystem }
import akka.serialization._
import com.typesafe.config.ConfigFactory
import org.json4s.jackson.Serialization.{ read, writePretty }
import org.json4s.{ DefaultFormats, Formats, jackson, Serialization }
import org.json4s._
import org.joda.time.DateTime

sealed trait TopicMessage extends Serializable
sealed trait TopicCommand extends TopicMessage
case class CreateTopic(title: String, content: String) extends TopicCommand

sealed trait TopicEvent extends TopicMessage
case class TopicCreated(id: String, title: String, content: String)
	extends TopicEvent

case class DiscussionItem(id: String, title: String)
case class Topic(
	title: String = "",
  content: String = "",
	discussions: List[DiscussionItem] = Nil
) extends TopicMessage
{
  def updated(evt: TopicEvent): Topic = evt match {
		case TopicCreated(id, title, content) =>
			copy(title = title, content = content)
	}
}

trait DiscussionCommand extends TopicMessage
case class StartDiscussion(
	id: String,
	topicId: String,
	title: String
)	extends DiscussionCommand

case class AddComment(
	id: String,
	title: String,
  content: String
)	extends DiscussionCommand

case class ReplyComment(
	id: String,
	parentId: String,
	title: String,
  content: String
)	extends DiscussionCommand

trait DiscussionEvent extends TopicMessage {
  def id: String
}

case class DiscussionStarted(
	id: String,
	topicId: String,
	title: String
)	extends DiscussionEvent

case class CommentAdded(
	id: String,
	title: String,
  content: String
)	extends DiscussionEvent

case class CommentReplied(
	id: String,
	parentId: String,
	title: String,
  content: String
)	extends DiscussionEvent

case class Comment(
  id: String,
  title: String,
  content: String,
  comments: List[Comment] = Nil)
case class Discussion(
	id: String = "",
	topicId: String = "",
	title: String = "",
	comments: List[Comment] = Nil
) extends TopicMessage

class TopicSerializer extends common.JsonSerializer {
	def identifier = 0xfeca

	implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(
			classOf[Topic],
			classOf[CreateTopic],
			classOf[TopicCreated],
			classOf[StartDiscussion],
			classOf[AddComment],
			classOf[ReplyComment],
			classOf[CommentAdded],
			classOf[CommentReplied],
			classOf[StartDiscussion]
		))
	} ++ org.json4s.ext.JodaTimeSerializers.all
}
