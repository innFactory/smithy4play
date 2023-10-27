import sbt.Keys.libraryDependencies
import sbt._

object Dependencies {

  val playVersion  = "2.9.0-RC3"
  val typesafePlay = "com.typesafe.play" %% "play" % playVersion

  val scalaVersion       = "2.13.12"
  val smithy4sVersion    = "0.17.19"
  val smithyCore         = "com.disneystreaming.smithy4s" %% "smithy4s-core"             % smithy4sVersion
  val smithyJson         = "com.disneystreaming.smithy4s" %% "smithy4s-json"             % smithy4sVersion
  val smithy4sCompliance = "com.disneystreaming.smithy4s" %% "smithy4s-compliance-tests" % smithy4sVersion
  val alloyCore          = "com.disneystreaming.alloy"     % "alloy-core"                % "0.2.8"
  val alloyOpenapi       = "com.disneystreaming.alloy"    %% "alloy-openapi"             % "0.2.8"

  val classgraph    = "io.github.classgraph" % "classgraph" % "4.8.162"
  val smithyVersion = "1.39.1"
  val testTraits    =
    "software.amazon.smithy" % "smithy-protocol-test-traits" % smithyVersion

  val scalatestPlus =
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
  val cats = "org.typelevel" %% "cats-core" % "2.9.0"

  lazy val list = Seq(
    smithyCore,
    smithyJson,
    alloyCore,
    alloyOpenapi,
    smithy4sCompliance,
    testTraits,
    classgraph,
    scalatestPlus,
    typesafePlay,
    cats
  )

}
