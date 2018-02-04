package discussion

import common._
import blog._

import akka.actor._
import akka.persistence._

object DiscussionActor {
	def props(id: String) = Props(new DiscussionActor(id))
}

class DiscussionActor(val id: String) extends CommandActor {
	import DiscussionActor._

  override def persistenceId = s"discussion-$id"

  var state: Option[Discussion] = None

  def updateState(event: DiscussionEvent): Unit = event match {
    case DiscussionStarted(id, userId, userName, blogId, title) =>
      state = Some(Discussion(id, blogId, title))
    case CommentAdded(_, id, userId, userName, content, index) =>
      state = state map { discussion =>
        discussion.copy(
          childCount = discussion.childCount + 1,
          comments = discussion.comments + (id -> CommentIndex(List(index), 0)))
      }
    case CommentReplied(_, id, userId, userName, parentId, content, path) =>
      state = state map { discussion =>
        val parentComment = discussion.comments(parentId)
        val childCount = parentComment.childCount
        discussion.copy(
          comments = (discussion.comments - parentId)
            + (id -> CommentIndex(path, 0))
            + (parentId -> parentComment.copy(childCount = childCount + 1)))
      }
  }

  val receiveRecover: Receive = {
    case event: DiscussionEvent =>	updateState(event)
    case SnapshotOffer(_, snapshot: Discussion) => state = Some(snapshot)
  }

  val processCommand: Receive = {
    case "snap" if state.isDefined => saveSnapshot(state.get)
    case StartDiscussion(id, blogId, title, userId, userName)
        if !state.isDefined =>
			val event = DiscussionStarted(id, userId, userName, blogId, title)
      persistAsync(event) { event =>
        sender ! event
        updateState(event)
			}
    case AddComment(commentId, content, loggedIn) if state.isDefined &&
        !state.get.comments.contains(commentId) =>
      val payload = CommonUtil.extractPayload(loggedIn.token)
      payload foreach { p =>
        val index = state.get.childCount
        val event =
          CommentAdded(state.get.id, commentId, p.sub, p.name, content, index)
        persistAsync(event) { event =>
          sender ! event
          updateState(event)
        }
      }
    case ReplyComment(commentId, parentId, content, loggedIn)
      if state.isDefined &&
        state.get.comments.contains(parentId) &&
        !state.get.comments.contains(commentId) =>
      val payload = CommonUtil.extractPayload(loggedIn.token)
      payload foreach { p =>
        val parentComment = state.get.comments(parentId)
        val id = state.get.id
        val path = (parentComment.childCount) :: parentComment.path
        val event =
          CommentReplied(id, commentId, p.sub, p.name, parentId, content, path)
        persistAsync(event) { event =>
          sender ! event
          updateState(event)
        }
      }
    case msg =>
      sender ! WrongMessage(msg.toString)
  }
}
