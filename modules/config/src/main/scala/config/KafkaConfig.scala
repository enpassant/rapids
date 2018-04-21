package config

import com.mongodb.casbah.Imports._
import com.typesafe.config.ConfigFactory

object OauthConfig {
  def get = {
    val config = ConfigFactory.load("oauth")
    OauthConfig(
      config.getString("clientId"),
      config.getString("clientSecret"),
      config.getString("redirectUri"))
  }
}

case class OauthConfig(
  clientId: String,
  clientSecret: String,
  redirectUri: String)

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

object DevelopmentKafkaConfig extends KafkaConfig("localhost:9094")
object DevelopmentDiscussionQueryConfig
  extends DiscussionQueryConfig("mongodb://localhost/blog")
object DevelopmentBlogQueryConfig
  extends BlogQueryConfig("mongodb://localhost/blog", "Blogok")
object DevelopmentBlogQueryBuilderConfig
  extends BlogQueryBuilderConfig("mongodb://localhost/blog")
object DevelopmentDiscussionQueryBuilderConfig
  extends DiscussionQueryBuilderConfig("mongodb://localhost/blog")

object ProductionKafkaConfig extends KafkaConfig("kafka:9092")
object ProductionDiscussionQueryConfig
  extends DiscussionQueryConfig("mongodb://mongodb/blog")
object ProductionBlogQueryConfig
  extends BlogQueryConfig("mongodb://mongodb/blog", "Blogok")
object ProductionBlogQueryBuilderConfig
  extends BlogQueryBuilderConfig("mongodb://mongodb/blog")
object ProductionDiscussionQueryBuilderConfig
  extends DiscussionQueryBuilderConfig("mongodb://mongodb/blog")
