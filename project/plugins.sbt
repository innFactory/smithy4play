addSbtPlugin("com.codecommit"               %% "sbt-github-packages"  % "0.5.3")
addSbtPlugin("org.scalameta"                %% "sbt-scalafmt"         % "2.5.2")
addSbtPlugin("com.disneystreaming.smithy4s" %% "smithy4s-sbt-codegen" % "0.18.15")
addSbtPlugin("org.playframework"            % "sbt-plugin"            % "3.0.1")
addSbtPlugin("org.scoverage"                %% "sbt-scoverage"        % "2.0.11")

ThisBuild / dependencyOverrides ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
)
