package de.innfactory.smithy4play.mcp

import play.api.libs.json.JsValue
import play.api.mvc.Result

object McpHttpUtil {

  def addCorsAndStreamingHeaders(result: Result): Result =
    HttpHeaders.applyStandardHeaders(CorsHeaders.applyCorsHeaders(result))

  def jsonRpcSuccess(id: Option[JsValue], result: JsValue): Result =
    addCorsAndStreamingHeaders(JsonRpcResponse.success(id, result))

  def jsonRpcError(id: Option[JsValue], code: Int, message: String): Result =
    addCorsAndStreamingHeaders(JsonRpcResponse.error(id, code, message))

  def unauthorizedError(message: String): Result =
    addCorsAndStreamingHeaders(JsonRpcResponse.unauthorized(message))
}
