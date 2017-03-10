package common

import akka.actor.{ ActorRef, ActorSystem }
import akka.serialization._
import com.typesafe.config.ConfigFactory
import org.json4s.jackson.Serialization.{ read, writePretty }
import org.json4s.{ DefaultFormats, Formats, jackson, Serialization }
import org.json4s._
import org.joda.time.DateTime
import scala.collection.immutable.TreeMap

case class FunctionLink(order: Int, url: String, title: String)
  extends Ordered[FunctionLink]
{
  def compare(that: FunctionLink) = order.compare(that.order)
}

object CommonSerializer extends common.JsonSerializer {
	def identifier = 0xfeca

	implicit val formats = new DefaultFormats {
		override val typeHintFieldName = "_t"
		override val typeHints = ShortTypeHints(List(
			classOf[FunctionLink]
		))
	} ++ org.json4s.ext.JodaTimeSerializers.all
}
