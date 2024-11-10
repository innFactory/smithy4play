addSbtPlugin("com.codecommit"               %% "sbt-github-packages"  % "0.5.3")
addSbtPlugin("org.wartremover"              %% "sbt-wartremover"      % "3.2.4")
addSbtPlugin("org.scalameta"                %% "sbt-scalafmt"         % "2.5.2")
addSbtPlugin("com.disneystreaming.smithy4s" %% "smithy4s-sbt-codegen" % "0.18.25")
addSbtPlugin("com.typesafe.play"            %% "sbt-plugin"           % "2.9.5")
addSbtPlugin("org.scoverage"                %% "sbt-scoverage"        % "2.2.2")

ThisBuild / dependencyOverrides ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
)
