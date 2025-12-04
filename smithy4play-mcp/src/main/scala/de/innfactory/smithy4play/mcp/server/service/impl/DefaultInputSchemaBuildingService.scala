package de.innfactory.smithy4play.mcp.server.service.impl

import de.innfactory.smithy4play.mcp.server.service.InputSchemaBuildingService
import de.innfactory.smithy4play.mcp.server.util.SchemaExtractor
import smithy4s.{ Document, Schema }

private[server] class DefaultInputSchemaBuildingService extends InputSchemaBuildingService {

  override def build(inputSchema: Schema[?], outputSchema: Schema[?]): Document = {
    val queryFields = SchemaExtractor.extractQueryFieldNames(inputSchema)
    val bodyFields  = SchemaExtractor.extractBodyFieldNames(inputSchema)

    val queryProps: Map[String, Document] =
      queryFields.map(name => name -> Document.obj("type" -> Document.fromString("string"))).toMap

    val bodyProp: Option[(String, Document)] =
      bodyFields.headOption.map { name =>
        name -> Document.obj(
          "type"       -> Document.fromString("object"),
          "properties" -> Document.obj() // keeping generic; deeper typing out of scope
        )
      }

    val properties = Document.obj(
      (queryProps ++ bodyProp.toMap).toSeq*
    )

    Document.obj(
      "type"       -> Document.fromString("object"),
      "properties" -> properties
    )
  }
}

