package discussion

import config._

import com.mongodb.casbah.Imports._

class DiscussionStoreDB(config: DiscussionQueryBuilderConfig)
  extends DiscussionStore
{
  val collDiscussion = config.mongoClient.getDB("blog")("discussion")

  def insert(
    id: String,
    userId: String,
    userName: String,
    blogId: String,
    title: String) =
  {
    collDiscussion.insert(
      MongoDBObject(
        "_id" -> id,
        "userId" -> userId,
        "userName" -> userName,
        "blogId" -> blogId,
        "title" -> title,
        "comments" -> List()
    ))
  }

  def addComment(
    key: String,
    id: String,
    userId: String,
    userName: String,
    content: String) =
  {
    collDiscussion.update(
      MongoDBObject("_id" -> key),
      $push("comments" -> MongoDBObject(
        "commentId" -> id,
        "userId" -> userId,
        "userName" -> userName,
        "content" -> content,
        "comments" -> List()
    )))
  }

  def replayComment(
    key: String,
    pos: String,
    id: String,
    userId: String,
    userName: String,
    content: String) =
  {
    collDiscussion.update(
      MongoDBObject("_id" -> key),
      $push(pos -> MongoDBObject(
        "commentId" -> id,
        "userId" -> userId,
        "userName" -> userName,
        "content" -> content,
        "comments" -> List()
    )))
  }
}

