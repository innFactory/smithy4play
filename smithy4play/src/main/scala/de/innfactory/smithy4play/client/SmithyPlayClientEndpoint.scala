package de.innfactory
package smithy4play
package client

import cats.implicits._
import smithy4s.codecs.{ BlobEncoder, PayloadDecoder, PayloadEncoder, PayloadError }
import smithy4s.http._
import smithy4s.json.Json.payloadCodecs
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.xml.Xml
import smithy4s.{ Blob, Endpoint, Schema }

import scala.concurrent.{ ExecutionContext, Future }

private[smithy4play] class SmithyPlayClientEndpoint[Op[_, _, _, _, _], I, E, O, SI, SO](
  endpoint: Endpoint[Op, I, E, O, SI, SO],
  baseUri: String,
  additionalHeaders: Option[Map[CaseInsensitive, Seq[String]]],
  additionalSuccessCodes: List[Int],
  httpEndpoint: HttpEndpoint[I],
  input: I,
  client: RequestClient
)(implicit executionContext: ExecutionContext) {

  private implicit val inputSchema: Schema[I]  = endpoint.input
  private implicit val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataEncoder =
    Metadata.Encoder.fromSchema(inputSchema)

  def send(
  ): ClientResponse[O] = {
    val metadata        = inputMetadataEncoder.encode(input)
    val path            = buildPath(metadata)
    val headers         = metadata.headers
    val contentTypeOpt  = headers.get(CaseInsensitive("content-type"))
    val contentType     = contentTypeOpt.getOrElse(Seq("application/json"))
    val headersWithAuth = if (additionalHeaders.isDefined) headers.combine(additionalHeaders.get) else headers
    val headersCombined =
      if (contentTypeOpt.isDefined) headersWithAuth
      else headers.combine(Map(CaseInsensitive("content-type") -> contentType))
    println("headers2", headersWithAuth)
    val code            = httpEndpoint.code
    val response        =
      client.send(
        httpEndpoint.method.toString,
        path,
        headersCombined,
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
      _       = println(res, expectedCode, additionalSuccessCodes)
      output <- if ((additionalSuccessCodes :+ expectedCode).contains(res.statusCode)) {
                  println("success")
                  handleSuccess(res)
                } else handleError(res)
    } yield output

  def handleSuccess(response: HttpResponse[Blob]): ClientResponse[O]       =
    Future {
      val headers     = response.headers.map(x => (x._1, x._2))
      val contentType = headers.getOrElse(CaseInsensitive("content-type"), Seq("application/json"))
      val codec       = CodecDecider.httpMessageDecoder(contentType)
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
