package common

import akka.serialization._
import org.json4s.jackson.Serialization.{ read, write }
import org.json4s.{ DefaultFormats, Formats, jackson, Serialization }

trait Json

abstract class JsonSerializer extends Serializer {
  implicit val serialization = jackson.Serialization
	implicit def formats: Formats

	def includeManifest: Boolean = false

	def toBinary(obj: AnyRef): Array[Byte] = {
		serialization.write(obj).getBytes
	}

	def fromBinary(
		bytes: Array[Byte],
		clazz: Option[Class[_]]): AnyRef = {
			serialization.read(new String(bytes))
	}
}
