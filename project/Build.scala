import sbt._
import Keys._

object AstracAskRetryBuild extends Build {
  // Akka
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.2.3"
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.2.3"

  // Utilities
  val scalatest = "org.scalatest" % "scalatest_2.10" % "2.0" % "test"

  lazy val astracAskRetry =
    Project(id = "astrac-askretry", base = file("."), settings = Project.defaultSettings ++ Seq(
      scalaVersion := "2.10.3",
      sbtVersion := "0.13.1",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      libraryDependencies ++= Seq(
        scalatest,
        akkaActor,
        akkaTestkit
      )))
}
