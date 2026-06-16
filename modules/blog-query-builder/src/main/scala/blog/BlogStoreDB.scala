package blog

import config._

import org.mongodb.scala._
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import scala.concurrent.Await
import scala.concurrent.duration._

class BlogStoreDB(config: BlogQueryBuilderConfig) extends BlogStore {
  val collection = config.mongoClient.getDatabase("blog")
    .getCollection("blog")

  def insert(blogCreated: BlogCreated, htmlContent: String): Unit = {
    val doc = Document(
      "_id" -> blogCreated.id,
      "userId" -> blogCreated.userId,
      "userName" -> blogCreated.userName,
      "title" -> blogCreated.title,
      "content" -> blogCreated.content,
      "htmlContent" -> htmlContent,
      "discussions" -> BsonArray()
    )
    Await.result(collection.insertOne(doc).toFuture(), 10.seconds)
  }

  def existsBlog(id: String) = {
    Await.result(collection.find(equal("_id", id)).first().toFuture(), 10.seconds) != null
  }

  def update(blogModified: BlogModified, htmlContent: String): Unit = {
    Await.result(
      collection.updateOne(
        equal("_id", blogModified.id),
        combine(
          set("title", blogModified.title),
          set("content", blogModified.content),
          set("htmlContent", htmlContent)
        )
      ).toFuture(),
      10.seconds
    )
  }

  def addDiscussion(discussionStarted: DiscussionStarted): Unit = {
    val doc = Document(
      "id" -> discussionStarted.id,
      "userId" -> discussionStarted.userId,
      "userName" -> discussionStarted.userName,
      "title" -> discussionStarted.title
    )
    Await.result(
      collection.updateOne(
        equal("_id", discussionStarted.blogId),
        push("discussions", doc)
      ).toFuture(),
      10.seconds
    )
  }
}
