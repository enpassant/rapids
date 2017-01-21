import akka.actor._
import akka.kafka._
import akka.kafka.scaladsl._
import akka.persistence._
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._
import com.mongodb.casbah.commons.Imports._
import salat._
import salat.global._

case object Shutdown
case class Cmd(data: String) extends Json
case class Evt(data: String)

case class ExampleState(events: List[String] = Nil) {
  def updated(evt: Evt): ExampleState = copy(evt.data :: events)
  def size: Int = events.length
  override def toString: String = events.reverse.toString
}

object User {
	def props(id: String) = Props(new User(id))
}

class User(val id: String) extends Actor with PersistentActor {
	import User._

  override def persistenceId = s"user-$id"

  var state = ExampleState()

  def updateState(event: Evt): Unit = state = state.updated(event)
  def updateBsonState(bson: DBObject): Unit =
		updateState(grater[Evt].asObject(bson))

  def numEvents = state.size

  val receiveRecover: Receive = {
    case bson: DBObject =>
			updateBsonState(bson)
    case evt: Evt =>
			updateState(evt)
    case SnapshotOffer(_, snapshot: ExampleState) =>
			state = snapshot
    case SnapshotOffer(_, snapshot: DBObject) =>
			state = grater[ExampleState].asObject(snapshot)
  }

  val receiveCommand: Receive = {
    case Cmd("snap")  => saveSnapshot(grater[ExampleState].asDBObject(state))
    case Cmd("print") => println(state)
    case Cmd(data) =>
      persist(grater[Evt].asDBObject(Evt(s"${data}-${numEvents}")))(updateBsonState)
    case Shutdown =>
			context.stop(self)
  }
}
