package de.innfactory.smithy4play.mcp.server.service.impl

import com.google.inject.{ Inject, Singleton }
import de.innfactory.smithy4play.AutoRouter
import de.innfactory.smithy4play.mcp.server.domain.{ McpError, Tool }
import de.innfactory.smithy4play.mcp.server.service.{
  InputSchemaBuildingService,
  McpToolRegistryService,
  ServiceDiscoveryService
}
import org.apache.pekko.stream.Materializer
import play.api.Application
import play.api.libs.json.{ JsArray, JsNull, JsObject, JsValue, Json }
import play.api.mvc.{ ControllerComponents, Request }
import smithy4s.{ Document, Endpoint, Schema, Service }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import cats.data.EitherT
import cats.implicits.*
import de.innfactory.smithy4play.mcp.AutoRouterWithMcp
import de.innfactory.smithy4play.mcp.server.util.DocumentConverter.documentToJsValue

import javax.inject.Provider

@Singleton
class McpToolRegistryServiceImpl @Inject() (
  autoRouter: Provider[AutoRouterWithMcp],
  serviceDiscovery: ServiceDiscoveryService,
  schemaBuilder: InputSchemaBuildingService
)(using ExecutionContext, ControllerComponents, Application)
    extends McpToolRegistryService {

  private given Materializer = summon[Application].materializer

  private lazy val mcpEndpoints: List[McpEndpointInfo] = discoverMcpEndpoints()

  private final case class McpEndpointInfo(
    toolName: String,
    description: Option[String],
    endpoint: Endpoint[?, ?, ?, ?, ?, ?],
    inputSchema: Schema[?],
    outputSchema: Schema[?]
  )

  private def discoverMcpEndpoints(): List[McpEndpointInfo] = {
    val services: List[Service[?]] = serviceDiscovery.discoverServices()

    services.flatMap { service =>
      service.endpoints.flatMap { endpoint =>
        endpoint.hints.get(using de.innfactory.smithy4play.mcp.ExposeMcp).map { exposeMcp =>
          val operationName = endpoint.id.name
          val toolName      = exposeMcp.description.fold(operationName)(_ => operationName)

          McpEndpointInfo(
            toolName = toolName,
            description = exposeMcp.description,
            endpoint = endpoint,
            inputSchema = endpoint.input,
            outputSchema = endpoint.output
          )
        }
      }
    }
  }

  private def schemaToToolInputDoc(inputSchema: Schema[?], outputSchema: Schema[?]): Document = {
    val input  = schemaBuilder.build(inputSchema, outputSchema)
    val output = schemaBuilder.build(outputSchema, inputSchema)

    val outputUnion = output match {
      case Document.DObject(fields) =>
        fields
          .get("properties")
          .flatMap {
            case Document.DObject(p) => p.get("body")
            case _                   => None
          }
          .flatMap {
            case Document.DObject(bodyFields) => bodyFields.get("oneOf")
            case _                            => None
          }
          .getOrElse(Document.obj())
      case _                        => Document.obj()
    }

    if (outputUnion != Document.obj()) {
      mergeObjects(input, Document.obj("x-outputSchema" -> outputUnion))
    } else {
      input
    }
  }

  private def mergeObjects(a: Document, b: Document): Document = (a, b) match {
    case (Document.DObject(f1), Document.DObject(f2)) => Document.obj((f1 ++ f2).toSeq*)
    case _                                            => b
  }

  override def getAllTools: List[Tool] =
    mcpEndpoints.map { info =>
      val inputSchema = schemaToToolInputDoc(info.inputSchema, info.outputSchema)
      Tool(
        name = info.toolName,
        description = info.description,
        inputSchema = inputSchema
      )
    }

  override def callTool(name: String, arguments: Option[Document], request: Request[?])(using
    ExecutionContext
  ): EitherT[Future, McpError, String] =
    for {
      endpointInfo  <- findEndpoint(name)
      document      <- validateArguments(name, arguments)
      inputJson      = documentToJsValue(document)
      httpInfo      <- extractHttpInfo(endpointInfo)
      pathAndParams <- extractPathParameters(httpInfo._1, inputJson)
      queryParams    = extractQueryParams(endpointInfo.inputSchema, inputJson)
      bodyOpt        = extractBody(endpointInfo.inputSchema, inputJson)
      result        <- executeRequest(
                         endpointInfo.toolName,
                         httpInfo._2,
                         pathAndParams._1,
                         queryParams,
                         bodyOpt,
                         request
                       )
    } yield result

  private def findEndpoint(name: String): EitherT[Future, McpError, McpEndpointInfo] =
    EitherT.fromOption[Future](
      mcpEndpoints.find(_.toolName == name),
      McpError.ToolNotFound(name)
    )

  private def validateArguments(toolName: String, arguments: Option[Document]): EitherT[Future, McpError, Document] =
    EitherT.fromOption[Future](
      arguments,
      McpError.InvalidArguments(toolName, "arguments are required")
    )

  private def parseJson(jsonString: String): EitherT[Future, McpError, JsValue] =
    EitherT.fromEither[Future](
      Try(Json.parse(jsonString)).toEither.leftMap(e => McpError.InvalidJsonDocument(e.getMessage))
    )

  private def extractHttpInfo(endpointInfo: McpEndpointInfo): EitherT[Future, McpError, (String, String)] =
    EitherT.fromOption[Future](
      endpointInfo.endpoint.hints.get(using smithy.api.Http).map { httpHint =>
        (httpHint.uri.value, httpHint.method.value)
      },
      McpError.MissingHttpHint(endpointInfo.toolName)
    )

  private def extractPathParameters(
    uriTemplate: String,
    inputJson: JsValue
  ): EitherT[Future, McpError, (String, Map[String, String])] = {
    val pathParamPattern = """\{([^}]+)}""".r

    def toStringValue(js: JsValue): Option[String] =
      js.asOpt[String]
        .orElse(js.asOpt[Int].map(_.toString))
        .orElse(js.asOpt[Long].map(_.toString))
        .orElse(js.asOpt[Double].map(_.toString))
        .orElse(js.asOpt[Boolean].map(_.toString))

    val result: Either[McpError, (String, Map[String, String])] = pathParamPattern
      .findAllMatchIn(uriTemplate)
      .foldLeft(
        Right((uriTemplate, Map.empty[String, String])): Either[McpError, (String, Map[String, String])]
      ) { (acc, m) =>
        acc.flatMap { case (path, params) =>
          val paramName                                = m.group(1)
          val seqValues: scala.collection.Seq[JsValue] = (inputJson \\ paramName)
          val optValue                                 = seqValues.view
            .flatMap(toStringValue)
            .headOption
            .orElse(
              (inputJson \
                paramName).toOption.flatMap(toStringValue)
            )

          optValue match {
            case Some(v) => Right((path.replace(s"{$paramName}", v), params + (paramName -> v)))
            case None    => Left(McpError.MissingPathParameter(paramName))
          }
        }
      }

    EitherT.fromEither[Future](result)
  }

  private def extractQueryParams(inputSchema: Schema[?], inputJson: JsValue): Map[String, String] = {
    val queryFields = extractQueryFieldNames(inputSchema)

    inputJson
      .as[JsObject]
      .fields
      .collect {
        case (key, value) if queryFields.contains(key) && value != JsNull =>
          value
            .asOpt[String]
            .map(key -> _)
            .orElse(value.asOpt[Int].map(n => key -> n.toString))
            .orElse(value.asOpt[Boolean].map(b => key -> b.toString))
      }
      .flatten
      .toMap
  }

  private def extractQueryFieldNames(schema: Schema[?]): Set[String] = {
    import smithy4s.schema.Schema.*

    schema match {
      case StructSchema(shapeId, hints, fields, make) =>
        fields.flatMap { field =>
          field.hints.get(smithy.api.HttpQuery).map(_ => field.label)
        }.toSet
      case _                                          => Set.empty
    }
  }

  private def extractBody(inputSchema: Schema[?], inputJson: JsValue): Option[JsObject] = {
    val bodyFieldNames = extractBodyFieldNames(inputSchema)

    if (bodyFieldNames.nonEmpty) {
      // If there are explicit @httpPayload fields, extract those
      bodyFieldNames.headOption.flatMap { fieldName =>
        (inputJson \\ fieldName).collectFirst(_.asOpt[JsObject]).flatten
      }
    } else {
      // If there are no explicit @httpPayload fields, send the entire input as the body
      inputJson.asOpt[JsObject]
    }
  }

  private def extractBodyFieldNames(schema: Schema[?]): Set[String] = {
    import smithy4s.schema.Schema.*

    schema match {
      case StructSchema(shapeId, hints, fields, make) =>
        fields.flatMap { field =>
          field.hints.get(using smithy.api.HttpPayload).map(_ => field.label)
        }.toSet
      case _                                          => Set.empty
    }
  }

  private def executeRequest(
    toolName: String,
    method: String,
    path: String,
    queryParams: Map[String, String],
    bodyOpt: Option[JsObject],
    originalRequest: Request[?]
  )(using ExecutionContext): EitherT[Future, McpError, String] = {
    val queryString = if (queryParams.isEmpty) "" else "?" + queryParams.map { case (k, v) => s"$k=$v" }.mkString("&")
    val fullPath    = path + queryString

    val request = createHttpRequest(method, fullPath, queryParams, bodyOpt, originalRequest)

    EitherT(
      autoRouter.get().routes.lift(request) match {
        case Some(handler) =>
          val essentialAction = handler.asInstanceOf[play.api.mvc.EssentialAction]
          val accumulator     = essentialAction(request)

          val bodyByteString = bodyOpt.map { body =>
            org.apache.pekko.util.ByteString(Json.stringify(body).getBytes("UTF-8"))
          }.getOrElse(org.apache.pekko.util.ByteString.empty)

          accumulator
            .run(bodyByteString)
            .flatMap { result =>
              result.body.consumeData.map { byteString =>
                Right(byteString.utf8String)
              }
            }
            .recover { case e: Throwable =>
              Left(McpError.ToolExecutionError(toolName, e))
            }

        case None =>
          Future.successful(Left(McpError.NoRouteFound(method, fullPath)))
      }
    )
  }

  private def createHttpRequest(
    method: String,
    fullPath: String,
    queryParams: Map[String, String],
    bodyOpt: Option[JsObject],
    originalRequest: Request[?]
  ): Request[play.api.mvc.RawBuffer] = {
    import play.api.libs.Files.SingletonTemporaryFileCreator

    val bodyBytes      = bodyOpt.map(body => Json.stringify(body).getBytes("UTF-8")).getOrElse(Array.empty[Byte])
    val bodyByteString = org.apache.pekko.util.ByteString(bodyBytes)
    val rawBuffer      = play.api.mvc.RawBuffer(bodyBytes.length, SingletonTemporaryFileCreator, bodyByteString)

    val headers = originalRequest.headers.add("Content-Type" -> "application/json")

    play.api.mvc.request.RequestFactory.plain.createRequest(
      connection = play.api.mvc.request.RemoteConnection(
        remoteAddressString = "127.0.0.1",
        secure = false,
        clientCertificateChain = None: Option[Seq[java.security.cert.X509Certificate]]
      ),
      method = method,
      target = play.api.mvc.request.RequestTarget(
        uriString = fullPath,
        path = fullPath.split('?').headOption.getOrElse(fullPath),
        queryString = queryParams.map { case (k, v) => k -> Seq(v) }
      ),
      version = "HTTP/1.1",
      headers = headers,
      attrs = play.api.libs.typedmap.TypedMap.empty,
      body = rawBuffer
    )
  }
}
