package common

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.{ HttpCharsets, MediaTypes }
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import org.joda.time.DateTime
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.mongo.JObjectParser._

object BaseFormats extends BaseFormats {

  sealed abstract class ShouldWritePretty

  object ShouldWritePretty {
    object True extends ShouldWritePretty
    object False extends ShouldWritePretty
  }
}

trait Json

trait BaseFormats {
  import BaseFormats._

  implicit val serialization = jackson.Serialization
  implicit val formats =
    DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all

  implicit val SeqJsonMarshaller =
    BaseFormats.marshaller[JValue](MediaTypes.`application/json`)

  implicit val SeqJsonCollectionMarshaller =
    BaseFormats.marshaller[Seq[JValue]](MediaTypes.`application/json`)

  lazy val `application/collection+json` =
		customMediaTypeUTF8("collection+json")

  def customMediaTypeUTF8(name: String): MediaType.WithFixedCharset =
    MediaType.customWithFixedCharset(
      "application",
      name,
      HttpCharsets.`UTF-8`
    )

  def json4sUnmarshallMediaType[A: Manifest]
		(mediaType: MediaType)
    (serialization: Serialization, formats: Formats)
		: FromEntityUnmarshaller[A] =
	{
	    unmarshaller(mediaType)(manifest, serialization, formats)
	}

	implicit def json4sUnmarshallerConverter[A <: Json : Manifest]
		(implicit serialization: Serialization, formats: Formats)
		: FromEntityUnmarshaller[A] =
	{
		unmarshaller(MediaTypes.`application/json`)(manifest, serialization, formats)
	}

  /**
   * HTTP entity => `A`
   *
   * @tparam A type to decode
   * @return unmarshaller for `A`
   */
  def unmarshaller[A: Manifest](mediaType: MediaType)
    (implicit serialization: Serialization, formats: Formats)
		: FromEntityUnmarshaller[A] =
	{
    Unmarshaller
      .stringUnmarshaller
      .forContentTypes(mediaType)
      .map { data =>
        serialization.read(data)
      }
	}

  def json4sMarshallMediaType[A <: AnyRef](mediaType: MediaType)
    (serialization: Serialization, formats: Formats,
      shouldWritePretty: ShouldWritePretty = ShouldWritePretty.False)
		: ToEntityMarshaller[A] =
	{
    marshaller(mediaType)(serialization, formats, shouldWritePretty)
	}

  //implicit def json4sMarshallerConverter[A <: AnyRef]
    //(implicit serialization: Serialization, formats: Formats,
		//	shouldWritePretty: ShouldWritePretty = ShouldWritePretty.False)
		//: ToEntityMarshaller[A] =
	//{
    //marshaller(MediaTypes.`application/json`)
		//	(serialization, formats, shouldWritePretty)
	//}

  /**
   * `A` => HTTP entity
   *
   * @tparam A type to encode, must be upper bounded by `AnyRef`
   * @return marshaller for any `A` value
   */
  def marshaller[A <: AnyRef](mediaType: MediaType)
    (implicit serialization: Serialization, formats: Formats,
      shouldWritePretty: ShouldWritePretty = ShouldWritePretty.False)
		: ToEntityMarshaller[A] =
	{
    shouldWritePretty match {
      case ShouldWritePretty.False =>
        Marshaller.StringMarshaller.wrap(mediaType)(serialization.write[A])
      case _ =>
        Marshaller.StringMarshaller.wrap(mediaType)(serialization.writePretty[A])
    }
	}
}


