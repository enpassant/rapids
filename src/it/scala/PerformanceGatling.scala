import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

class PerformanceGatling extends Simulation {
  val feeder = Iterator.continually(
    Map(
      "discId" -> Random.nextInt(10).toString,
      "parentId" -> Random.nextInt(100).toString
    )
  )

  val scn = scenario("SimpleGatling")
    .exec(
      http("login").post("/login").basicAuth("john", "john")
      .check(
        header("X-Token").exists.saveAs("token"))
    )
    //.feed(feeder)
    .repeat(50, "blogId") {
      exec(Command.createBlog)
    }
    .pause(900 milliseconds)
    .repeat(50, "blogId") {
      repeat(40, "commentId") {
        exec(Command.addComment)
        .repeat(40, "replyId") {
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
    scn.inject(atOnceUsers(1))
  ).protocols(httpConf)

  object Command {
    val createBlog = http("CreateBlog")
      .post(s"/commands/blog/$${blogId}")
      .header("Authorization", s"Bearer $${token}")
      .body(StringBody(s"""{"_t":"CreateBlog", "title": "Blog $${blogId}", "content": "My $${blogId}. blog", "loggedIn": ""}"""))
      .check(status.is(session => 200))

    val addComment = http("AddComment")
      .post(s"/commands/discussion/disc-$${blogId}")
      .header("Authorization", s"Bearer $${token}")
      .body(StringBody(s"""{"_t":"AddComment", "id": "$${blogId}-$${commentId}", "content": "$${commentId}. megjegyzés", "loggedIn": ""}"""))
      .check(status.is(session => 200))

    val replyComment = http("ReplyComment")
      .post(s"/commands/discussion/disc-$${blogId}")
      .header("Authorization", s"Bearer $${token}")
      .body(StringBody(s"""{"_t":"ReplyComment", "id": "$${blogId}-$${commentId}-$${replyId}", "parentId": "$${blogId}-$${commentId}", "content": "$${replyId}. válasz", "loggedIn": ""}"""))
      .check(status.is(session => 200))
  }
}
