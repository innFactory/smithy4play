addSbtPlugin("com.codecommit"               %% "sbt-github-packages"  % "0.5.3")
addSbtPlugin("org.wartremover"              %% "sbt-wartremover"      % "3.1.6")
addSbtPlugin("org.scalameta"                %% "sbt-scalafmt"         % "2.5.2")
addSbtPlugin("com.disneystreaming.smithy4s" %% "smithy4s-sbt-codegen" % "0.18.8")
addSbtPlugin("com.typesafe.play"            %% "sbt-plugin"           % "2.9.1")
addSbtPlugin("org.scoverage"                %% "sbt-scoverage"        % "2.0.9")

ThisBuild / dependencyOverrides ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
)
