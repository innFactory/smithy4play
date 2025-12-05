package de.innfactory.smithy4play.mcp

import play.api.mvc.Result

object HttpHeaders {

  private val standardHeaders: Seq[(String, String)] = Seq(
    "Cache-Control"          -> "no-cache, no-store, must-revalidate",
    "Connection"             -> "keep-alive",
    "X-Content-Type-Options" -> "nosniff",
    "X-Accel-Buffering"      -> "no"
  )

  def applyStandardHeaders(result: Result): Result =
    result.withHeaders(standardHeaders*)
}
