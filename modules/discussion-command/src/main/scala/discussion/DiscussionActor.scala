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

  override def persistenceId = s"topic-$id"

  var state: Option[Discussion] = None

  def updateState(event: DiscussionEvent): Unit = event match {
    case DiscussionStarted(id, topicId, title) if state == None =>
      state = Some(Discussion(id, topicId, title))
  }

  val receiveRecover: Receive = {
    case event: DiscussionEvent =>	updateState(event)
    case SnapshotOffer(_, snapshot: Discussion) => state = Some(snapshot)
  }

  val receiveCommand: Receive = {
    case "snap" if state.isDefined => saveSnapshot(state.get)
    case StartDiscussion(id, topicId, title) =>
			val event = DiscussionStarted(id, topicId, title)
      persistAsync(event) {
				event =>
					sender ! event
					updateState(event)
			}
  }
}

