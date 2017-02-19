package common.web

import common._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
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
  def completePage(
    makeObject: JObject,
    render: Object => String,
    template: String): Route =
  {
    get {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          render(makeObject))
      ) ~
      complete(makeObject) ~
			getFromResource(template + ".hbs", `text/html+xml`)
    }
  }

  def completePage(
    makeObject: Option[JValue],
    render: Object => String,
    template: String): Route =
  {
    get {
      complete(
        makeObject map { obj =>
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            render(obj))
      }) ~
      complete(makeObject) ~
			getFromResource(template + ".hbs", `text/html+xml`)
    }
  }
}
