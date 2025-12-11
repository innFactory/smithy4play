package de.innfactory.smithy4play.mcp

import play.api.libs.json.{ JsNull, JsValue, Json }
import play.api.mvc.Result

object JsonRpcResponse {

  def success(id: Option[JsValue], result: JsValue): Result =
    play.api.mvc.Results.Ok(
      Json.obj(
        "jsonrpc" -> "2.0",
        "id"      -> id.getOrElse(JsNull),
        "result"  -> result
      )
    )

  def error(id: Option[JsValue], code: Int, message: String): Result = {
    val httpStatus = mapJsonRpcErrorToHttpStatus(code)
    val jsonBody   = Json.obj(
      "jsonrpc" -> "2.0",
      "id"      -> id.getOrElse(JsNull),
      "error"   -> Json.obj(
        "code"    -> code,
        "message" -> message
      )
    )
    httpStatus(jsonBody)
  }

  def unauthorized(message: String): Result =
    error(None, 401, s"Unauthorized: $message")

  private def mapJsonRpcErrorToHttpStatus(code: Int): JsValue => Result =
    code match {
      case -32700 => body => play.api.mvc.Results.Ok(body)
      case -32600 => body => play.api.mvc.Results.Ok(body)
      case -32601 => body => play.api.mvc.Results.Ok(body)
      case -32602 => body => play.api.mvc.Results.Ok(body)
      case -32603 => body => play.api.mvc.Results.Ok(body)
      case -32001 => body => play.api.mvc.Results.Ok(body)
      case 401    => body => play.api.mvc.Results.Unauthorized(body)
      case _      => body => play.api.mvc.Results.InternalServerError(body)
    }
}
