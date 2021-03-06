package common.web

import common._

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, HttpCookiePair}
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directive1
import akka.stream._
import java.util.Base64
import com.mongodb.casbah.Imports._
import org.json4s._
import org.json4s.jackson.JsonMethods.{parse => jparse}
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.mongo.JObjectParser._

object Directives extends BaseFormats {
  def completePage(render: JValue => String, template: String)
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

  val encoder = Base64.getEncoder()
  val decoder = Base64.getDecoder()

  def authenticateJwt(getUser: String => User)
    (credentials: Credentials): Option[LoggedIn] =
  {
    credentials match {
      case Credentials.Provided(id) =>
        extractJwt(getUser)(id)
      case _ => None
    }
  }

  def extractJwt(getUser: String => User)
    (token: String): Option[LoggedIn] =
  {
    val parts = token.split('.')
    if (parts.length == 3) {
      val header = parts(0)
      CommonUtil.encodeOpt("secret", s"$header.${parts(1)}") { t =>
        if (encoder.encodeToString(t) == parts(2)) {
          implicit val formats = DefaultFormats
          val payload = jparse(new String(decoder.decode(parts(1))))
            .extract[Payload]
          if (System.currentTimeMillis / 1000 <= payload.exp) {
            val user = User(payload.sub, payload.name, payload.roles:_*)
            CommonUtil.createJwt(user, 5 * 60, 0)
          } else {
            None
          }
        } else {
          None
        }
      }
    } else {
      None
    }
  }

  def authenticate(getUser: String => User)
    (credentials: Credentials): Option[LoggedIn] =
  {
    credentials match {
      case p @ Credentials.Provided(id) if p.verify(id) =>
        val user = getUser(id)
        CommonUtil.createJwt(user, 5 * 60, System.currentTimeMillis / 1000)
      case _ => None
    }
  }

  def authenticates(getUser: String => User): Directive1[LoggedIn] = {
    val cookieDirective:Directive1[LoggedIn] = optionalCookie("X-Token").flatMap {
      case Some(cookie) => {
        extractJwt(getUser)(cookie.value) match {
          case Some(loggedIn) =>
            provide(loggedIn)
          case _ => reject
        }
      }
      case _ =>
        reject
    }

    cookieDirective |
      authenticateOAuth2("rapids", authenticateJwt(getUser)) |
        authenticateBasic(realm = "rapids", authenticate(getUser))
  }

  def stat(statActor: ActorRef)(route: Route) = (request: RequestContext) =>
    Performance.statF(statActor)(route(request))
}
