package de.innfactory.smithy4play.mcp.server.domain

import smithy4s.{ Endpoint, Schema }

private[server] final case class McpEndpointInfo(
  toolName: String,
  description: Option[String],
  endpoint: Endpoint[?, ?, ?, ?, ?, ?],
  inputSchema: Schema[?],
  outputSchema: Schema[?]
)
