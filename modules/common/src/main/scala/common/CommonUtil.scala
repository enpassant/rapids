package common

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.util.{Try, Success}

case class ConsumerData(key: String, value: String)
case class ProducerData[A](topic: String, key: String, value: A)
case class WrongCommand(consumerData: ConsumerData)

case class User(id: String, name: String, roles: String*)
case class Payload(sub: String, exp: Long, jti: String,
  name: String, roles: String*)

object CommonUtil {
	def uuid = java.util.UUID.randomUUID.toString

  val encoder = Base64.getEncoder()

  def createJwt(user: User, duration: Long, created: Long) = {
    val validTo = System.currentTimeMillis / 1000 + duration
    val header = encoder.encodeToString(
      s"""{"typ":"JWT","alg":"HS256"}""".getBytes)
    val roles = "[\"" + user.roles.mkString("\",\"") + "\"]"
    val payload = s"""{"sub":"${user.id}","exp":$validTo,"jti":"$uuid",""" +
      s""""name":"${user.name}","roles":$roles}"""
    val len = payload.length + ((3 - payload.length % 3) % 3)
    val payload64 = encoder.encodeToString(payload.padTo(len, ' ').getBytes)
    CommonUtil.encodeOpt("secret", s"$header.$payload64") { t =>
      val token = encoder.encodeToString(t)
      Some(auth.LoggedIn(
        user.id, s"Bearer $header.$payload64.$token", validTo, created))
    }
  }

  def encode(key: String, data: String): Try[Array[Byte]] = Try {
    val sha256_HMAC = Mac.getInstance("HmacSHA256")
    val secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256")
    sha256_HMAC.init(secret_key)
    sha256_HMAC.doFinal(data.getBytes("UTF-8"))
  }

  def encodeOpt[T](key: String, data: String)(process: Array[Byte] => Option[T])
    : Option[T] =
  {
    encode(key, data) match {
      case Success(result) => process(result)
      case _ => None
    }
  }
}
