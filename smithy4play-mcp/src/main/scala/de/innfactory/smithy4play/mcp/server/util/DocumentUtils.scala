package de.innfactory.smithy4play.mcp.server.util

import smithy4s.Document

object DocumentUtils {
  def merge(a: Document, b: Document): Document = (a, b) match {
    case (Document.DObject(f1), Document.DObject(f2)) => Document.obj((f1 ++ f2).toSeq*)
    case _                                            => b
  }
}
