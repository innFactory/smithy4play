package de.innfactory.smithy4play.mcp

import play.api.mvc.{ RequestHeader, Result }

/**
 * CORS headers configuration for MCP endpoints.
 * 
 * SECURITY NOTE: The default configuration allows all origins ("*") which is suitable
 * for development but should be restricted in production. Override `allowedOrigins`
 * or use `withAllowedOrigins` to specify allowed origins for your deployment.
 */
object CorsHeaders {

  private val defaultAllowedOrigins: Option[Set[String]] = None // None means "*" (all origins)

  private def corsHeaders(origin: Option[String], allowedOrigins: Option[Set[String]]): Seq[(String, String)] = {
    val allowOrigin = allowedOrigins match {
      case None => "*" // Allow all origins (development mode)
      case Some(allowed) =>
        origin.filter(allowed.contains).getOrElse("") // Only allow if in whitelist
    }
    
    val baseHeaders = Seq(
      "Access-Control-Allow-Origin"   -> allowOrigin,
      "Access-Control-Allow-Methods"  -> "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers"  -> "Content-Type, Authorization, X-Session-ID",
      "Access-Control-Max-Age"        -> "3600",
      "Access-Control-Expose-Headers" -> "X-Session-ID"
    )
    
    // Add Vary: Origin when using a whitelist to ensure proper caching behavior
    if (allowedOrigins.isDefined) baseHeaders :+ ("Vary" -> "Origin") else baseHeaders
  }

  /**
   * Apply CORS headers to a result, allowing all origins.
   * Use `applyCorsHeaders(result, request, allowedOrigins)` for production.
   */
  def applyCorsHeaders(result: Result): Result =
    result.withHeaders(corsHeaders(None, defaultAllowedOrigins)*)

  /**
   * Apply CORS headers to a result with configurable allowed origins.
   * 
   * @param result The result to add headers to
   * @param request The request (used to extract the Origin header)
   * @param allowedOrigins Set of allowed origins, or None to allow all ("*")
   */
  def applyCorsHeaders(result: Result, request: RequestHeader, allowedOrigins: Option[Set[String]]): Result =
    result.withHeaders(corsHeaders(request.headers.get("Origin"), allowedOrigins)*)
}
