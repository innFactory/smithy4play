package de.innfactory.smithy4play.mcp.server.domain

private[server] final case class HttpInfo(method: String, uriTemplate: String)

private[server] final case class PathInfo(resolvedPath: String, pathParams: Map[String, String])

private[server] final case class ClientInfo(name: String, version: String)

private[server] final case class McpSession(
  sessionId: String,
  var isInitialized: Boolean = false,
  var clientInfo: Option[ClientInfo] = None,
  createdAt: Long = System.currentTimeMillis()
)
