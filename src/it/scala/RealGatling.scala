import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

class RealGatling extends Simulation {
  val feeder = Iterator.continually(
    Map(
      "userId" -> Random.nextInt(100).toString,
      "discId" -> Random.nextInt(10).toString,
      "parentId" -> Random.nextInt(100).toString
    )
  )

  val scn = scenario("RealGatling")
    .feed(feeder)
    .exec(
      http("login").post("/login").basicAuth(s"john$${userId}", s"john$${userId}")
      .check(
        header("X-Token").exists.saveAs("token"))
    )
    .repeat(1, "blogId") {
      exec(Command.createBlog)
    }
    .pause(900 milliseconds)
    .repeat(1, "blogId") {
      repeat(5, "commentId") {
        exec(Command.addComment)
        .pause(10 milliseconds)
        .repeat(5, "replyId") {
          exec(Command.replyComment)
        }
      }
    }

  val httpConf = http
    .baseURL("http://localhost:8080")
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  setUp(
    scn.inject(rampUsersPerSec(1) to(100) during(50 seconds) randomized)
  ).protocols(httpConf)

  object Command {
    val createBlog = http("CreateBlog")
      .post(s"/commands/blog/$${userId}$${blogId}")
      .header("Authorization", s"Bearer $${token}")
      .body(StringBody(s"""{"_t":"CreateBlog", "title": "Blog $${userId} $${blogId}", "content": "My $${userId} $${blogId}. blog", "loggedIn": ""}"""))
      .check(status.is(session => 200))

    val addComment = http("AddComment")
      .post(s"/commands/discussion/disc-$${userId}$${blogId}")
      .header("Authorization", s"Bearer $${token}")
      .body(StringBody(s"""{"_t":"AddComment", "id": "$${userId}$${blogId}-$${commentId}", "content": "$${commentId}. megjegyzés", "loggedIn": ""}"""))
      .check(status.is(session => 200))

    val replyComment = http("ReplyComment")
      .post(s"/commands/discussion/disc-$${userId}$${blogId}")
      .header("Authorization", s"Bearer $${token}")
      .body(StringBody(s"""{"_t":"ReplyComment", "id": "$${userId}$${blogId}-$${commentId}-$${replyId}", "parentId": "$${userId}$${blogId}-$${commentId}", "content": "$${replyId}. válasz", "loggedIn": ""}"""))
      .check(status.is(session => 200))
  }
}
