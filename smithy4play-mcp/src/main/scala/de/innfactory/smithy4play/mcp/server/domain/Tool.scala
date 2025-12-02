package io.cleverone.mcp.server.domain

import smithy4s.Document

final case class Tool(
    name: String,
    description: Option[String],
    inputSchema: Document
)

