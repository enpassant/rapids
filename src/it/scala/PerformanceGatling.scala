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
      .body(StringBody(s"""{"_t":"CreateBlog", "title": "Blog $${blogId}", "content": "My $${blogId}. blog"}"""))
      .check(status.is(session => 200))

    val addComment = http("AddComment")
      .post(s"/commands/discussion/disc-$${blogId}")
      .body(StringBody(s"""{"_t":"AddComment", "id": "$${blogId}-$${commentId}", "title": "Megjegyzés $${commentId}", "content": "$${commentId}. megjegyzés"}"""))
      .check(status.is(session => 200))

    val replyComment = http("ReplyComment")
      .post(s"/commands/discussion/disc-$${blogId}")
      .body(StringBody(s"""{"_t":"ReplyComment", "id": "$${blogId}-$${commentId}-$${replyId}", "parentId": "$${blogId}-$${commentId}", "title": "Válasz $${replyId}", "content": "$${replyId}. válasz"}"""))
      .check(status.is(session => 200))
  }
}