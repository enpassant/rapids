import sbt._
import Keys._

object Common {
	def appName = "rapids"

	val akkaVersion = "2.5.11"

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
    "com.github.enpassant" %% "ickenham" % "1.4.1",
    "com.github.enpassant" %% "ickenham-json4s" % "1.4.1"
  )

	val commonDependencies = Seq(
		"com.typesafe.akka"      %% "akka-actor"            % akkaVersion,
		"com.typesafe.akka"      %% "akka-persistence"      % akkaVersion,
		"com.typesafe.akka"      %% "akka-stream"           % akkaVersion,
		"com.typesafe.akka"      %% "akka-stream-kafka"     % "0.20",
		"com.typesafe.akka"      %% "akka-testkit"          % akkaVersion   % "test",
		"com.typesafe.akka"      %% "akka-slf4j"            % akkaVersion,
		"com.typesafe.akka"      %% "akka-http"             % "10.0.6",
		"org.json4s"             %% "json4s-jackson"        % "3.5.3",
		"org.json4s"             %% "json4s-ext"            % "3.5.3",
		"org.json4s"             %% "json4s-mongo"          % "3.5.3",
		"org.mongodb"            %% "casbah"                % "3.1.1",
    "com.vladsch.flexmark"    % "flexmark-all"          % "0.19.3",
    "com.github.ironfish"    %% "akka-persistence-mongo" % "1.0.0-SNAPSHOT" % "compile"
	)
}

