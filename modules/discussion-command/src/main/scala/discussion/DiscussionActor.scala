package discussion

import common._
import blog._

import org.apache.pekko.actor._
import org.apache.pekko.persistence._

object DiscussionActor {
  def props(id: String) = Props(new DiscussionActor(id))
}

class DiscussionActor(val id: String) extends CommandActor {

  override def persistenceId = s"discussion-$id"

  var state: Option[Discussion] = None

  def updateState(event: DiscussionEvent): Unit = event match {
    case DiscussionStarted(startedId, userId, userName, blogId, _, title) =>
      state = Some(Discussion(startedId, blogId, title))
    case CommentAdded(_, commentId, userId, userName, content, _, index) =>
      state = state map { discussion =>
        discussion.copy(
          childCount = discussion.childCount + 1,
          comments = discussion.comments + (commentId -> CommentIndex(List(index), 0)))
      }
    case CommentReplied(_, replyId, userId, userName, parentId, content, _, path) =>
      state = state map { discussion =>
        val parentComment = discussion.comments(parentId)
        val childCount = parentComment.childCount
        discussion.copy(
          comments = (discussion.comments - parentId)
            + (replyId -> CommentIndex(path, 0))
            + (parentId -> parentComment.copy(childCount = childCount + 1)))
      }
    case _ =>
  }

  val receiveRecover: Receive = {
    case event: DiscussionEvent =>  updateState(event)
    case SnapshotOffer(_, snapshot: Discussion) => state = Some(snapshot)
  }

  val processCommand: Receive = {
    case "snap" if state.isDefined => saveSnapshot(state.get)
    case StartDiscussion(id, blogId, title, userId, userName, datetime)
        if !state.isDefined =>
      val event = DiscussionStarted(id, userId, userName, blogId, datetime, title)
      persistAsync(event) { event =>
        sender() ! event
        updateState(event)
      }
    case AddComment(commentId, content, datetime, loggedIn) if state.isDefined &&
        !state.get.comments.contains(commentId) =>
      val payload = CommonUtil.extractPayload(loggedIn.token)
      payload foreach { p =>
        val index = state.get.childCount
        val event =
          CommentAdded(state.get.id, commentId, loggedIn.userId, loggedIn.userName, content, datetime, index)
        persistAsync(event) { event =>
          sender() ! event
          updateState(event)
        }
      }
    case ReplyComment(commentId, parentId, content, datetime, loggedIn)
      if state.isDefined &&
        state.get.comments.contains(parentId) &&
        !state.get.comments.contains(commentId) =>
      val payload = CommonUtil.extractPayload(loggedIn.token)
      payload foreach { p =>
        val parentComment = state.get.comments(parentId)
        val id = state.get.id
        val path = (parentComment.childCount) :: parentComment.path
        val event =
          CommentReplied(id, commentId, loggedIn.userName, loggedIn.userName, parentId, content, datetime, path)
        persistAsync(event) { event =>
          sender() ! event
          updateState(event)
        }
      }
    case msg =>
      sender() ! WrongMessage(msg.toString)
  }
}
