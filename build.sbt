Common.appSettings

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "com.github.scopt"       %% "scopt"                 % "3.3.0"
)

lazy val common = (project in file("modules/common"))

lazy val topicCommand =
  (project in file("modules/topic-command")).dependsOn(common)

lazy val discussionCommand =
  (project in file("modules/discussion-command")).dependsOn(common)

lazy val web =
  (project in file("modules/web")).dependsOn(common)

lazy val websocket =
  (project in file("modules/websocket")).dependsOn(common)

lazy val topicQueryBuilder =
  (project in file("modules/topic-query-builder")).dependsOn(common)

lazy val discussionQueryBuilder =
  (project in file("modules/discussion-query-builder")).dependsOn(common)

lazy val root = (project in file("."))
  .enablePlugins(GatlingPlugin)
  .settings(libraryDependencies ++= Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.2.1" % "it",
    "io.gatling"            % "gatling-test-framework"    % "2.2.1" % "it"
  ))
  .aggregate(
    common,
    topicCommand,
    web,
    websocket,
    discussionCommand,
    topicQueryBuilder,
    discussionQueryBuilder
  )
  .dependsOn(
    common,
    topicCommand,
    web,
    websocket,
    discussionCommand,
    topicQueryBuilder,
    discussionQueryBuilder
  )


libraryDependencies ++= Common.commonDependencies

javaOptions in GatlingIt := overrideDefaultJavaOptions("-Xms1024m", "-Xmx2048m")

