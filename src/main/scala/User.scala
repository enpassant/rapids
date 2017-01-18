import akka.actor._
import akka.kafka._
import akka.kafka.scaladsl._
import akka.persistence._
import akka.stream._
import akka.stream.scaladsl._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._
import reactivemongo.bson._

case class Cmd(data: String)
case class Evt(data: String)

case class ExampleState(events: List[String] = Nil) {
  def updated(evt: Evt): ExampleState = copy(evt.data :: events)
  def size: Int = events.length
  override def toString: String = events.reverse.toString
}

object User {
	def props(id: String) = Props(new User(id))

	implicit val cmdHandler: BSONHandler[BSONDocument, Cmd] =
		Macros.handler[Cmd]

	implicit val evtHandler: BSONHandler[BSONDocument, Evt] =
		Macros.handler[Evt]

	implicit val stateHandler: BSONHandler[BSONDocument, ExampleState] =
		Macros.handler[ExampleState]
}

class User(val id: String) extends Actor with PersistentActor {
	import User._

  override def persistenceId = s"user-$id"

  var state = ExampleState()

  def updateState(event: Evt): Unit = state = state.updated(event)
  def updateBsonState(bson: BSONDocument): Unit =
		updateState(BSON.readDocument[Evt](bson))

  def numEvents = state.size

  val receiveRecover: Receive = {
    case bson: BSONDocument =>
			updateBsonState(bson)
    case evt: Evt =>
			updateState(evt)
    case SnapshotOffer(_, snapshot: ExampleState) =>
			state = snapshot
    case SnapshotOffer(_, snapshot: BSONDocument) =>
			state = BSON.readDocument[ExampleState](snapshot)
  }

  val receiveCommand: Receive = {
    case Cmd(data) =>
      persist(BSON.write(Evt(s"${data}-${numEvents}")))(updateBsonState)
    case "snap"  => saveSnapshot(BSON.write(state))
    case "print" => println(state)
  }
}
