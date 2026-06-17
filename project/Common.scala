import sbt._
import Keys._

object Common {
  def appName = "rapids"

  val pekkoVersion = "1.5.0"
  val pekkoHttpVersion = "1.3.0"
  val json4sVersion = "3.6.12"
  val mongoDriverVersion = "5.6.5"

  // Common settings for every project
  def settings (theName: String) = Seq(
    name := theName,
    organization := "enpassant",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.13.18",
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
    resolvers += Resolver.sonatypeCentralSnapshots,
    libraryDependencySchemes += "com.github.luben" % "zstd-jni" % "always"
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
    "org.apache.pekko"       %% "pekko-actor"            % pekkoVersion,
    "org.apache.pekko"       %% "pekko-persistence"      % pekkoVersion,
    "org.apache.pekko"       %% "pekko-stream"           % pekkoVersion,
    "org.apache.pekko"       %% "pekko-connectors-kafka" % "1.1.0",
    "org.apache.pekko"       %% "pekko-testkit"          % pekkoVersion   % "test",
    "org.apache.pekko"       %% "pekko-slf4j"            % pekkoVersion,
    "org.apache.pekko"       %% "pekko-persistence-query" % pekkoVersion,
    "org.apache.pekko"       %% "pekko-http"             % pekkoHttpVersion,
    "org.json4s"             %% "json4s-jackson"        % json4sVersion,
    "org.json4s"             %% "json4s-ext"            % json4sVersion,
    "org.json4s"             %% "json4s-mongo"          % json4sVersion,
    "org.mongodb" % "mongodb-driver-core"          % mongoDriverVersion,
    "org.mongodb" % "mongodb-driver-reactivestreams" % mongoDriverVersion,
    "org.mongodb" % "bson"                         % mongoDriverVersion,
    "org.mongodb.scala"      %% "mongo-scala-driver"    % mongoDriverVersion,
    "com.vladsch.flexmark"    % "flexmark-all"          % "0.19.3",
    "ch.qos.logback" % "logback-classic" % "1.3.14",
    "com.github.scullxbones" %% "pekko-persistence-mongodb" % "1.5.0"
  )
}

