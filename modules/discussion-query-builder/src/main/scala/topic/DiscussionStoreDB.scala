package discussion

import blog._
import config._

import org.mongodb.scala._
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import scala.concurrent.Await
import scala.concurrent.duration._

class DiscussionStoreDB(config: DiscussionQueryBuilderConfig)
  extends DiscussionStore
{
  val collDiscussion = config.mongoClient.getDatabase("blog")
    .getCollection("discussion")

  def insert(discussionStarted: DiscussionStarted) = {
    val doc = Document(
      "_id" -> discussionStarted.id,
      "userId" -> discussionStarted.userId,
      "userName" -> discussionStarted.userName,
      "blogId" -> discussionStarted.blogId,
      "title" -> discussionStarted.title,
      "comments" -> BsonArray()
    )
    Await.result(collDiscussion.insertOne(doc).toFuture(), 10.seconds)
  }

  def addComment(commentAdded: CommentAdded, htmlContent: String) = {
    val doc = Document(
      "commentId" -> commentAdded.commentId,
      "userId" -> commentAdded.userId,
      "userName" -> commentAdded.userName,
      "content" -> commentAdded.content,
      "htmlContent" -> htmlContent,
      "comments" -> BsonArray()
    )
    Await.result(
      collDiscussion.updateOne(equal("_id", commentAdded.id), push("comments", doc)).toFuture(),
      10.seconds
    )
  }

  def replayComment(commentReplied: CommentReplied, htmlContent: String) = {
    val pos = commentReplied.path.tail.foldLeft("comments") {
      (p, i) => s"$p.$i.comments"
    }
    val doc = Document(
      "commentId" -> commentReplied.commentId,
      "userId" -> commentReplied.userId,
      "userName" -> commentReplied.userName,
      "content" -> commentReplied.content,
      "htmlContent" -> htmlContent,
      "comments" -> BsonArray()
    )
    Await.result(
      collDiscussion.updateOne(equal("_id", commentReplied.id), push(pos, doc)).toFuture(),
      10.seconds
    )
  }
}

