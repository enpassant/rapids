Common.appSettings

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "com.github.scopt"       %% "scopt"                 % "3.3.0"
)

lazy val common = (project in file("modules/common"))

lazy val webCommon = (project in file("modules/web-common"))
  .settings(libraryDependencies ++= Common.webDependencies)
  .aggregate(common)
  .dependsOn(common)

lazy val blogCommand = (project in file("modules/blog-command"))
  .dependsOn(common)

lazy val discussionCommand = (project in file("modules/discussion-command"))
  .dependsOn(common)

lazy val web = (project in file("modules/web"))
  .aggregate(webCommon)
  .dependsOn(webCommon)

lazy val blogQueryBuilder = (project in file("modules/blog-query-builder"))
  .dependsOn(common)

lazy val discussionQueryBuilder =
  (project in file("modules/discussion-query-builder"))
    .dependsOn(common)

lazy val blogQuery = (project in file("modules/blog-query"))
  .aggregate(webCommon)
  .dependsOn(webCommon)

lazy val discussionQuery = (project in file("modules/discussion-query"))
  .aggregate(webCommon)
  .dependsOn(webCommon)

lazy val monitor = (project in file("modules/monitor"))
  .aggregate(webCommon)
  .dependsOn(webCommon)

lazy val root = (project in file("."))
  .enablePlugins(GatlingPlugin)
  .settings(libraryDependencies ++= Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.2.1" % "it",
    "io.gatling"            % "gatling-test-framework"    % "2.2.1" % "it"
  ))
  .aggregate(
    common,
    webCommon,
    blogCommand,
    web,
    discussionCommand,
    blogQueryBuilder,
    discussionQueryBuilder,
    blogQuery,
    discussionQuery,
    monitor
  )
  .dependsOn(
    common,
    webCommon,
    blogCommand,
    web,
    discussionCommand,
    blogQueryBuilder,
    discussionQueryBuilder,
    blogQuery,
    discussionQuery,
    monitor
  )


libraryDependencies ++= Common.commonDependencies

javaOptions in GatlingIt := overrideDefaultJavaOptions("-Xms1024m", "-Xmx2048m")

