import sbt._

object Dependencies {

  val playVersion = "2.8.13"
  val typesafePlay = "com.typesafe.play" %% "play" % playVersion

  val scalaVersion = "2.13.8"

  val smithyCore = "com.disneystreaming.smithy4s" %% "smithy4s-core" % "0.14.2"
  //val smithyCli = "com.disneystreaming.smithy4s" %% "smithy4s-codegen-cli" % "0.14.2"

  val smithyJson = "com.disneystreaming.smithy4s" %% "smithy4s-json" % "0.14.2"
  val smithyOpenApi = "com.disneystreaming.smithy4s" %% "smithy4s-openapi" % "0.14.2"
  val smithyProtocols =
    "com.disneystreaming.smithy4s" %% "smithy4s-aws-kernel" % "0.14.2"

  val scalatestPlus =
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
  val cats = "org.typelevel" %% "cats-core" % "2.7.0"

  val smithyVersion = "1.22.0"
  val model = "software.amazon.smithy" % "smithy-model" % smithyVersion
  val build = "software.amazon.smithy" % "smithy-build" % smithyVersion
  val awsTraits =
    "software.amazon.smithy" % "smithy-aws-traits" % smithyVersion
  val openapi = "software.amazon.smithy" % "smithy-openapi" % smithyVersion
  val waiters = "software.amazon.smithy" % "smithy-waiters" % smithyVersion

  lazy val list = Seq(
    smithyCore,
    smithyJson,
    model,
    build,
    awsTraits,
    openapi,
    waiters,
    smithyOpenApi,
    //smithyProtocols,
    //smithyCli,
    scalatestPlus,
    typesafePlay,
    cats
  )

}
