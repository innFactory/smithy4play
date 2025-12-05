package de.innfactory.smithy4play.mcp.server.service

import com.google.inject.ImplementedBy
import smithy4s.{ Document, Schema }
import de.innfactory.smithy4play.mcp.server.service.impl.{ DefaultInputSchemaBuildingService, DefaultServiceDiscovery }

@ImplementedBy(classOf[DefaultServiceDiscovery])
trait ServiceDiscoveryService {
  def discoverServices(): List[smithy4s.Service[?]]
}

@ImplementedBy(classOf[DefaultInputSchemaBuildingService])
trait InputSchemaBuildingService {

  def build(inputSchema: Schema[?], outputSchema: Schema[?]): Document
}
