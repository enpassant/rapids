package common

import akka.actor._
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl._
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future

class MQTest(val system: ActorSystem) extends MQProtocol
{
  val mqTestActor = system.actorOf(MQTestActor.props(), "MQTestActor")

  def createProducer[A]()
    (mapper: A => ProducerData[String])
    (implicit system: ActorSystem) =
  {
    implicit val askTimeout = Timeout(5.seconds)
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    Source.queue[A](256, OverflowStrategy.backpressure)
      .map(mapper)
      .mapAsync(5)(msg => (mqTestActor ? msg).mapTo[String])
      .to(Sink.ignore)
      .run()
  }

  def createConsumerSource[T](groupId: String, topic: String*)
    (mapper: ConsumerData => Future[T])
    (implicit system: ActorSystem) =
  {
    implicit val executionContext = system.dispatcher

    createBaseConsumerSource(groupId, topic :_*)
      .mapAsync(1) { msg =>
        mapper(msg)
          .map((msg.key, _))
      }
      .viaMat(KillSwitches.single)(Keep.right)
  }

  def createConsumer[T](groupId: String, topic: String*)
    (mapper: ConsumerData => Future[T])
    (implicit system: ActorSystem) =
  {
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    createBaseConsumerSource(groupId, topic :_*)
      .mapAsync(1)(mapper)
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  private def createBaseConsumerSource(groupId: String, topic: String*)
    (implicit system: ActorSystem)
    : Source[ConsumerData, SourceQueueWithComplete[ConsumerData]] =
  {
    val source = Source.queue[ConsumerData](
      256,
      OverflowStrategy.backpressure
    )
    source mapMaterializedValue { queue =>
      mqTestActor ! Subscribe(groupId, queue, topic)
      queue
    }
  }
}
