package discussion

import common._
import blog._

import akka.actor._
import akka.persistence._
import com.mongodb.casbah.commons.Imports._
import scala.concurrent.duration._

object DiscussionActor {
	def props(id: String) = Props(new DiscussionActor(id))

  val PATH_CHARS =
    "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
  val PATH_CHARS_LEN = PATH_CHARS.length

  def indexToPath(index: Int, path: String): String = if (index == 0) {
    path
  } else if (index < PATH_CHARS_LEN) {
    PATH_CHARS(index) + path
  } else {
    indexToPath(
      index / PATH_CHARS_LEN,
      PATH_CHARS(index % PATH_CHARS_LEN) + path)
  }
}

class DiscussionActor(val id: String) extends Actor with PersistentActor {
	import DiscussionActor._

  context.setReceiveTimeout(10.minutes)

  override def persistenceId = s"discussion-$id"

  var state: Option[Discussion] = None

  def updateState(event: DiscussionEvent): Unit = event match {
    case DiscussionStarted(id, userId, userName, blogId, title) =>
      state = Some(Discussion(id, blogId, title))
    case CommentAdded(id, userId, userName, content, index) =>
      state = state map { discussion =>
        discussion.copy(
          childCount = discussion.childCount + 1,
          comments = discussion.comments + (id -> CommentIndex(List(index), 0)))
      }
    case CommentReplied(id, userId, userName, parentId, content, path) =>
      state = state map { discussion =>
        val parentComment = discussion.comments(parentId)
        val childCount = parentComment.childCount
        discussion.copy(
          comments = (discussion.comments - parentId)
            + (id -> CommentIndex(path, 0))
            + (parentId -> parentComment.copy(childCount = childCount + 1)))
      }
    case _ =>
  }

  val receiveRecover: Receive = {
    case event: DiscussionEvent =>	updateState(event)
    case SnapshotOffer(_, snapshot: Discussion) => state = Some(snapshot)
  }

  val receiveCommand: Receive = {
    case ReceiveTimeout =>
      self ! PoisonPill
    case "snap" if state.isDefined => saveSnapshot(state.get)
    case StartDiscussion(id, blogId, title, userId, userName)
        if !state.isDefined =>
			val event = DiscussionStarted(id, userId, userName, blogId, title)
      persistAsync(event) {
				event =>
					sender ! event
					updateState(event)
			}
    case AddComment(id, content, loggedIn) if state.isDefined &&
        !state.get.comments.contains(id) =>
      val payload = CommonUtil.extractPayload(loggedIn.token)
      payload foreach { p =>
        val index = state.get.childCount
        val event = CommentAdded(id, p.sub, p.name, content, index)
        persistAsync(event) {
          event =>
            sender ! event
            updateState(event)
        }
      }
    case ReplyComment(id, parentId, content, loggedIn) if state.isDefined &&
        state.get.comments.contains(parentId) &&
        !state.get.comments.contains(id) =>
      val payload = CommonUtil.extractPayload(loggedIn.token)
      payload foreach { p =>
        val parentComment = state.get.comments(parentId)
        val path = (parentComment.childCount) :: parentComment.path
        val event = CommentReplied(id, p.sub, p.name, parentId, content, path)
        persistAsync(event) {
          event =>
            sender ! event
            updateState(event)
        }
      }
    case msg =>
      sender ! WrongMessage(msg.toString)
  }
}

