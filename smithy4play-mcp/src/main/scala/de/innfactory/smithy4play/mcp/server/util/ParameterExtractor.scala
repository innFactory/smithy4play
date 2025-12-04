package de.innfactory.smithy4play.mcp.server.util

import play.api.libs.json.JsValue
import smithy4s.Document

object ParameterExtractor {

  def extractPathParameters(
    uriTemplate: String,
    inputJson: JsValue
  ): Either[String, (String, Map[String, String])] = {
    val pathParamPattern = """\{([^}]+)}""".r

    val result: Either[String, (String, Map[String, String])] = pathParamPattern
      .findAllMatchIn(uriTemplate)
      .foldLeft(
        Right((uriTemplate, Map.empty[String, String])): Either[String, (String, Map[String, String])]
      ) { (acc, m) =>
        acc.flatMap { case (path, params) =>
          val paramName     = m.group(1)
          val paramValueOpt = (inputJson \ paramName)
            .asOpt[String]
            .orElse((inputJson \ paramName).asOpt[Int].map(_.toString))
            .orElse((inputJson \ paramName).asOpt[Long].map(_.toString))

          paramValueOpt match {
            case Some(paramValue) =>
              Right((path.replace(s"{$paramName}", paramValue), params + (paramName -> paramValue)))
            case None             =>
              Left(s"Missing required path parameter: $paramName")
          }
        }
      }

    result
  }
}
