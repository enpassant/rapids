import com.typesafe.sbt.packager.docker._

Common.appSettings

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

dockerBaseImage := "frolvlad/alpine-oraclejdk8"

dockerCommands := dockerCommands.value.flatMap{
 case cmd@Cmd("FROM",_) => List(cmd, Cmd("RUN", "apk update && apk add bash"))
 case other => List(other)
}

dockerRepository := Some("enpassant")

dockerExposedPorts := Seq(8080)

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= Seq(
  "com.github.scopt"       %% "scopt"                 % "3.3.0",
  "org.scala-lang"          % "scala-compiler"        % scalaVersion.value
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

lazy val testError = (project in file("modules/test-error"))
  .dependsOn(common)

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
    monitor,
    testError
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
    monitor,
    testError
  )


libraryDependencies ++= Common.commonDependencies

javaOptions in GatlingIt := overrideDefaultJavaOptions("-Xms1024m", "-Xmx2048m")

