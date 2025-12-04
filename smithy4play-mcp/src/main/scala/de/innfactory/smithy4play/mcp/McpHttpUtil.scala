package de.innfactory.smithy4play.mcp

import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.mvc.Result

object McpHttpUtil {

  def addCorsAndStreamingHeaders(result: Result): Result =
    result.withHeaders(
      "Access-Control-Allow-Origin"   -> "*",
      "Access-Control-Allow-Methods"  -> "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers"  -> "Content-Type, Authorization, X-Session-ID",
      "Access-Control-Max-Age"        -> "3600",
      "Access-Control-Expose-Headers" -> "X-Session-ID",
      "Cache-Control"                 -> "no-cache, no-store, must-revalidate",
      "Connection"                    -> "keep-alive",
      "X-Content-Type-Options"        -> "nosniff",
      "X-Accel-Buffering"             -> "no"
    )

  def jsonRpcSuccess(id: Option[JsValue], result: JsValue): Result =
    addCorsAndStreamingHeaders(
      play.api.mvc.Results.Ok(
        Json.obj(
          "jsonrpc" -> "2.0",
          "id"      -> id.getOrElse(JsNull),
          "result"  -> result
        )
      )
    )

  def jsonRpcError(id: Option[JsValue], code: Int, message: String): Result =
    addCorsAndStreamingHeaders(
      play.api.mvc.Results.Ok(
        Json.obj(
          "jsonrpc" -> "2.0",
          "id"      -> id.getOrElse(JsNull),
          "error"   -> Json.obj(
            "code"    -> code,
            "message" -> message
          )
        )
      )
    )

  def unauthorizedError(message: String): Result =
    addCorsAndStreamingHeaders(
      play.api.mvc.Results.Ok(
        Json.obj(
          "jsonrpc" -> "2.0",
          "id"      -> JsNull,
          "error"   -> Json.obj(
            "code"    -> 401,
            "message" -> s"Unauthorized: $message"
          )
        )
      )
    )
}
