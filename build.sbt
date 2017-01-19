name := """rapids"""

version := "1.0"

scalaVersion := "2.11.8"

val akkaVersion = "2.4.16"

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "com.github.scopt"       %% "scopt"                 % "3.3.0",
  "com.typesafe.akka"      %% "akka-actor"            % akkaVersion,
  "com.typesafe.akka"      %% "akka-persistence"      % akkaVersion,
  "com.typesafe.akka"      %% "akka-stream"           % akkaVersion,
  "com.typesafe.akka"      %% "akka-stream-kafka"     % "0.13",
  "com.typesafe.akka"      %% "akka-testkit"          % akkaVersion   % "test",
  "com.typesafe.akka"      %% "akka-slf4j"            % akkaVersion,
  "org.json4s"             %% "json4s-jackson"        % "3.2.10",
  "org.json4s"             %% "json4s-ext"            % "3.2.10",
  "com.github.salat"       %% "salat"                 % "1.10.0",
  "org.mongodb"            %% "casbah"                % "3.1.1",
  "com.github.scullxbones" %% "akka-persistence-mongo-casbah" % "1.3.7"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-feature",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

