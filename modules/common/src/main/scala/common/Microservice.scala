package common

import akka.actor._

trait Microservice {
  def statActorAndProducer(mq: MQProtocol, name: String)
    (implicit system: ActorSystem) =
  {
    val producer = mq.createProducer[ProducerData[String]]() {
      case msg @ ProducerData(topic, id, value) => msg
    }
    (system.actorOf(Performance.props(name, producer)), producer)
  }
}
