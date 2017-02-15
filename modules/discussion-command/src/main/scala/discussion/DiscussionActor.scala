package discussion

import common.Json
import topic._

import akka.actor._
import akka.persistence._
import com.mongodb.casbah.commons.Imports._

object DiscussionActor {
	def props(id: String) = Props(new DiscussionActor(id))
}

class DiscussionActor(val id: String) extends Actor with PersistentActor {
	import DiscussionActor._
	import common.TypeHintContext._

  override def persistenceId = s"discussion-$id"

  var state: Option[Discussion] = None

  def updateState(event: DiscussionEvent): Unit = event match {
    case DiscussionStarted(id, topicId, title) =>
      state = Some(Discussion(id, topicId, title))
    case CommentAdded(id, title, content) =>
      state = state map { discussion =>
        discussion.copy(
          comments = discussion.comments + (id -> Comment(id, title, content)))
      }
    case CommentReplied(id, parentId, title, content) =>
      state = state map { discussion =>
        discussion.copy(
          comments = discussion.comments + (id -> Comment(id, title, content)))
      }
    case _ =>
  }

  val receiveRecover: Receive = {
    case event: DiscussionEvent =>	updateState(event)
    case SnapshotOffer(_, snapshot: Discussion) => state = Some(snapshot)
  }

  val receiveCommand: Receive = {
    case "snap" if state.isDefined => saveSnapshot(state.get)
    case StartDiscussion(id, topicId, title) if !state.isDefined =>
			val event = DiscussionStarted(id, topicId, title)
      persistAsync(event) {
				event =>
					sender ! event
					updateState(event)
			}
    case AddComment(id, title, content) if state.isDefined &&
        !state.get.comments.contains(id) =>
			val event = CommentAdded(id, title, content)
      persistAsync(event) {
				event =>
					sender ! event
					updateState(event)
			}
    case ReplyComment(id, parentId, title, content) if state.isDefined &&
        state.get.comments.contains(parentId) &&
        !state.get.comments.contains(id) =>
			val event = CommentReplied(id, parentId, title, content)
      persistAsync(event) {
				event =>
					sender ! event
					updateState(event)
			}
    case msg =>
      sender ! WrongMessage(msg.toString)
  }
}

