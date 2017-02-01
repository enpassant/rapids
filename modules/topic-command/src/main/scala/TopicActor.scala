import common.Json

import akka.actor._
import akka.persistence._
import com.mongodb.casbah.commons.Imports._
import salat._

trait TopicCommand extends Json
case class CreateTopic(url: String, title: String) extends TopicCommand

trait TopicEvent extends Json
case class TopicCreated(url: String, title: String) extends TopicEvent

case class Discussion(id: String, title: String)

case class Topic(
	url: String = "",
	title: String = "",
	discussions: List[Discussion] = Nil
) {
  def updated(evt: TopicEvent): Topic = evt match {
		case TopicCreated(url, title) =>
			copy(url = url, title = title)
	}
}

object TopicActor {
	def props(id: String) = Props(new TopicActor(id))
}

class TopicActor(val id: String) extends Actor with PersistentActor {
	import TopicActor._
	import common.TypeHintContext._

  override def persistenceId = s"topic-$id"

  var state = Topic()

  def updateState(event: TopicEvent): Unit = state = state.updated(event)
  def updateBsonState(bson: DBObject): Unit = {
		updateState(grater[TopicEvent].asObject(bson))
	}

  val receiveRecover: Receive = {
    case bson: DBObject =>
			updateBsonState(bson)
    case evt: TopicEvent =>
			updateState(evt)
    case SnapshotOffer(_, snapshot: Topic) =>
			state = snapshot
    case SnapshotOffer(_, snapshot: DBObject) =>
			state = grater[Topic].asObject(snapshot)
  }

  val receiveCommand: Receive = {
    case "snap"  => saveSnapshot(grater[Topic].asDBObject(state))
    case CreateTopic(url, title) =>
			val event = TopicCreated(url, title)
      persist(grater[TopicCreated].asDBObject(event))(updateBsonState)
  }
}

