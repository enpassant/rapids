package common

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.util.Try

case class ConsumerData(key: String, value: String)
case class ProducerData[A](topic: String, key: String, value: A)
case class WrongCommand(consumerData: ConsumerData)

object CommonUtil {
	def uuid = java.util.UUID.randomUUID.toString

  def encode(key: String, data: String): Try[String] = Try {
    val sha256_HMAC = Mac.getInstance("HmacSHA256")
    val secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256")
    sha256_HMAC.init(secret_key)

    sha256_HMAC.doFinal(data.getBytes("UTF-8")).map("%02X" format _).mkString
  }
}
