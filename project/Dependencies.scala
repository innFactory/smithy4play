import sbt.Keys.libraryDependencies
import sbt._

object Dependencies {

  val playVersion  = "2.8.13"
  val typesafePlay = "com.typesafe.play" %% "play" % playVersion

  val scalaVersion    = "2.13.8"
  val smithy4sVersion = "0.16.4"
  val smithyCore      = "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion
  val smithyJson      = "com.disneystreaming.smithy4s" %% "smithy4s-json" % smithy4sVersion

  val classgraph    = "io.github.classgraph"   % "classgraph"   % "4.8.149"
  val smithyVersion = "1.24.0"
  val model         = "software.amazon.smithy" % "smithy-model" % smithyVersion

  val testTraits =
    "software.amazon.smithy" % "smithy-protocol-test-traits" % smithyVersion
  val build     = "software.amazon.smithy" % "smithy-build" % smithyVersion
  val awsTraits =
    "software.amazon.smithy" % "smithy-aws-traits" % smithyVersion

  val scalatestPlus =
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
  val cats = "org.typelevel" %% "cats-core" % "2.8.0"

  lazy val list = Seq(
    smithyCore,
    smithyJson,
    model,
    testTraits,
    build,
    awsTraits,
    classgraph,
    scalatestPlus,
    typesafePlay,
    cats
  )

}
