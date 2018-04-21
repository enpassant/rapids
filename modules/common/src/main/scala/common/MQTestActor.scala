package common

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._

case class Subscribe(
  groupId: String,
  subscriber: SourceQueue[ConsumerData],
  topicList: Seq[String])

class MQTestActor extends Actor {
  type Subscribers = Map[String, Map[String, List[SourceQueue[ConsumerData]]]]

  def receive = process(
    Map.empty[String, Map[String, List[SourceQueue[ConsumerData]]]]
  )

  def process(subscribers: Subscribers): Receive = {
    case Subscribe(groupId, subscriber, topicList) =>
      val ns = topicList.foldLeft(subscribers) {
        case (s, topic) =>
          val ss = if (s contains topic) {
            s(topic)
          } else {
            Map.empty[String, List[SourceQueue[ConsumerData]]]
          }
          if (ss contains groupId) {
            s.updated(topic, ss.updated(groupId, subscriber :: ss(groupId)))
          } else {
            s.updated(topic, ss + (groupId -> List(subscriber)))
          }
      }
      sender() ! "Subscribed"
      context become process(ns)

    case msg: ProducerData[String] @unchecked =>
      subscribers.get(msg.topic) foreach {
        ss => ss foreach {
          case (groupId, consumers) => consumers.headOption foreach {
            sourceQueue => sourceQueue offer
              ConsumerData(new String(msg.key), msg.value)
          }
        }
      }
      sender() ! "Produced"

    case _ =>
  }
}

object MQTestActor {
  def props() = Props(new MQTestActor())
}
