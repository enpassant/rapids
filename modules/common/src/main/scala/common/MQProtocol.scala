package common

import akka.Done
import akka.actor._
import akka.stream.KillSwitch
import akka.stream.scaladsl._
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
