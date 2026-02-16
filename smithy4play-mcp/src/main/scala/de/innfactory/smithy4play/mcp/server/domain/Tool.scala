package de.innfactory.smithy4play.mcp.server.domain

import smithy4s.Document

final case class Tool(
  name: String,
  description: Option[String] = None,
  inputSchema: Document,
  outputSchema: Option[Document] = None,
  title: Option[String] = None,
  annotations: Option[ToolAnnotations] = None
)
