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

  var state = Topic()

  def updateState(event: TopicEvent): Unit = state = state.updated(event)

  val receiveRecover: Receive = {
    case event: TopicEvent =>	updateState(event)
    case SnapshotOffer(_, snapshot: Topic) => state = snapshot
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
  }
}

