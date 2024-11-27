name := "yangjing-site"

homepage := Some(url("https://www.yangjing.me"))

startYear := Some(2019)

scalaVersion := "2.13.1"

scalafmtOnCompile := true

resolvers ++= Seq(
  "Bintray helloscala".at("https://helloscala.bintray.com/maven"),
  Resolver.sonatypeRepo("snapshots")
)

val versionFusion = "2.0.6"

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.2.14",
  "io.r2dbc" % "r2dbc-postgresql" % "0.8.3.RELEASE",
  "org.projectlombok" % "lombok" % "1.18.12",
  "com.typesafe.akka" %% "akka-serialization-jackson" % "2.6.6",
  "com.typesafe.akka" %% "akka-persistence-typed" % "2.6.6",
  "com.helloscala.fusion" %% "fusion-cluster" % versionFusion,
  "com.helloscala.fusion" %% "fusion-json-jackson" % versionFusion,
  "com.helloscala.fusion" %% "fusion-core" % versionFusion,
  "com.helloscala.fusion" %% "fusion-testkit" % versionFusion % Test
)
