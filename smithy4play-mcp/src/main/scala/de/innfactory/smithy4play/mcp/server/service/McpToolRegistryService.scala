package io.cleverone.mcp.server.service

import cats.data.EitherT
import io.cleverone.mcp.server.domain.{McpError, Tool}
import play.api.mvc.Request
import smithy4s.Document

import scala.concurrent.{ExecutionContext, Future}

trait McpToolRegistryService {

  def getAllTools: List[Tool]

  def callTool(name: String, arguments: Option[Document], request: Request[?])(using
      ExecutionContext
  ): EitherT[Future, McpError, String]
}

