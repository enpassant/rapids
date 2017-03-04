package auth

import akka.actor.{ ActorRef, ActorSystem }
import akka.serialization._
import com.typesafe.config.ConfigFactory
import org.json4s.jackson.Serialization.{ read, writePretty }
import org.json4s.{ DefaultFormats, Formats, jackson, Serialization }
import org.json4s._
import scala.collection.immutable.TreeMap

sealed trait AuthMessage extends Serializable
case class WrongMessage(message: String) extends AuthMessage

sealed trait AuthCommand extends AuthMessage
case class Login(user: String, password: String) extends AuthCommand

sealed trait AuthEvent extends AuthMessage
case class LoggedIn(userId: String, token: String, validTo: Long)
	extends AuthEvent

class AuthSerializer extends common.JsonSerializer {
	def identifier = 0xfeca

	implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(
			classOf[Login],
			classOf[LoggedIn]
		))
	} ++ org.json4s.ext.JodaTimeSerializers.all
}
