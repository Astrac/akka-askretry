import sbt._
import Keys._

object AstracAskRetryBuild extends Build {
  // Akka
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.2.3"

  // Utilities
  val specs2 = "org.specs2" %% "specs2" % "2.2.3" % "test"

  lazy val astracAskRetry =
    Project(id = "astrac-askretry", base = file("."), settings = Project.defaultSettings ++ Seq(
      scalaVersion := "2.10.3",
      sbtVersion := "0.13.1",
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      libraryDependencies ++= Seq(
        specs2,
        akkaActor
      )))
}
