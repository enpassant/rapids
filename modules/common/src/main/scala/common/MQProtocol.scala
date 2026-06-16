package common

import org.apache.pekko.Done
import org.apache.pekko.actor._
import org.apache.pekko.stream.KillSwitch
import org.apache.pekko.stream.scaladsl._
import scala.concurrent.Future

trait MQProtocol {
  def createProducer[A]()
    (mapper: A => ProducerData[String])
    (implicit system: ActorSystem): SourceQueueWithComplete[A];

  def createConsumerSource[T](groupId: String, topic: String*)
    (mapper: ConsumerData => Future[T])
    (implicit system: ActorSystem): Source[(String, T), KillSwitch];

  def createConsumer[T](groupId: String, topic: String*)
    (mapper: ConsumerData => Future[T])
    (implicit system: ActorSystem): (KillSwitch, Future[Done]);
}
