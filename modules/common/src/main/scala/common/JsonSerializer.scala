package common

import akka.serialization._
import org.json4s.jackson.Serialization.{ read, write }
import org.json4s.{ DefaultFormats, Formats, jackson, Serialization }

abstract class JsonSerializer extends Serializer {
  implicit val serialization = jackson.Serialization
  implicit def formats: Formats

  def includeManifest: Boolean = false

  def toString(obj: AnyRef): String = {
    serialization.write(obj)
  }

  def fromString(json: String): AnyRef = {
    serialization.read[AnyRef](json)
  }

  def toBinary(obj: AnyRef): Array[Byte] = {
    toString(obj).getBytes
  }

  def fromBinary(
    bytes: Array[Byte],
    clazz: Option[Class[_]]): AnyRef = {
      fromString(new String(bytes))
  }
}
