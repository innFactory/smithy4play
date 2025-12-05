package de.innfactory.smithy4play.mcp.server.service.impl

import de.innfactory.smithy4play.mcp.server.service.InputSchemaBuildingService
import de.innfactory.smithy4play.mcp.server.util.InputSchemaBuilder
import smithy4s.{ Document, Schema }

private[server] class DefaultInputSchemaBuildingService extends InputSchemaBuildingService {

  override def build(inputSchema: Schema[?], outputSchema: Schema[?]): Document = {
    val inputProperties = InputSchemaBuilder.build(inputSchema)

    inputProperties
  }
}
