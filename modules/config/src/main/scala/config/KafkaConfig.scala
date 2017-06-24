package config

import com.mongodb.casbah.Imports._

trait MongoClientConfig {
  def mongoDbUri: String
  val mongoClient = MongoClient(MongoClientURI(mongoDbUri))
}

case class KafkaConfig(server: String)

case class BlogQueryConfig(mongoDbUri: String, title: String)
  extends MongoClientConfig
case class DiscussionQueryConfig(mongoDbUri: String)
  extends MongoClientConfig
case class BlogQueryBuilderConfig(mongoDbUri: String)
  extends MongoClientConfig
case class DiscussionQueryBuilderConfig(mongoDbUri: String)
  extends MongoClientConfig

object ProductionKafkaConfig extends KafkaConfig("localhost:9092")
object ProductionDiscussionQueryConfig
  extends DiscussionQueryConfig("mongodb://localhost/blog")
object ProductionBlogQueryConfig
  extends BlogQueryConfig("mongodb://localhost/blog", "Blogok")
object ProductionBlogQueryBuilderConfig
  extends BlogQueryBuilderConfig("mongodb://localhost/blog")
object ProductionDiscussionQueryBuilderConfig
  extends DiscussionQueryBuilderConfig("mongodb://localhost/blog")
