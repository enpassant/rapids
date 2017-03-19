import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

class RealGatling extends Simulation {
  val feeder = Iterator.continually(
    Map(
      "userId" -> Random.nextInt(100).toString,
      "blogId" -> Random.nextInt(100).toString,
      "uuid" -> common.CommonUtil.uuid
    )
  )

  val scn = scenario("RealGatling")
    .feed(feeder)
    .exec(
      http("login")
        .post("/login")
        .basicAuth(s"john$${userId}", s"john$${userId}")
        .check(header("X-Token").exists.saveAs("token"))
    )
    .exec(Command.queryBlogs)
    .doIfOrElse(session => session("blogCount").as[Int] >= 200) {
      repeat(50, "commentId") {
        exec { session =>
          val ids = session("discussionIds").as[Seq[String]]
          session.set("discussionUuid",
            ids(scala.util.Random.nextInt(ids.length)))
        }
        .exec(Command.queryComments)
        .repeat(5, "replyId") {
          doIfOrElse(session => session.contains("commentIds")) {
            exec(Command.replyComment)
          } {
            exec(Command.addComment)
          }
        }
        .pause(1)
      }
    } {
      exec(Command.createBlog)
    }

  val httpConf = http
    .baseURL("http://localhost:8080")
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .doNotTrackHeader("1")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .acceptEncodingHeader("gzip, deflate")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  setUp(
    scn.inject(rampUsersPerSec(1) to(15) during(50 seconds) randomized)
  ).protocols(httpConf)

  object Command {
    val queryBlogs = http("queryBlogs")
      .get("/blog")
      .header("Authorization", s"Bearer $${token}")
      .header(HttpHeaderNames.Accept, HttpHeaderValues.ApplicationJson)
      .check(status.is(session => 200))
      .check(jsonPath("$.blogs[*]._id").count.optional.saveAs("blogCount"))
      .check(jsonPath("$.blogs[*].discussions[*].id").findAll.optional
        .saveAs("discussionIds"))

    val queryComments = http("queryComments")
      .get(s"/discussion/$${discussionIds.random()}")
      .header("Authorization", s"Bearer $${token}")
      .header(HttpHeaderNames.Accept, HttpHeaderValues.ApplicationJson)
      .check(status.is(session => 200))
      .check(jsonPath("$._id").exists.saveAs("discussionUuid"))
      .check(jsonPath("$..commentId").findAll.optional
        .saveAs("commentIds"))

    val createBlog = http("createBlog")
      .post(s"/commands/blog/$${uuid}")
      .header("Authorization", s"Bearer $${token}")
      .body(StringBody(s"""{"_t":"CreateBlog", "title": "Blog $${userId} $${blogId}", "content": "My $${userId} $${blogId}. blog", "loggedIn": ""}"""))
      .check(status.is(session => 200))

    val addComment = http("addComment")
      .post(s"/commands/discussion/$${discussionUuid}")
      .header("Authorization", s"Bearer $${token}")
      .body(StringBody(s"""{"_t":"AddComment", "id": "$${uuid}", "content": "$${commentId}. megjegyzés", "loggedIn": ""}"""))
      .check(status.is(session => 200))

    val replyComment = http("replyComment")
      .post(s"/commands/discussion/$${discussionUuid}")
      .header("Authorization", s"Bearer $${token}")
      .body(StringBody(s"""{"_t":"ReplyComment", "id": "$${uuid}", "parentId": "$${commentIds.random()}", "content": "$${replyId}. válasz", "loggedIn": ""}"""))
      .check(status.is(session => 200))
  }
}
