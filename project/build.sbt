libraryDependencies += "io.github.classgraph" % "classgraph" % "6.0.1"

Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "smithy4play-sbt-codegen" / "src" / "main" / "scala"
