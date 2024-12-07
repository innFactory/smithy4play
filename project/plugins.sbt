addSbtPlugin("com.codecommit"               %% "sbt-github-packages"  % "0.5.3")
addSbtPlugin("org.wartremover"              %% "sbt-wartremover"      % "3.2.5")
addSbtPlugin("org.scalameta"                %% "sbt-scalafmt"         % "2.5.2")
addSbtPlugin("com.disneystreaming.smithy4s" %% "smithy4s-sbt-codegen" % "0.18.27")
addSbtPlugin("org.playframework"            %% "sbt-plugin"           % "3.0.6")
addSbtPlugin("org.scoverage"                %% "sbt-scoverage"        % "2.2.2")

ThisBuild / dependencyOverrides ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "2.3.0"
)
