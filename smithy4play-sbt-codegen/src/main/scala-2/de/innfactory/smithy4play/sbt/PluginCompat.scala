package de.innfactory.smithy4play.sbt

import sbt.*

/** sbt 1.x source-compatibility shim.
  *
  * On sbt 1.x a classpath's `Attributed#data` is already a `java.io.File`, so the `FileConverter` argument is accepted
  * (to keep the call site identical across sbt versions) but unused. The sbt 2.x counterpart lives in
  * `src/main/scala-3`.
  */
private[smithy4play] object PluginCompat {

  def toFiles(cp: Seq[Attributed[File]])(@annotation.unused conv: xsbti.FileConverter): Seq[File] =
    cp.map(_.data)

  /** sbt 1.x has no task cache, so `Def.uncached(...)` is a no-op identity. On sbt 2.x the native `Def.uncached`
    * (provided by the scala-3 variant's absence of this shim) opts the redefined task out of the result cache.
    * Importing `PluginCompat.*` brings this into scope.
    */
  implicit class DefUncachedOps(private val d: sbt.Def.type) extends AnyVal {
    def uncached[A](a: A): A = a
  }

}
