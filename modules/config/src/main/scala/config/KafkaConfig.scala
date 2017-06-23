package config

case class KafkaConfig(server: String)

object ProductionKafkaConfig extends KafkaConfig("localhost:9092")
