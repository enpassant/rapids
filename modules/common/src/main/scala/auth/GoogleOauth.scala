package auth

import common._
import config._

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.security._
import java.security.spec.RSAPublicKeySpec
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.headers.LinkParams._
import akka.http.scaladsl.model.ws.{UpgradeToWebSocket, TextMessage}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream._
import org.json4s._
import org.json4s.jackson.JsonMethods.{parse => jparse}
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import akka.util.ByteString
import org.json4s.jackson.JsonMethods._

case class GoogleResponse(
  access_token: String,
  token_type: String,
  expires_in: Int,
  id_token: String)

case class Header(
  alg: String,
  kid: String
)

case class IdToken(
  azp: String,
  aud: String,
  sub: String,
  email: String,
  email_verified: Boolean,
  at_hash: String,
  nonce: String,
  iss: String,
  iat: Long,
  exp: Long,
  name: String,
  picture: String,
  given_name: String,
  family_name: String,
  locale: String
)

object GoogleOauth {
  val encoder = Base64.getUrlEncoder()
  val decoder = Base64.getUrlDecoder()

  val googleN = Map(
    "a0eb736e47190f38e9251187676403e09aa8f9f0" -> "iz7sYHwIcY9XIvDJ_s7ujJnL-OPrSApxyR9cUmhB_YazFhFQOEBHviib46CwY-RLfTfsL6tBwZsI10RtgwVUGjZZ41G6gaZwzGtrrwRvx22_X1GROb1UO5wu0GVJKSD_9Hf7-SnzwkzJ73OiQAFkegiKqghDCRKRxlKQzRFIpdgXuSfdDxy7MPhhKC3YGfKj2S4jaNzNxFJm-8dY-oa2-7f2qQvgXvVnEnFG72FbJChy_Ol7cXkpXf03v6gRMOm8dQIrWh3VhRSm5HJNoOZb2X8Ubc6o-6CDN7OJFZMKlgNuLKT3s3xpY5j7qbvhMANOhAgPpYlDJTR9FHFFFRPFew==",
    "9fd71f6734984febfdbac1ce6d74235abbdc1aa9" -> "v9ugWig2bv7i2CH8EAujRQlLT4Yuju3nd5vi0vjR6Q28HkM9iusvIlSLWKW7PS2mEK1H2mw7U4o7OClB6LlSFKRGymHLQ7_D1OlbnFHYqFlVhFUcG8kILk6o7b1jdRE2sUNgsArgHaWRPu4qKY6LP26ePOzbcIQ5JswgWGUx0ok7LX5z61HRaMVNcH1JWbJXFlOkZ_LooF_TZ3HElwwmhDBwBGzVJgz1glpY31dj2Zc6ms2Ddl2Be5eyay9dj8aESM-NePmgUa_qXcjjRwQlhPaLoopGB1xQuLh-Gj_bYBGnivQMRwFb44NHUlJujEPzpBe7SjC4a1jbOqoXCWGJVQ",
    "9b8abc75385c47955744b39d571e5deb1e188d0b" -> "0gyg_L-Mvf6gsRZWjbsO-x8VKnSNIE74A8ekqLI_7kQddPRKSGLdz-KATVqjrIOs6K97aaAOqqfAcYLcIDN-zfdXqG2jHpbtKKPPULUltfRxQfHT7XH8N8PErTt0ePXXujWEpY2Fto7hr9_gA6BcXwgzhQ9z0x23FcATZGYvIXA3IT1xy0xqWBYfD6lQ5K74ZQ8q0MaMK5AqpgWSmW6TLMfvTuDkX91O0ewfHQEsO5Q_s6JWxDAyDo_3ls0kUoQ61uAt5CPoUs8kcvZLu-Hbv6zPXUOrg1p6GW8Q-yKgBQ9OF1FkHjYVF0VBByDJHB-mroh8PY6ER3lINQB9PRAp2w==",
    "009350c0d3dbc378ee1daf37ddf7f7dbd728a222" -> "vofY0J7vNqKJOEmP3T66cocJ7Z-vNa39_UyvrRkdBDptzop7p8g9JvP25zB1g6LeBSCK6yQzJhhocqqWvaUROpfDl_ChFQGYLs3E4KWgKNPhOBfD6b2hNmGqSMFCxaMnUnT3A0l0YAnYrUDWCfWtMh4KGETNWcKK4SshLznnC5uI0B-Y1M9IlgzC9fXGGTmwEmlH2cURNaqQN0JcGH6SfdQplgEHv_eNlBW4ZLIali225SdD2mQIP_WIkHUahsxbawyRz9BfJ1A_puBYQVb0dwiGCpTq5pFlsoBjoxBF_Om4eNYMhei-207wEJdQruGDqIX5L8ws1rsgMmBgugiGkQ==")

  val googleE = "AQAB"

  def extractIdToken(token: String): Option[LoggedIn] = {
    val parts = token.split('.')
    if (parts.length == 3) {
      implicit val formats = DefaultFormats
      val header = parts(0)
      val headerJwt =
        jparse(new String(decoder.decode(parts(0)))).extract[Header]
      val hash = s"$header.${parts(1)}".getBytes
      encrypt(
        googleN(headerJwt.kid),
        googleE,
        hash,
        decoder.decode(parts(2))
      ) flatMap { t =>
        if (t) {
          val idToken = new String(decoder.decode(parts(1)))
          Option(jparse(idToken).extract[IdToken]) flatMap { idTokenPayload =>
            val user = User(idTokenPayload.sub, idTokenPayload.name, "user")
            CommonUtil.createJwt(user, 5 * 60, 0)
          }
        } else None
      }
    } else {
      None
    }
  }

  def route(config: OauthConfig)(
    implicit system: ActorSystem,
    materializer: ActorMaterializer
  ): Route = get {
    implicit val formats = DefaultFormats
    parameters('code, 'state) { (code, state) =>
      val responseFuture: Future[HttpResponse] =
        Http().singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = "https://www.googleapis.com/oauth2/v4/token",
            entity = HttpEntity(
              MediaTypes.`application/x-www-form-urlencoded`
                .toContentType(HttpCharsets.`UTF-8`),
              "grant_type=authorization_code" +
              "&code=" + code +
              "&client_id=" + config.clientId +
              "&client_secret=" + config.clientSecret +
              "&redirect_uri=" + config.redirectUri
            )
          )
      )
      onComplete(responseFuture) {
        case Success(httpResponse) =>
          if (httpResponse.status.isSuccess) {
            onSuccess(
              httpResponse.entity.dataBytes.runFold(ByteString(""))(_ ++ _)) {
              body =>
                val json = parse(body.utf8String)
                val googleResponse = json.extract[GoogleResponse]
                GoogleOauth.extractIdToken(googleResponse.id_token) match {
                  case Some(loggedIn) =>
                    setCookie(HttpCookie(
                      "X-Token",
                      loggedIn.token,
                      Some(DateTime.now + 24 * 60 * 60 * 1000),
                      path = Some("/"),
                      httpOnly = false))
                    {
                      redirect(state.split("\\|")(1), StatusCodes.TemporaryRedirect)
                    }
                  case _ =>
                    redirect(state.split("\\|")(1), StatusCodes.TemporaryRedirect)
                }
              }
          } else {
            complete("Login failed")
          }
        case Failure(ex) =>
          complete((
            StatusCodes.InternalServerError,
            s"An error occurred: ${ex.getMessage}"))
      }
    }
  }

  def encrypt(n: String, e: String, data: Array[Byte], check: Array[Byte]):
    Option[Boolean] =
  {
    getKey(n, e) flatMap { pubKey =>
      val t = Try {
        val signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(pubKey);
        signature.update(data);
        signature.verify(check);
      }
      t.toOption
    }
  }

  private def getKey(n: String, e: String): Option[PublicKey] = Try {
    val exp =  new BigInteger(1, decoder.decode(e))
    val mod =  new BigInteger(1, decoder.decode(n))
    val publicKeySpec = new RSAPublicKeySpec(mod, exp)
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePublic(publicKeySpec)
  }.toOption

  def sha256(text: String) = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(text.getBytes(StandardCharsets.UTF_8))
  }
}

