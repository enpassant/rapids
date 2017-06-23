package common

import com.typesafe.config.ConfigFactory

trait Microservice {
  val config = ConfigFactory.load
}
