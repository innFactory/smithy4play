package io.cleverone.mcp.server.service.impl

import cats.data.EitherT
import cats.implicits.*
import io.cleverone.mcp.server.domain.{McpError, McpEndpointInfo, Tool}
import io.cleverone.mcp.server.service.{InputSchemaBuildingService, McpToolRegistryService, ServiceDiscoveryService}
import io.cleverone.mcp.server.util.{DocumentConverter, ParameterExtractor, SchemaExtractor}
import play.api.libs.json.{Json, JsObject, JsValue}
import play.api.mvc.Request
import smithy4s.{Document, Endpoint, Schema, Service}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Base implementation of McpToolRegistryService
 *
 * This class handles the core logic of discovering tools and executing them.
 * Applications should extend this class and provide implementations for the abstract methods.
 */
abstract class BaseToolRegistryServiceImpl(
    protected val serviceDiscovery: ServiceDiscoveryService,
    protected val schemaBuildingService: InputSchemaBuildingService
)(using protected val ec: ExecutionContext)
    extends McpToolRegistryService {

  private lazy val mcpEndpoints: List[McpEndpointInfo] = discoverMcpEndpoints()

  /**
   * Execute an HTTP request. Must be implemented by subclasses.
   *
   * @param toolName The name of the tool being executed
   * @param method The HTTP method
   * @param path The request path
   * @param queryParams Query parameters
   * @param bodyOpt Optional request body
   * @param originalRequest The original request
   * @return Either an error or the response as a string
   */
  protected def executeRequest(
      toolName: String,
      method: String,
      path: String,
      queryParams: Map[String, String],
      bodyOpt: Option[JsObject],
      originalRequest: Request[?]
  ): EitherT[Future, McpError, String]

  private def discoverMcpEndpoints(): List[McpEndpointInfo] = {
    val services: List[Service[?]] = serviceDiscovery.discoverServices()

    services.flatMap { service =>
      service.endpoints.flatMap { endpoint =>
        endpoint.hints.get(getExposeMcpHint()).map { exposeMcp =>
          val operationName = endpoint.id.name
          val description = getDescriptionFromHint(exposeMcp)

          McpEndpointInfo(
            toolName = operationName,
            description = description,
            endpoint = endpoint,
            inputSchema = endpoint.input,
            outputSchema = endpoint.output
          )
        }
      }
    }
  }

  /**
   * Get the ExposeMcp hint type. Override this if using a custom hint.
   * Should return the Class or type representation of your hint.
   */
  protected def getExposeMcpHint(): Any

  /**
   * Extract description from the ExposeMcp hint. Override as needed.
   */
  protected def getDescriptionFromHint(hint: Any): Option[String] = None

  private def schemaToToolInputDoc(inputSchema: Schema[?], outputSchema: Schema[?]): Document = {
    val input = schemaBuildingService.build(inputSchema, outputSchema)
    val output = schemaBuildingService.build(outputSchema, inputSchema)

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
      case _ => Document.obj()
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
      endpointInfo <- findEndpoint(name)
      document <- validateArguments(name, arguments)
      jsonString = DocumentConverter.documentToJsonString(document)
      inputJson <- parseJson(jsonString)
      httpInfo <- extractHttpInfo(endpointInfo)
      pathAndParams <- extractPathParameters(httpInfo._1, inputJson)
      queryParams = SchemaExtractor.extractQueryParams(endpointInfo.inputSchema, inputJson)
      bodyOpt = SchemaExtractor.extractBody(endpointInfo.inputSchema, inputJson)
      result <- executeRequest(
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
      scala.util.Try(Json.parse(jsonString)).toEither.leftMap(e => McpError.InvalidJsonDocument(e.getMessage))
    )

  private def extractHttpInfo(endpointInfo: McpEndpointInfo): EitherT[Future, McpError, (String, String)] =
    EitherT.fromOption[Future](
      endpointInfo.endpoint.hints.get(smithy.api.Http).map { httpHint =>
        (httpHint.uri.value, httpHint.method.value)
      },
      McpError.MissingHttpHint(endpointInfo.toolName)
    )

  private def extractPathParameters(
      uriTemplate: String,
      inputJson: JsValue
  ): EitherT[Future, McpError, (String, Map[String, String])] =
    EitherT.fromEither[Future](
      ParameterExtractor.extractPathParameters(uriTemplate, inputJson).leftMap(e => McpError.InvalidArguments("", e))
    )
}

