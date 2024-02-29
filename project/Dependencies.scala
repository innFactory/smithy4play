import sbt.Keys.libraryDependencies
import sbt._

object Dependencies {

  val playVersion  = "3.0.1"
  val typesafePlay = "org.playframework" %% "play" % playVersion

  val scalaVersion       = "2.13.13"
  val smithy4sVersion    = "0.18.9"
  val smithyCore         = "com.disneystreaming.smithy4s" %% "smithy4s-core"             % smithy4sVersion
  val smithyJson         = "com.disneystreaming.smithy4s" %% "smithy4s-json"             % smithy4sVersion
  val smithyXml          = "com.disneystreaming.smithy4s" %% "smithy4s-xml"              % smithy4sVersion
  val smithy4sCompliance = "com.disneystreaming.smithy4s" %% "smithy4s-compliance-tests" % smithy4sVersion
  val alloyCore          = "com.disneystreaming.alloy"     % "alloy-core"                % "0.3.0"
  val alloyOpenapi       = "com.disneystreaming.alloy"    %% "alloy-openapi"             % "0.3.0"

  val classgraph    = "io.github.classgraph" % "classgraph" % "4.8.165"
  val smithyVersion = "1.42.0"
  val testTraits    =
    "software.amazon.smithy" % "smithy-protocol-test-traits" % smithyVersion

  val scalatestPlus =
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
  val cats = "org.typelevel" %% "cats-core" % "2.9.0"

  lazy val list = Seq(
    smithyCore,
    smithyJson,
    smithyXml,
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
