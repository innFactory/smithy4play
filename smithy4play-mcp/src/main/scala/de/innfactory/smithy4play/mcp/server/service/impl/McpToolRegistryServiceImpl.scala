package de.innfactory.smithy4play.mcp.server.service.impl

import com.google.inject.{ Inject, Singleton }
import de.innfactory.smithy4play.mcp.server.domain.{ McpEndpointInfo, McpError, Tool }
import de.innfactory.smithy4play.mcp.server.service.{
  McpToolRegistryService,
  SchemaBuilderService,
  ServiceDiscoveryService
}
import org.apache.pekko.stream.Materializer
import play.api.Application
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.mvc.{ ControllerComponents, Request }
import smithy4s.{ Document, Endpoint, Schema, Service }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import cats.data.EitherT
import cats.implicits.*
import de.innfactory.smithy4play.mcp.AutoRouterWithMcp
import de.innfactory.smithy4play.mcp.common.MCPCommon.ContentTypes.APPLICATION_JSON
import de.innfactory.smithy4play.mcp.server.util.DocumentConverter.documentToJsValue
import de.innfactory.smithy4play.mcp.server.util.SchemaExtractor

import javax.inject.Provider

@Singleton
class McpToolRegistryServiceImpl @Inject() (
  autoRouter: Provider[AutoRouterWithMcp],
  serviceDiscovery: ServiceDiscoveryService,
  schemaBuilder: SchemaBuilderService
)(using ExecutionContext, ControllerComponents, Application)
    extends McpToolRegistryService {

  private given Materializer = summon[Application].materializer

  private lazy val mcpEndpoints: List[McpEndpointInfo] = discoverMcpEndpoints()

  private def discoverMcpEndpoints(): List[McpEndpointInfo] = {
    val services: List[Service[?]] = serviceDiscovery.discoverServices()

    services.flatMap { service =>
      val controllerName = service.id.name

      service.endpoints.flatMap { endpoint =>
        endpoint.hints.get(using de.innfactory.smithy4play.mcp.ExposeMcp).map { exposeMcp =>
          val operationName = endpoint.id.name
          val toolName      = s"$controllerName.$operationName"

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

  override def getAllTools: List[Tool] =
    mcpEndpoints.map { info =>
      val inputSchema  = schemaBuilder.buildInput(info.inputSchema)
      val outputSchema = schemaBuilder.buildInput(info.outputSchema)
      Tool(
        name = info.toolName,
        description = info.description,
        inputSchema = inputSchema,
        outputSchema = outputSchema
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
          val seqValues: scala.collection.Seq[JsValue] = inputJson \\ paramName
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

  private def extractQueryParams(inputSchema: Schema[?], inputJson: JsValue): Map[String, String] =
    SchemaExtractor.extractQueryParams(inputSchema, inputJson)

  private def extractBody(inputSchema: Schema[?], inputJson: JsValue): Option[JsObject] = {
    val bodyFieldNames = SchemaExtractor.extractBodyFieldNames(inputSchema)

    if (bodyFieldNames.nonEmpty) {
      bodyFieldNames.headOption.flatMap { fieldName =>
        (inputJson \\ fieldName).collectFirst(_.asOpt[JsObject]).flatten
      }
    } else {
      inputJson.asOpt[JsObject]
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
    def urlEncode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)
    val queryString                  =
      if (queryParams.isEmpty) ""
      else "?" + queryParams.map { case (k, v) => s"${urlEncode(k)}=${urlEncode(v)}" }.mkString("&")
    val fullPath                     = path + queryString

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
    val headers        = originalRequest.headers.add("Content-Type" -> APPLICATION_JSON)

    play.api.mvc.request.RequestFactory.plain.createRequest(
      connection = play.api.mvc.request.RemoteConnection(
        remoteAddressString = "service.internal",
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
