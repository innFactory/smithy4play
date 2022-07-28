import sbt._

object Dependencies {

  val playVersion = "2.8.13"
  val typesafePlay = "com.typesafe.play" %% "play" % playVersion

  val scalaVersion = "2.13.8"

  val smithyCore = "com.disneystreaming.smithy4s" %% "smithy4s-core" % "0.14.2"
  //val smithyCli = "com.disneystreaming.smithy4s" %% "smithy4s-codegen-cli" % "0.14.2"

  val smithyJson = "com.disneystreaming.smithy4s" %% "smithy4s-json" % "0.14.2"
  //val smithyProtocols = "com.disneystreaming.smithy4s" %% "smithy4s-aws-kernel" % "0.13.5"

  val scalatestPlus =
    "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
  val cats = "org.typelevel" %% "cats-core" % "2.7.0"

  lazy val list = Seq(
    smithyCore,
    smithyJson,
    //smithyProtocols,
    //smithyCli,
    scalatestPlus,
    typesafePlay,
    cats
  )

}
