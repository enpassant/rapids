package common

case class ConsumerData(key: String, value: String)
case class ProducerData[A](topic: String, key: String, value: A)
case class WrongCommand(consumerData: ConsumerData)

object CommonUtil {
	def uuid = java.util.UUID.randomUUID.toString
}
