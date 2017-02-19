package common.web

import common._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream._
import com.github.jknack.handlebars.{ Context, Handlebars }
import com.mongodb.casbah.Imports._
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.mongo.JObjectParser._

object Directives extends BaseFormats {
  def completePage(render: Object => String, template: String)
    (makeObject: => Option[JValue]): Route =
  {
    get {
      rejectEmptyResponse {
        handleReq(MediaTypes.`text/html`) {
          complete {
            makeObject map { obj =>
            HttpEntity(
              ContentTypes.`text/html(UTF-8)`,
              render(obj))
            }}
        } ~
        handleReq(MediaTypes.`application/json`)(complete(makeObject)) ~
        getFromResource(template + ".hbs", `text/html+xml`)
      }
    }
  }

  def handleReq[T <: ToResponseMarshallable](mediaType: MediaType)
    (route: Route) =
  {
    extractRequest { request =>
      request.header[Accept].map(_.mediaRanges).flatMap { r =>
        r.find(_.matches(mediaType)).map(_ => route)
      }.getOrElse(reject)
    }
  }
}
