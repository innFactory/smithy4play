package de.innfactory.smithy4play.mcp

import play.api.mvc.Result

object CorsHeaders {

  private val corsHeaders: Seq[(String, String)] = Seq(
    "Access-Control-Allow-Origin"   -> "*",
    "Access-Control-Allow-Methods"  -> "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers"  -> "Content-Type, Authorization, X-Session-ID",
    "Access-Control-Max-Age"        -> "3600",
    "Access-Control-Expose-Headers" -> "X-Session-ID"
  )

  def applyCorsHeaders(result: Result): Result =
    result.withHeaders(corsHeaders*)
}
