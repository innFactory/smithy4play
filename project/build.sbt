libraryDependencies += "io.github.classgraph" % "classgraph" % "4.8.179"

Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "smithy4play-sbt-codegen" / "src" / "main" / "scala"
// The meta-build runs on Scala 2.12 (sbt 1.x), so it needs the sbt 1.x variant of the
// PluginCompat shim. The matching scala-3 variant is used only when the plugin itself is
// cross-compiled for sbt 2.x.
Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "smithy4play-sbt-codegen" / "src" / "main" / "scala-2"
