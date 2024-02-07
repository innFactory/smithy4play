package de.innfactory
package smithy4play
package client

import alloy.SimpleRestJson
import aws.protocols.RestXml
import cats.implicits._
import play.api.http.MimeTypes
import smithy4s.codecs.PayloadError
import smithy4s.http._
import smithy4s.{ Blob, Endpoint, Hints, Schema }

import scala.concurrent.{ ExecutionContext, Future }

private[smithy4play] class SmithyPlayClientEndpoint[Op[_, _, _, _, _], I, E, O, SI, SO](
  endpoint: Endpoint[Op, I, E, O, SI, SO],
  serviceHints: Hints,
  baseUri: String,
  additionalHeaders: Option[Map[CaseInsensitive, Seq[String]]],
  additionalSuccessCodes: List[Int],
  httpEndpoint: HttpEndpoint[I],
  input: I,
  client: RequestClient
)(implicit executionContext: ExecutionContext) {

  private implicit val inputSchema: Schema[I]  = endpoint.input
  private implicit val outputSchema: Schema[O] = endpoint.output

  private val serviceContentType: String = serviceHints.toMimeType
  private val inputMetadataEncoder       =
    Metadata.Encoder.fromSchema(HttpRestSchema.OnlyMetadata(inputSchema).schema)
  private val contentTypeKey             = CaseInsensitive("content-type")

  def send(
  ): ClientResponse[O] = {
    val metadata        = inputMetadataEncoder.encode(input)
    val path            = buildPath(metadata)
    val headers         = metadata.headers
    val contentTypeOpt  = headers.get(contentTypeKey)
    val contentType     = contentTypeOpt.getOrElse(Seq(serviceContentType))
    val headersWithAuth = if (additionalHeaders.isDefined) headers.combine(additionalHeaders.get) else headers
    val code            = httpEndpoint.code
    val response        =
      client.send(
        httpEndpoint.method.toString,
        path,
        headersWithAuth.updated(contentTypeKey, contentType),
        writeInputToBlob(input, contentType)
      )
    decodeResponse(response, code)
  }

  private def writeInputToBlob(input: I, contentType: Seq[String]): EndpointRequest = {
    val codecs = CodecDecider.requestEncoder(contentType)
    codecs.fromSchema(inputSchema).write(PlayHttpRequest(Blob.empty, Metadata.empty), input)
  }

  private def decodeResponse(
    response: Future[HttpResponse[Blob]],
    expectedCode: Int
  ): ClientResponse[O] =
    for {
      res    <- response
      output <- if ((additionalSuccessCodes :+ expectedCode).contains(res.statusCode)) {
                  handleSuccess(res)
                } else handleError(res)
    } yield output

  def handleSuccess(response: HttpResponse[Blob]): ClientResponse[O]       =
    Future {
      val headers     = response.headers.map(x => (x._1, x._2))
      val contentType = headers.getOrElse(contentTypeKey, Seq(serviceContentType))
      val codec       = CodecDecider.httpResponseDecoder(contentType)

      codec
        .fromSchema(outputSchema)
        .decode(response)
        .map(o => HttpResponse(response.statusCode, headers, o))
        .leftMap {
          case error: PayloadError  =>
            SmithyPlayClientEndpointErrorResponse(error.expected.getBytes(), response.statusCode)
          case error: MetadataError =>
            SmithyPlayClientEndpointErrorResponse(error.getMessage().getBytes(), response.statusCode)
        }
    }
  private def handleError(response: HttpResponse[Blob]): ClientResponse[O] = Future(
    Left {
      SmithyPlayClientEndpointErrorResponse(
        response.body.toArray,
        response.statusCode
      )
    }
  )

  def buildPath(metadata: Metadata): String =
    baseUri + httpEndpoint.path(input).mkString("/") + metadata.queryFlattened
      .map(s => s._1 + "=" + s._2)
      .mkString("?", "&", "")

}
