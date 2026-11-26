libraryDependencies += "io.github.classgraph" % "classgraph" % "6.0.2"

Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "smithy4play-sbt-codegen" / "src" / "main" / "scala"
