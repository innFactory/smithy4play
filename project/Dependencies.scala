import sbt.Keys.libraryDependencies
import sbt._

object Dependencies {

  val playVersion  = "2.9.6"
  val typesafePlay = "com.typesafe.play" %% "play" % playVersion

  val scalaVersion       = "2.13.15"
  val smithy4sVersion    = "0.18.27"
  val smithyCore         = "com.disneystreaming.smithy4s" %% "smithy4s-core"             % smithy4sVersion
  val smithyJson         = "com.disneystreaming.smithy4s" %% "smithy4s-json"             % smithy4sVersion
  val smithyXml          = "com.disneystreaming.smithy4s" %% "smithy4s-xml"              % smithy4sVersion
  val smithy4sCompliance = "com.disneystreaming.smithy4s" %% "smithy4s-compliance-tests" % smithy4sVersion
  val alloyCore          = "com.disneystreaming.alloy"     % "alloy-core"                % "0.3.14"
  val alloyOpenapi       = "com.disneystreaming.alloy"    %% "alloy-openapi"             % "0.3.14"

  val classgraph    = "io.github.classgraph" % "classgraph" % "4.8.179"
  val smithyVersion = "1.56.0"
  val testTraits    =
    "software.amazon.smithy" % "smithy-protocol-test-traits" % smithyVersion

  val scalatestPlus =
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
  val cats = "org.typelevel" %% "cats-core" % "2.12.0"

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
