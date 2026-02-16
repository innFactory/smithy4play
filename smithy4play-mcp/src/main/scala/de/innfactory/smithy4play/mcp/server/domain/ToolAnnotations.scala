package de.innfactory.smithy4play.mcp.server.domain

final case class ToolAnnotations(
  title: Option[String] = None,
  readOnlyHint: Option[Boolean] = None,
  destructiveHint: Option[Boolean] = None,
  idempotentHint: Option[Boolean] = None,
  openWorldHint: Option[Boolean] = None
)
