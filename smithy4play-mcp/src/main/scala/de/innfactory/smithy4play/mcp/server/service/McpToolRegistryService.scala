package de.innfactory.smithy4play.mcp.server.service

import com.google.inject.ImplementedBy
import cats.data.EitherT
import de.innfactory.smithy4play.mcp.server.domain.{ McpError, Tool }
import play.api.mvc.Request
import smithy4s.Document
import scala.concurrent.{ ExecutionContext, Future }
import de.innfactory.smithy4play.mcp.server.service.impl.McpToolRegistryServiceImpl

@ImplementedBy(classOf[McpToolRegistryServiceImpl])
trait McpToolRegistryService {

  def getAllTools: List[Tool]

  def callTool(name: String, arguments: Option[Document], request: Request[?])(using
    ExecutionContext
  ): EitherT[Future, McpError, String]
}
