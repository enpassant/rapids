Common.appSettings

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "com.github.scopt"       %% "scopt"                 % "3.3.0"
)

lazy val common = (project in file("modules/common"))

lazy val topicCommand = (project in file("modules/topic-command"))
  .dependsOn(common)

lazy val web = (project in file("modules/web"))
  .dependsOn(common)

lazy val client = (project in file("modules/client"))
  .dependsOn(common)

lazy val root = (project in file("."))
  .aggregate(common, topicCommand, web, client)
  .dependsOn(common, topicCommand, web, client)


libraryDependencies ++= Common.commonDependencies

