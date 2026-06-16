resolvers += Resolver.url("sbt-plugins-releases", url("https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

addSbtPlugin("io.gatling" % "gatling-sbt" % "4.13.3")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.3")
