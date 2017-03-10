import sbt._
import Keys._

object Common {
	def appName = "rapids"

	val akkaVersion = "2.4.16"

	// Common settings for every project
	def settings (theName: String) = Seq(
		name := theName,
		organization := "enpassant",
		version := "1.0-SNAPSHOT",
		scalaVersion := "2.11.8",
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
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots")
    )
	)
	// Settings for the app, i.e. the root project
	val appSettings = settings(appName)
	// Settings for every module, i.e. for every subproject
	def moduleSettings (module: String) = settings(module) ++: Seq(
		javaOptions in Test += s"-Dconfig.resource=application.conf"
	)
	// Settings for every service, i.e. for admin and web subprojects

  val webDependencies = Seq(
    "com.github.spullara.mustache.java" % "compiler" % "0.9.4",
    "me.lessis" %% "fixie-grips-json4s" % "0.1.0"
  )

	val commonDependencies = Seq(
		"com.typesafe.akka"      %% "akka-actor"            % akkaVersion,
		"com.typesafe.akka"      %% "akka-persistence"      % akkaVersion,
		"com.typesafe.akka"      %% "akka-stream"           % akkaVersion,
		"com.typesafe.akka"      %% "akka-stream-kafka"     % "0.13",
		"com.typesafe.akka"      %% "akka-testkit"          % akkaVersion   % "test",
		"com.typesafe.akka"      %% "akka-slf4j"            % akkaVersion,
		"com.typesafe.akka"      %% "akka-http"             % "10.0.4",
		"org.json4s"             %% "json4s-jackson"        % "3.2.10",
		"org.json4s"             %% "json4s-ext"            % "3.2.10",
		"org.json4s"             %% "json4s-mongo"          % "3.2.10",
		"com.github.salat"       %% "salat"                 % "1.10.0",
		"org.mongodb"            %% "casbah"                % "3.1.1",
    "com.github.ironfish"    %% "akka-persistence-mongo" % "1.0.0-SNAPSHOT" % "compile"
	)
}

