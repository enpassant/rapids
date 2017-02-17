package blog

import akka.actor.{ ActorRef, ActorSystem }
import akka.serialization._
import com.typesafe.config.ConfigFactory
import org.json4s.jackson.Serialization.{ read, writePretty }
import org.json4s.{ DefaultFormats, Formats, jackson, Serialization }
import org.json4s._
import org.joda.time.DateTime
import scala.collection.immutable.TreeMap

sealed trait BlogMessage extends Serializable
case class WrongMessage(message: String) extends BlogMessage

sealed trait BlogCommand extends BlogMessage
case class CreateBlog(title: String, content: String) extends BlogCommand

sealed trait BlogEvent extends BlogMessage
case class BlogCreated(id: String, title: String, content: String)
	extends BlogEvent

case class DiscussionItem(id: String, title: String)
case class Blog(
	title: String = "",
  content: String = "",
	discussions: List[DiscussionItem] = Nil
) extends BlogMessage

trait DiscussionCommand extends BlogMessage
case class StartDiscussion(
	id: String,
	blogId: String,
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

trait DiscussionEvent extends BlogMessage {
  def id: String
}

case class DiscussionStarted(
	id: String,
	blogId: String,
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
  content: String
)
case class Discussion(
	id: String = "",
	blogId: String = "",
	title: String = "",
	comments: TreeMap[String, Comment] = TreeMap()
) extends BlogMessage

class BlogSerializer extends common.JsonSerializer {
	def identifier = 0xfeca

	implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(
			classOf[Blog],
			classOf[CreateBlog],
			classOf[BlogCreated],
			classOf[StartDiscussion],
			classOf[AddComment],
			classOf[ReplyComment],
			classOf[DiscussionStarted],
			classOf[CommentAdded],
			classOf[CommentReplied],
			classOf[StartDiscussion]
		))
	} ++ org.json4s.ext.JodaTimeSerializers.all
}
