package de.innfactory.smithy4play.sbt

import sbt.*

/** sbt 2.x source-compatibility shim.
  *
  * On sbt 2.x a classpath's `Attributed#data` is an `xsbti.HashedVirtualFileRef` (part of the virtual-file migration),
  * so it must be resolved back to a real `java.io.File` through the build's `FileConverter`. The sbt 1.x counterpart
  * lives in `src/main/scala-2`.
  */
private[smithy4play] object PluginCompat {

  def toFiles(cp: Seq[Attributed[xsbti.HashedVirtualFileRef]])(conv: xsbti.FileConverter): Seq[File] =
    cp.map(entry => conv.toPath(entry.data).toFile)

}
