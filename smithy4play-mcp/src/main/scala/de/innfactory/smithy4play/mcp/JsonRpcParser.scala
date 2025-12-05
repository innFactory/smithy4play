package de.innfactory.smithy4play.mcp

import play.api.libs.json.JsValue

object JsonRpcParser {

  final case class JsonRpcRequest(
    jsonrpcVersion: Option[String],
    id: Option[JsValue],
    method: Option[String],
    params: Option[JsValue]
  ) {
    def validate: Either[JsonRpcValidationError, Unit] =
      if (jsonrpcVersion.contains("2.0")) Right(())
      else Left(JsonRpcValidationError(-32600, "Invalid Request: jsonrpc must be 2.0"))
  }

  final case class JsonRpcValidationError(code: Int, message: String)

  def parse(body: JsValue): JsonRpcRequest =
    JsonRpcRequest(
      jsonrpcVersion = (body \ "jsonrpc").asOpt[String],
      id = (body \ "id").toOption,
      method = (body \ "method").asOpt[String],
      params = (body \ "params").toOption
    )

  private def getMethod(request: JsonRpcRequest): Either[JsonRpcValidationError, String] =
    request.method match {
      case Some(method) => Right(method)
      case None         => Left(JsonRpcValidationError(-32600, "Invalid Request: missing method"))
    }

  def validateRequest(request: JsonRpcRequest): Either[JsonRpcValidationError, JsonRpcRequest] =
    for {
      _ <- request.validate
      _ <- getMethod(request)
    } yield request
}
