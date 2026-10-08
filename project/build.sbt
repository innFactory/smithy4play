libraryDependencies += "io.github.classgraph" % "classgraph" % "4.8.197"

Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "smithy4play-sbt-codegen" / "src" / "main" / "scala"
