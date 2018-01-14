package common

import akka.actor._
import akka.kafka._
import akka.kafka.ConsumerMessage._
import akka.kafka.scaladsl._
import akka.stream._
import akka.stream.scaladsl._
import monix.execution.Scheduler
import monix.kafka.KafkaConsumerConfig
import monix.kafka.KafkaConsumerObservable
import monix.kafka.KafkaProducerConfig
import monix.kafka.KafkaProducer
import monix.kafka.config.ObservableCommitType
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._
import scala.concurrent.Future

class Kafka(kafkaConfig: config.KafkaConfig) extends MQProtocol {
	def createProducer[A]()
		(mapper: A => ProducerData[String])
		(implicit system: ActorSystem) =
	{
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher

    val producerCfg = KafkaProducerConfig.default.copy(
      bootstrapServers = List(kafkaConfig.server)
    )

    val io = Scheduler.io(name="engine-io")
    val producer = KafkaProducer[String,String](producerCfg, io)

		Source.queue[A](256, OverflowStrategy.backpressure)
			.to(Sink.foreach { input =>
        val msg = mapper(input)
        producer.send(msg.topic, msg.key, msg.value).runAsync(io)
      })
      .run()
	}

	private def createBaseConsumerSource(
    groupId: String,
    topic: String*)
		(implicit system: ActorSystem) =
	{
		val consumerSettings = ConsumerSettings(
			system,
			new ByteArrayDeserializer,
			new StringDeserializer
		)
			.withBootstrapServers(kafkaConfig.server)
			.withGroupId(groupId)

		Consumer.committableSource(
			consumerSettings,
			Subscriptions.topics(topic :_*)
		)
	}

	def createConsumerSource[T](groupId: String, topic: String*)
    (mapper: ConsumerData => Future[T])
		(implicit system: ActorSystem) =
	{
		implicit val executionContext = system.dispatcher

    createBaseConsumerSource(groupId, topic :_*)
			.mapAsync(1) { msg =>
        mapper(ConsumerData(new String(msg.record.key), msg.record.value))
          .map((_, msg))
      }
      .mapAsync(1) { msg =>
        msg._2.committableOffset.commitScaladsl() map {
          _ => (new String(msg._2.record.key), msg._1)
        }
      }
      .viaMat(KillSwitches.single)(Keep.right)
  }

	def createConsumer[T](groupId: String, topic: String*)
    (mapper: ConsumerData => Future[T])
		(implicit system: ActorSystem) =
	{
		implicit val materializer = ActorMaterializer()
		implicit val executionContext = system.dispatcher

    val consumerCfg = KafkaConsumerConfig.default.copy(
      bootstrapServers = List(kafkaConfig.server),
      groupId = groupId
    )

    import monix.execution.Scheduler
    implicit val io = Scheduler.io()

    val observable =
      KafkaConsumerObservable[String,String](consumerCfg, topic.toList)

    val cancelableFuture = observable.foreach { msg =>
      mapper(ConsumerData(new String(msg.key), msg.value))
    }

    val killSwitch = new KillSwitch {
      def abort(ex: Throwable) = cancelableFuture.cancel
      def shutdown = cancelableFuture.cancel
    }

    (killSwitch, cancelableFuture.map(result => akka.Done)(io))
  }
}
