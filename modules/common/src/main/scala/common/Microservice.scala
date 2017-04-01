package common

import com.typesafe.config.ConfigFactory

trait Microservice {
  val config = ConfigFactory.load
  val kafkaServer = config.getString("microservice.kafka.server")
}
