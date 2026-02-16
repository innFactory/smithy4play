package de.innfactory.smithy4play.mcp.server.service.impl

import de.innfactory.smithy4play.mcp.server.service.SchemaBuilderService
import de.innfactory.smithy4play.mcp.server.util.{ InputSchemaBuilder, OutputSchemaBuilder }
import smithy4s.{ Document, Schema }

private[server] class DefaultSchemaBuilderService extends SchemaBuilderService {

  override def buildInput(inputSchema: Schema[?]): Document = InputSchemaBuilder.build(inputSchema)

  override def buildOutput(outputSchema: Schema[?], recursive: Boolean = false): Document =
    OutputSchemaBuilder.build(outputSchema, recursive)
}
