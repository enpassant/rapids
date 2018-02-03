package blog

import config._

import com.mongodb.casbah.Imports._

class BlogStoreDB(config: BlogQueryBuilderConfig) extends BlogStore {
  val collection = config.mongoClient.getDB("blog")("blog")

  def insert(
    id: String,
    userId: String,
    userName: String,
    title: String,
    content: String,
    htmlContent: String) =
  {
    collection.insert(MongoDBObject(
      "_id" -> id,
      "userId" -> userId,
      "userName" -> userName,
      "title" -> title,
      "content" -> content,
      "htmlContent" -> htmlContent,
      "discussions" -> Seq()))
  }

  def existsBlog(id: String) = {
    collection.findOne(MongoDBObject("_id" -> id)).isDefined
  }

  def update(
    id: String,
    title: String,
    content: String,
    htmlContent: String) =
  {
    collection.update(
      MongoDBObject("_id" -> id),
      MongoDBObject("$set" -> MongoDBObject(
        "title" -> title,
        "content" -> content,
        "htmlContent" -> htmlContent)))
  }

  def addDiscussion(
    blogId: String,
    id: String,
    userId: String, userName: String, title: String) =
  {
    collection.update(
      MongoDBObject("_id" -> blogId),
      $push("discussions" ->
        MongoDBObject(
          "id" -> id,
          "userId" -> userId,
          "userName" -> userName,
          "title" -> title)))
  }
}
