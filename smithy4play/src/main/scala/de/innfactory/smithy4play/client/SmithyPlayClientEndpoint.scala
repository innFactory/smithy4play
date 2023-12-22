package de.innfactory
package smithy4play
package client
import cats.implicits._
import smithy4s.codecs.{ PayloadError, StringAndBlobCodecs }
import smithy4s.http.{ CaseInsensitive, HttpEndpoint, Metadata, MetadataError }
import smithy4s.json.Json
import smithy4s.xml.{ Xml, XmlDecodeError }
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
    val headersWithAuth = if (additionalHeaders.isDefined) headers.combine(additionalHeaders.get) else headers
    val code            = httpEndpoint.code
    val response        = client.send(httpEndpoint.method.toString, path, headersWithAuth, Json.writeBlob(input))
    decodeResponse(response, code)
  }

  private def decodeResponse(
    response: Future[SmithyClientResponse],
    expectedCode: Int
  ): ClientResponse[O] =
    for {
      res    <- response
      output <- if ((additionalSuccessCodes :+ expectedCode).contains(res.statusCode))
                  handleSuccess(res)
                else handleError(res)
    } yield output

  def handleSuccess(response: SmithyClientResponse)       = {
    val headers     = response.headers.map(x => (x._1.toLowerCase, x._2))
    val contentType = headers.getOrElse("content-type", Seq("application/json"))
    val codec       = contentType match {
      case "application/json" :: _ => (o: Blob) => Json.read(o)(outputSchema)
      case "application/xml" :: _  => (o: Blob) => Xml.read(o)(outputSchema)
      case _                       => (o: Blob) => StringAndBlobCodecs.decoders.fromSchema(outputSchema).get.decode(o)
    }
    Future(
      codec(response.body).map(o => SmithyPlayClientEndpointResponse(Some(o), headers, response.statusCode)).leftMap {
        case error: PayloadError   =>
          SmithyPlayClientEndpointErrorResponse(error.expected.getBytes, response.statusCode)
        case error: XmlDecodeError =>
          SmithyPlayClientEndpointErrorResponse(error.getMessage().getBytes(), response.statusCode)
      }
    )
  }
  private def handleError(response: SmithyClientResponse) = Future(
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
