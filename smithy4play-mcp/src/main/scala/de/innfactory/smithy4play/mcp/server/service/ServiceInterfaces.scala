package io.cleverone.mcp.server.service

import smithy4s.{Document, Schema}

trait ServiceDiscoveryService {

  def discoverServices(): List[smithy4s.Service[?]]
}

trait InputSchemaBuildingService {

  def build(inputSchema: Schema[?], outputSchema: Schema[?]): Document
}

trait HttpExtractionService {

  def extractHttpInfo(endpoint: smithy4s.Endpoint[?, ?, ?, ?, ?, ?]): Either[String, (String, String)]
}

trait RequestExecutionService {

  def executeRequest(
      method: String,
      path: String,
      queryParams: Map[String, String],
      body: Option[String],
      request: play.api.mvc.Request[?]
  ): Either[String, String]
}

