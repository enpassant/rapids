package blog

import config._

import com.mongodb.casbah.Imports._

class BlogStoreDB(config: BlogQueryBuilderConfig) extends BlogStore {
  val collection = config.mongoClient.getDB("blog")("blog")

  def insert(blogCreated: BlogCreated, htmlContent: String) {
    collection.insert(MongoDBObject(
      "_id" -> blogCreated.id,
      "userId" -> blogCreated.userId,
      "userName" -> blogCreated.userName,
      "title" -> blogCreated.title,
      "content" -> blogCreated.content,
      "htmlContent" -> htmlContent,
      "discussions" -> Seq()))
  }

  def existsBlog(id: String) = {
    collection.findOne(MongoDBObject("_id" -> id)).isDefined
  }

  def update(blogModified: BlogModified, htmlContent: String) {
    collection.update(
      MongoDBObject("_id" -> blogModified.id),
      MongoDBObject("$set" -> MongoDBObject(
        "title" -> blogModified.title,
        "content" -> blogModified.content,
        "htmlContent" -> htmlContent)))
  }

  def addDiscussion(discussionStarted: DiscussionStarted) {
    collection.update(
      MongoDBObject("_id" -> discussionStarted.blogId),
      $push("discussions" ->
        MongoDBObject(
          "id" -> discussionStarted.id,
          "userId" -> discussionStarted.userId,
          "userName" -> discussionStarted.userName,
          "title" -> discussionStarted.title)))
  }
}
