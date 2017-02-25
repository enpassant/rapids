import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

class SimpleGatling extends Simulation {
  val scn = scenario("SimpleGatling")
    .repeat(1, "blogId") {
      exec(Command.createBlog("simple"))
    }
    .pause(900 milliseconds)
    .exec(Command.addComment("simple", "c1"))
    .exec(Command.replyComment("simple", "c1", "r1"))
    .exec(Command.addComment("simple", "c2"))
    .exec(Command.replyComment("simple", "r1", "r2"))
    .exec(Command.replyComment("simple", "r1", "r3"))
    .exec(Command.replyComment("simple", "r2", "r4"))

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
    def createBlog(blogId: String) = http("CreateBlog")
      .post(s"/commands/blog/${blogId}")
      .body(StringBody(s"""{"_t":"CreateBlog", "title": "Blog ${blogId}", "content": "My ${blogId}. blog"}"""))
      .check(status.is(session => 200))

    def addComment(blogId: String, commentId: String) = http("AddComment")
      .post(s"/commands/discussion/disc-${blogId}")
      .body(StringBody(s"""{"_t":"AddComment", "id": "${commentId}", "content": "${commentId}. megjegyzés"}"""))
      .check(status.is(session => 200))

    def replyComment(blogId: String, commentId: String, replyId: String) = {
      http("ReplyComment")
      .post(s"/commands/discussion/disc-${blogId}")
      .body(StringBody(s"""{"_t":"ReplyComment", "id": "${replyId}", "parentId": "${commentId}", "content": "${replyId}. válasz"}"""))
      .check(status.is(session => 200))
    }
  }
}
