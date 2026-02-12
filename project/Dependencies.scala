import play.sbt.PlayImport
import sbt.Keys.libraryDependencies
import sbt.*

object Dependencies {

  val playVersion  = "3.0.10"
  val typesafePlay = "org.playframework" %% "play" % playVersion

  val scalaVersion    = "3.7.4"
  val smithy4sVersion = "0.18.47"
  val smithyVersion   = "1.67.0"

  val smithyCore         = "com.disneystreaming.smithy4s" %% "smithy4s-core"             % smithy4sVersion
  val smithyInteropCats  = "com.disneystreaming.smithy4s" %% "smithy4s-cats"             % smithy4sVersion
  val smithyJson         = "com.disneystreaming.smithy4s" %% "smithy4s-json"             % smithy4sVersion
  val smithyXml          = "com.disneystreaming.smithy4s" %% "smithy4s-xml"              % smithy4sVersion
  val smithy4sCompliance = "com.disneystreaming.smithy4s" %% "smithy4s-compliance-tests" % smithy4sVersion
  val alloyCore          = "com.disneystreaming.alloy"     % "alloy-core"                % "0.3.36"
  val alloyOpenapi       = "com.disneystreaming.alloy"    %% "alloy-openapi"             % "0.3.36"
  val smithyOpenapi      = "software.amazon.smithy"        % "smithy-openapi"            % "1.67.0"

  val opentelemetryBOM      = "io.opentelemetry" % "opentelemetry-bom"       % "1.59.0"
  val opentelemetryBOMAlpha = "io.opentelemetry" % "opentelemetry-bom-alpha" % "1.53.0-alpha"
  val opentelemetryAPI      = "io.opentelemetry" % "opentelemetry-api"       % "1.59.0"

  // val testTraits    =
  //   "software.amazon.smithy" % "smithy-protocol-test-traits" % smithyVersion

  val scalatestPlus =
    "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test

  val cats = "org.typelevel" %% "cats-core" % "2.13.0"

  lazy val list: Seq[ModuleID] = Seq(
    smithyCore,
    smithyJson,
    PlayImport.ws,
    smithyXml,
    smithyOpenapi,
    alloyCore,
    alloyOpenapi,
    scalatestPlus,
    typesafePlay,
    cats,
    smithyInteropCats,
    opentelemetryBOM,
    opentelemetryAPI,
    opentelemetryBOMAlpha
  )

}
