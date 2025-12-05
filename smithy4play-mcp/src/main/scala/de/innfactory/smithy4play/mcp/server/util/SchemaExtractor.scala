package de.innfactory.smithy4play.mcp.server.util

import smithy4s.schema.Schema.*

import play.api.libs.json.JsValue
import smithy4s.Schema

object SchemaExtractor {

  def extractQueryFieldNames(schema: Schema[?]): Set[String] =
    schema match {
      case StructSchema(_, _, fields, _) =>
        fields.flatMap { field =>
          field.hints.get(using smithy.api.HttpQuery).map(_ => field.label)
        }.toSet
      case _ => Set.empty
    }

  def extractBodyFieldNames(schema: Schema[?]): Set[String] =
    schema match {
      case StructSchema(_, _, fields, _) =>
        fields.flatMap { field =>
          field.hints.get(using smithy.api.HttpPayload).map(_ => field.label)
        }.toSet
      case _ => Set.empty
    }

  def extractQueryParams(inputSchema: Schema[?], inputJson: JsValue): Map[String, String] = {
    import play.api.libs.json.*

    val queryFields = extractQueryFieldNames(inputSchema)

    inputJson
      .as[JsObject]
      .fields
      .collect {
        case (key, value) if queryFields.contains(key) && value != JsNull =>
          value match {
            case JsString(s)  => Some(key -> s)
            case JsNumber(n)  => Some(key -> n.toString)
            case JsBoolean(b) => Some(key -> b.toString)
            case _            => None
          }
      }
      .flatten
      .toMap
  }

  def extractBody(inputSchema: Schema[?], inputJson: JsValue): Option[play.api.libs.json.JsObject] = {
    import play.api.libs.json.*

    val bodyFieldNames = extractBodyFieldNames(inputSchema)

    bodyFieldNames.headOption.flatMap { fieldName =>
      (inputJson \ fieldName).asOpt[JsObject]
    }
  }
}
