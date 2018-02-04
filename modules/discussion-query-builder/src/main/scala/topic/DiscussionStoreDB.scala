package discussion

import blog._
import config._

import com.mongodb.casbah.Imports._

class DiscussionStoreDB(config: DiscussionQueryBuilderConfig)
  extends DiscussionStore
{
  val collDiscussion = config.mongoClient.getDB("blog")("discussion")

  def insert(discussionStarted: DiscussionStarted) = {
    collDiscussion.insert(
      MongoDBObject(
        "_id" -> discussionStarted.id,
        "userId" -> discussionStarted.userId,
        "userName" -> discussionStarted.userName,
        "blogId" -> discussionStarted.blogId,
        "title" -> discussionStarted.title,
        "comments" -> List()
    ))
  }

  def addComment(commentAdded: CommentAdded) = {
    collDiscussion.update(
      MongoDBObject("_id" -> commentAdded.id),
      $push("comments" -> MongoDBObject(
        "commentId" -> commentAdded.commentId,
        "userId" -> commentAdded.userId,
        "userName" -> commentAdded.userName,
        "content" -> commentAdded.content,
        "comments" -> List()
    )))
  }

  def replayComment(commentReplied: CommentReplied) = {
    val pos = commentReplied.path.tail.foldLeft("comments") {
      (p, i) => s"comments.$i.$p"
    }
    collDiscussion.update(
      MongoDBObject("_id" -> commentReplied.id),
      $push(pos -> MongoDBObject(
        "commentId" -> commentReplied.commentId,
        "userId" -> commentReplied.userId,
        "userName" -> commentReplied.userName,
        "content" -> commentReplied.content,
        "comments" -> List()
    )))
  }
}

