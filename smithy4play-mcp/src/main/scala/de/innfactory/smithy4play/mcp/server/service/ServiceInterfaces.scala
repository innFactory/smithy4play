package de.innfactory.smithy4play.mcp.server.service

import com.google.inject.ImplementedBy
import smithy4s.{ Document, Schema }
import de.innfactory.smithy4play.mcp.server.service.impl.DefaultSchemaBuilderService

@ImplementedBy(classOf[DefaultSchemaBuilderService])
trait SchemaBuilderService {
  def buildInput(inputSchema: Schema[?]): Document
  def buildOutput(outputSchema: Schema[?]): Document
}
