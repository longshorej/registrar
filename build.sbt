import scala.collection.immutable.Seq
import scala.sys.process._
import ReleaseTransformations._

name := "rp-registrar"

scalaVersion := Versions.Scala

enablePlugins(SbtReactiveAppPlugin)

endpoints := Vector(HttpEndpoint("http", 0))

val Versions = new {
  val Akka      = "2.5.4"
  val AkkaHttp  = "10.0.10"
  val Scala     = "2.12.3"
  val ScalaTest = "3.0.1"
  val SprayJson = "1.3.3"
}

libraryDependencies ++= Vector(
  "com.typesafe.akka" %% "akka-actor"           % Versions.Akka,
  "com.typesafe.akka" %% "akka-stream"          % Versions.Akka,
  "com.typesafe.akka" %% "akka-http"            % Versions.AkkaHttp,
  "com.typesafe.akka" %% "akka-http-spray-json" % Versions.AkkaHttp,
  "com.typesafe.akka" %% "akka-http-testkit"    % Versions.AkkaHttp      % "test",
  "com.typesafe.akka" %% "akka-testkit"         % Versions.Akka          % "test",
  "com.typesafe.akka" %% "akka-typed"           % Versions.Akka,
  "io.spray"          %% "spray-json"           % Versions.SprayJson,
  "org.scalatest"     %% "scalatest"            % Versions.ScalaTest     % "test"
)

organization := "com.lightbend.rp"
organizationName := "Lightbend, Inc."
startYear := Some(2017)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

TaskKey[Unit]("dockerTagAndWarnAboutPublishing") := {
  val tag = s"lightbend-docker-registry.bintray.io/rp/registrar:${version.value}"
  val tagCommand = Seq("docker", "tag", s"registrar:${version.value}", tag)

  streams.value.log.info(s"Tagging Docker Image: $tag")

  val tagCode = tagCommand.!

  if (tagCode != 0) {
    sys.error(s"Expected 0, received $tagCode for: $tagCommand")
  }

  streams.value.log.warn("The build has been completed but the application has not been published. Consult the Platform Tooling Release Process document in Google Drive.")
  streams.value.log.warn(s"""To publish: docker push "$tag"""")
}

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommandAndRemaining("test"),
  setReleaseVersion,
  commitReleaseVersion,
  releaseStepCommandAndRemaining("packageBin"),
  releaseStepCommandAndRemaining("docker:publishLocal"),
  releaseStepCommandAndRemaining("dockerTagAndWarnAboutPublishing"),
  tagRelease,
  setNextVersion,
  commitNextVersion,
  pushChanges
)
