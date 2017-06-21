package common

import org.json4s.{ DefaultFormats, Formats, jackson, Serialization }
import org.json4s._

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
			classOf[FunctionLink],
			classOf[Stat]
		))
	} ++ org.json4s.ext.JodaTimeSerializers.all
}
