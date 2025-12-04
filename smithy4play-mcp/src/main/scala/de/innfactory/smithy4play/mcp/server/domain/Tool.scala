package de.innfactory.smithy4play.mcp.server.domain

import smithy4s.Document

final case class Tool(
  name: String,
  description: Option[String],
  inputSchema: Document
)
