package de.innfactory.smithy4play.mcp.common

object MCPCommon {
  
  val MCP_ENDPOINT: String = "/mcp"
  
  object HttpMethods {
    val GET: String    = "GET"
    val POST: String   = "POST"
    val PUT: String    = "PUT"
    val DELETE: String = "DELETE"
    val PATCH: String  = "PATCH"
    val OPTIONS: String = "OPTIONS"
  }
  
  object ContentTypes {
    val APPLICATION_JSON: String = "application/json"
    val TEXT_PLAIN: String       = "text/plain"
  }

}
