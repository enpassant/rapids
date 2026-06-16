import sbt._
import Keys._

object Common {
  def appName = "rapids"

  val akkaVersion = "2.6.20"
  val json4sVersion = "3.6.12"
  val mongoDriverVersion = "4.11.1" 

  // Common settings for every project
  def settings (theName: String) = Seq(
    name := theName,
    organization := "enpassant",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.13.16",
    scalacOptions ++= Seq(
      "-unchecked",
      "-feature",
      "-deprecation",
      "-Xlint",
      "-Ywarn-dead-code",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8"
    ),
    resolvers += Resolver.sonatypeCentralSnapshots
  )
  // Settings for the app, i.e. the root project
  val appSettings = settings(appName)
  // Settings for every module, i.e. for every subproject
  def moduleSettings (module: String) = settings(module) ++: Seq(
    Test / javaOptions += s"-Dconfig.resource=application.conf"
  )
  // Settings for every service, i.e. for admin and web subprojects

  val webDependencies = Seq(
    "com.github.enpassant" %% "ickenham" % "1.5.0",
    "com.github.enpassant" %% "ickenham-json4s" % "1.5.0"
  )

  val commonDependencies = Seq(
    "com.typesafe.akka"      %% "akka-actor"            % akkaVersion,
    "com.typesafe.akka"      %% "akka-persistence"      % akkaVersion,
    "com.typesafe.akka"      %% "akka-stream"           % akkaVersion,
    "com.typesafe.akka"      %% "akka-stream-kafka"     % "2.1.1",
    "com.typesafe.akka"      %% "akka-testkit"          % akkaVersion   % "test",
    "com.typesafe.akka"      %% "akka-slf4j"            % akkaVersion,
    "com.typesafe.akka"      %% "akka-persistence-query" % akkaVersion,
    "com.typesafe.akka"      %% "akka-http"             % "10.2.10",
    "org.json4s"             %% "json4s-jackson"        % json4sVersion,
    "org.json4s"             %% "json4s-ext"            % json4sVersion,
    "org.json4s"             %% "json4s-mongo"          % json4sVersion,
    "org.mongodb" % "mongodb-driver-core"          % mongoDriverVersion,
    "org.mongodb" % "mongodb-driver-reactivestreams" % mongoDriverVersion,
    "org.mongodb" % "bson"                         % mongoDriverVersion,
    "org.mongodb.scala"      %% "mongo-scala-driver"    % "4.11.1",
    "com.vladsch.flexmark"    % "flexmark-all"          % "0.19.3",
    "ch.qos.logback" % "logback-classic" % "1.3.14",
    "com.github.scullxbones" %% "akka-persistence-mongo-rxmongo" % "3.0.8" % "compile" 
  )
}

