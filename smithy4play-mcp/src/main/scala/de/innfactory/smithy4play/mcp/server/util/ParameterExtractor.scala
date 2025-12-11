package de.innfactory.smithy4play.mcp.server.util

import play.api.libs.json.JsValue

object ParameterExtractor {

  def extractPathParameters(
    uriTemplate: String,
    inputJson: JsValue
  ): Either[String, (String, Map[String, String])] = {
    val pathParamPattern = """\{([^}]+)}""".r

    val initialState: Either[String, (String, Map[String, String])] =
      Right((uriTemplate, Map.empty[String, String]))

    pathParamPattern
      .findAllMatchIn(uriTemplate)
      .foldLeft(initialState) { (acc, m) =>
        acc.flatMap { case (path, params) =>
          val paramName = m.group(1)
          extractParameterValue(inputJson, paramName).map { paramValue =>
            (path.replace(s"{$paramName}", paramValue), params + (paramName -> paramValue))
          }
        }
      }
  }

  private def extractParameterValue(inputJson: JsValue, paramName: String): Either[String, String] = {
    val value = (inputJson \ paramName)
      .asOpt[String]
      .orElse((inputJson \ paramName).asOpt[Int].map(_.toString))
      .orElse((inputJson \ paramName).asOpt[Long].map(_.toString))
      .orElse((inputJson \ paramName).asOpt[Double].map(_.toString))
      .orElse((inputJson \ paramName).asOpt[Boolean].map(_.toString))

    value match {
      case Some(v) => Right(v)
      case None    => Left(s"Missing required path parameter: $paramName")
    }
  }
}
