package blog

import common._

import akka.actor.{ ActorRef, ActorSystem }
import akka.serialization._
import org.json4s.jackson.Serialization.{ read, writePretty }
import org.json4s.{ DefaultFormats, Formats, jackson, Serialization }
import org.json4s._
import org.joda.time.DateTime
import scala.collection.immutable.TreeMap

sealed trait BlogMessage extends Serializable
case class WrongMessage(message: String) extends BlogMessage

sealed trait BlogCommand extends BlogMessage
case class CreateBlog(title: String, content: String, loggedIn: LoggedIn)
  extends BlogCommand with UserCommand
case class ModifyBlog(title: String, content: String, loggedIn: LoggedIn)
  extends BlogCommand with UserCommand

sealed trait BlogEvent extends BlogMessage
case class BlogCreated(
  id: String,
  userId: String,
  userName: String,
  title: String,
  content: String
)	extends BlogEvent
case class BlogModified(
  id: String,
  userId: String,
  userName: String,
  title: String,
  content: String
)	extends BlogEvent

case class DiscussionItem(id: String, title: String)
case class Blog(
  userId: String = "",
  userName: String = "",
	title: String = "",
  content: String = "",
	discussions: List[DiscussionItem] = Nil
) extends BlogMessage

trait DiscussionCommand extends BlogMessage
case class StartDiscussion(
	id: String,
	blogId: String,
	title: String,
  userId: String,
  userName: String
)	extends DiscussionCommand

case class AddComment(
	id: String,
  content: String,
  loggedIn: LoggedIn
)	extends DiscussionCommand

case class ReplyComment(
	id: String,
	parentId: String,
  content: String,
  loggedIn: LoggedIn
)	extends DiscussionCommand

trait DiscussionEvent extends BlogMessage {
  def id: String
}

case class DiscussionStarted(
	id: String,
  userId: String,
  userName: String,
	blogId: String,
	title: String
)	extends DiscussionEvent

case class CommentAdded(
	id: String,
  userId: String,
  userName: String,
  content: String,
  index: Int
)	extends DiscussionEvent

case class CommentReplied(
	id: String,
  userId: String,
  userName: String,
	parentId: String,
  content: String,
  path: List[Int]
)	extends DiscussionEvent

case class CommentIndex(path: List[Int], childCount: Int)

case class Comment(
  id: String,
  userId: String,
  userName: String,
  content: String
)
case class Discussion(
	id: String = "",
  userId: String,
  userName: String,
	blogId: String = "",
	title: String = "",
	comments: TreeMap[String, CommentIndex] = TreeMap(),
  childCount: Int = 0
) extends BlogMessage

class BlogSerializer extends common.JsonSerializer {
	def identifier = 0xfecb

	implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(
			classOf[LoggedIn],
			classOf[Blog],
			classOf[CreateBlog],
			classOf[BlogCreated],
			classOf[ModifyBlog],
			classOf[BlogModified],
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

object BlogSerializer extends BlogSerializer {
}
