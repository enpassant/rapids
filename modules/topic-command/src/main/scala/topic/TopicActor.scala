package topic

import common.Json

import akka.actor._
import akka.persistence._
import com.mongodb.casbah.commons.Imports._

object TopicActor {
	def props(id: String) = Props(new TopicActor(id))
}

class TopicActor(val id: String) extends Actor with PersistentActor {
	import TopicActor._
	import common.TypeHintContext._

  override def persistenceId = s"topic-$id"

  var state: Option[Topic] = None

  def updateState(event: TopicMessage): Unit = event match {
    case TopicCreated(id, title, content) if !state.isDefined =>
      state = Some(Topic(title, content))
    case DiscussionStarted(id, topicId, title) =>
      state = state map { topic =>
        topic.copy(discussions = DiscussionItem(id, title) :: topic.discussions)
      }
    case _ =>
  }

  val receiveRecover: Receive = {
    case event: TopicMessage =>	updateState(event)
    case SnapshotOffer(_, snapshot: Topic) => state = Some(snapshot)
  }

  val receiveCommand: Receive = {
    case "snap"  => saveSnapshot(state)
    case CreateTopic(title, content) =>
			val event = TopicCreated(id, title, content)
      persistAsync(event) {
				event =>
					sender ! event
					updateState(event)
			}
    case event: DiscussionStarted =>
      persistAsync(event) {
				event =>
					sender ! event
					updateState(event)
			}
  }
}

