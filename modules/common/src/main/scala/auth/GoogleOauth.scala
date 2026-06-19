package auth

import common._
import config._

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.security._
import java.security.spec.RSAPublicKeySpec
import org.apache.pekko.actor._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers._
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream._
import org.json4s._
import org.json4s.jackson.JsonMethods.{parse => jparse}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import org.apache.pekko.util.ByteString
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
  nonce: Option[String],
  iss: String,
  iat: Long,
  exp: Long,
  name: String,
  picture: Option[String],
  given_name: Option[String],
  family_name: Option[String],
  locale: Option[String]
)

case class GoogleKeys(keys: List[GoogleKey])
case class GoogleKey(
  kty: String,
  alg: String,
  use: String,
  kid: String,
  n: String,
  e: String
)

object GoogleOauth {
  val encoder = Base64.getUrlEncoder()
  val decoder = Base64.getUrlDecoder()

  val googleE = "AQAB"

  def extractIdToken(token: String, keys: GoogleKeys): Option[LoggedIn] = {
    val parts = token.split('.')
    if (parts.length == 3) {
      implicit val formats = DefaultFormats
      val header = parts(0)
      val headerJwt =
        jparse(new String(decoder.decode(parts(0)))).extract[Header]
      val hash = s"$header.${parts(1)}".getBytes
      val keyOpt = keys.keys.find { key => key.kid == headerJwt.kid }
      keyOpt flatMap { key =>
        encrypt(
          key.n,
          key.e,
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
      }
    } else {
      None
    }
  }

  def getGoogleKeys(
    implicit system: ActorSystem,
    materializer: Materializer): Future[GoogleKeys] =
  {
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(
          method = HttpMethods.GET,
          uri = "https://www.googleapis.com/oauth2/v3/certs"
        ))
    responseFuture flatMap { response =>
      if (response.status.isSuccess) {
        val bodyF = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
        bodyF map { body =>
          implicit val formats = DefaultFormats
          val json = parse(body.utf8String)
          json.extract[GoogleKeys]
        }
      } else {
        Future.failed(new RuntimeException("failed"))
      }
    }
  }

  def route(config: OauthConfig)(
    implicit system: ActorSystem,
    materializer: Materializer
  ): Route = get {
    implicit val formats = DefaultFormats
    parameters(Symbol("code"), Symbol("state")) { (code, state) =>
      val responseFuture: Future[HttpResponse] =
        Http().singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = "https://www.googleapis.com/oauth2/v4/token",
            entity = HttpEntity(
              MediaTypes.`application/x-www-form-urlencoded`
                .toContentType,
              "grant_type=authorization_code" +
              "&code=" + code +
              "&client_id=" + config.clientId +
              "&client_secret=" + config.clientSecret +
              "&redirect_uri=" + config.redirectUri
            )
          )
      )
      val responses = for {
        response <- responseFuture
        keys <- getGoogleKeys
      } yield (response, keys)
      onComplete(responses) {
        case Success((httpResponse, keys)) =>
          if (httpResponse.status.isSuccess) {
            onSuccess(
              httpResponse.entity.dataBytes.runFold(ByteString(""))(_ ++ _)) {
              body =>
                val json = parse(body.utf8String)
                val googleResponse = json.extract[GoogleResponse]
                GoogleOauth.extractIdToken(googleResponse.id_token, keys) match {
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

