package de.innfactory
package smithy4play
package client
import cats.implicits._
import smithy4s.codecs.{ PayloadError, StringAndBlobCodecs }
import smithy4s.http.{ CaseInsensitive, HttpEndpoint, HttpResponse, Metadata, MetadataError }
import smithy4s.json.Json
import smithy4s.json.Json.payloadCodecs
import smithy4s.schema.CachedSchemaCompiler
import smithy4s.xml.internals.XmlEncoder
import smithy4s.xml.{ Xml, XmlDecodeError, XmlDocument }
import smithy4s.{ Blob, Endpoint, Schema }

import scala.concurrent.{ ExecutionContext, Future }

private[smithy4play] class SmithyPlayClientEndpoint[Op[_, _, _, _, _], I, E, O, SI, SO](
  endpoint: Endpoint[Op, I, E, O, SI, SO],
  baseUri: String,
  additionalHeaders: Option[Map[String, Seq[String]]],
  additionalSuccessCodes: List[Int],
  httpEndpoint: HttpEndpoint[I],
  input: I,
  client: RequestClient
)(implicit executionContext: ExecutionContext) {

  private implicit val inputSchema: Schema[I]  = endpoint.input
  private implicit val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataEncoder  =
    Metadata.Encoder.fromSchema(inputSchema)
  private val outputMetadataDecoder =
    Metadata.Decoder.fromSchema(outputSchema)

  def send(
  ): ClientResponse[O] = {
    val metadata        = inputMetadataEncoder.encode(input)
    val path            = buildPath(metadata)
    val headers         = metadata.headers.map(x => (x._1.toString.toLowerCase, x._2))
    val contentType     = headers.getOrElse("content-type", Seq("application/json"))
    val headersWithAuth = if (additionalHeaders.isDefined) headers.combine(additionalHeaders.get) else headers
    val code            = httpEndpoint.code
    val response        =
      client.send(
        httpEndpoint.method.toString,
        path,
        headersWithAuth.combine(Map("content-type" -> contentType)),
        writeInputToBlob(input, contentType)
      )
    decodeResponse(response, code)
  }

  private def writeInputToBlob(input: I, contentType: Seq[String]): Blob =
    // TODO: Use Correct Encoders for Json and Xml and fix StringAndBlobCodecs
    (contentType match {
      case Seq("application/json") => Some(payloadCodecs.encoders.fromSchema(inputSchema))
      case Seq("application/xml")  => Some(Xml.encoders.fromSchema(inputSchema))
      case _                       =>
        StringAndBlobCodecs.encoders
          .fromSchema(inputSchema)

    }).map(_.encode(input)).getOrElse(Blob.empty)

  private def decodeResponse(
    response: Future[HttpResponse[Blob]],
    expectedCode: Int
  ): ClientResponse[O] =
    for {
      res    <- response
      output <- if ((additionalSuccessCodes :+ expectedCode).contains(res.statusCode))
                  handleSuccess(res)
                else handleError(res)
    } yield output

  def handleSuccess(response: HttpResponse[Blob]): ClientResponse[O]       = {
    val headers     = response.headers.map(x => (x._1, x._2))
    val contentType = headers.getOrElse(CaseInsensitive("content-type"), Seq("application/json"))
    val codec       = contentType match {
      case "application/json" :: _ => Some(payloadCodecs.decoders.fromSchema(outputSchema))
      case "application/xml" :: _  => Some(Xml.decoders.fromSchema(outputSchema))
      case _                       => StringAndBlobCodecs.decoders.fromSchema(outputSchema)
    }
    Future(
      codec
        .map(_.decode(response.body))
        .map(o =>
          o.map(res => HttpResponse(response.statusCode, headers, res)).leftMap { error =>
            SmithyPlayClientEndpointErrorResponse(error.expected.getBytes, response.statusCode)
          }
        )
        .getOrElse(Left(SmithyPlayClientEndpointErrorResponse("No Decoder found".getBytes, response.statusCode)))
    )
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
