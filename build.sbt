Common.appSettings

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "com.github.scopt"       %% "scopt"                 % "3.3.0"
)

lazy val common = (project in file("modules/common"))

lazy val blogCommand =
  (project in file("modules/blog-command")).dependsOn(common)

lazy val discussionCommand =
  (project in file("modules/discussion-command")).dependsOn(common)

lazy val web =
  (project in file("modules/web")).dependsOn(common)

lazy val websocket =
  (project in file("modules/websocket")).dependsOn(common)

lazy val blogQueryBuilder =
  (project in file("modules/blog-query-builder")).dependsOn(common)

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
    blogCommand,
    web,
    websocket,
    discussionCommand,
    blogQueryBuilder,
    discussionQueryBuilder
  )
  .dependsOn(
    common,
    blogCommand,
    web,
    websocket,
    discussionCommand,
    blogQueryBuilder,
    discussionQueryBuilder
  )


libraryDependencies ++= Common.commonDependencies

javaOptions in GatlingIt := overrideDefaultJavaOptions("-Xms1024m", "-Xmx2048m")

