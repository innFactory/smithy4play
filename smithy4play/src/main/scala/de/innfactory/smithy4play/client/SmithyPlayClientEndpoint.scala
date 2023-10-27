package de.innfactory
package smithy4play
package client
import smithy4s.{ Endpoint, Schema }
import smithy4s.http.{ CaseInsensitive, CodecAPI, HttpEndpoint, Metadata, MetadataError, PayloadError }
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

private[smithy4play] class SmithyPlayClientEndpoint[Op[_, _, _, _, _], I, E, O, SI, SO](
  endpoint: Endpoint[Op, I, E, O, SI, SO],
  baseUri: String,
  additionalHeaders: Option[Map[String, Seq[String]]],
  additionalSuccessCodes: List[Int] = List.empty,
  httpEndpoint: HttpEndpoint[I],
  input: I,
  client: RequestClient
)(implicit executionContext: ExecutionContext) {

  private val inputSchema: Schema[I]  = endpoint.input
  private val outputSchema: Schema[O] = endpoint.output

  private val inputMetadataEncoder  =
    Metadata.Encoder.fromSchema(inputSchema)
  private val outputMetadataDecoder =
    Metadata.PartialDecoder.fromSchema(outputSchema)
  private val inputHasBody          =
    Metadata.TotalDecoder.fromSchema(inputSchema).isEmpty

  def send(
  ): ClientResponse[O] = {
    val metadata           = inputMetadataEncoder.encode(input)
    val path               = buildPath(metadata)
    val headers            = metadata.headers.map(x => (x._1.toString.toLowerCase, x._2))
    val headersWithAuth    = if (additionalHeaders.isDefined) headers.combine(additionalHeaders.get) else headers
    val code               = httpEndpoint.code
    val codecApi: CodecAPI = CodecUtils.extractCodec(headers)
    val send               = client.send(httpEndpoint.method.toString, path, headersWithAuth, _)
    val response           = if (inputHasBody) {
      val codec       = codecApi.compileCodec(inputSchema)
      val bodyEncoded = codecApi.writeToArray(codec, input)
      send(Some(bodyEncoded))
    } else send(None)
    decodeResponse(response, code)
  }

  private def decodeResponse(
    response: Future[SmithyClientResponse],
    expectedCode: Int
  ): ClientResponse[O] =
    for {
      res     <- response
      metadata = Metadata(headers = res.headers.map(headers => (CaseInsensitive(headers._1), headers._2)))
      output  <- if ((additionalSuccessCodes :+ expectedCode).contains(res.statusCode))
                   handleSuccess(metadata, res)
                 else handleError(res)
    } yield output

  def handleSuccess(metadata: Metadata, response: SmithyClientResponse) = {
    val headers = response.headers.map(x => (x._1.toLowerCase, x._2))
    val output  = outputMetadataDecoder.total match {
      case Some(totalDecoder) =>
        totalDecoder.decode(metadata)
      case None               =>
        for {
          metadataPartial <- outputMetadataDecoder.decode(metadata)
          codecApi         = CodecUtils.extractCodec(headers)
          bodyPartial     <-
            codecApi.decodeFromByteArrayPartial(codecApi.compileCodec(outputSchema), response.body.get)
          output          <- metadataPartial.combineCatch(bodyPartial)
        } yield output
    }
    Future(
      output.map(o => SmithyPlayClientEndpointResponse(Some(o), headers, response.statusCode)).left.map {
        case error: PayloadError  =>
          SmithyPlayClientEndpointErrorResponse(error.expected.getBytes, response.statusCode)
        case error: MetadataError =>
          SmithyPlayClientEndpointErrorResponse(error.getMessage().getBytes(), response.statusCode)
      }
    )
  }
  def handleError(response: SmithyClientResponse)                       = Future(
    Left {
      SmithyPlayClientEndpointErrorResponse(
        response.body.getOrElse(Array.emptyByteArray),
        response.statusCode
      )
    }
  )

  def buildPath(metadata: Metadata): String =
    baseUri + httpEndpoint.path(input).mkString("/") + metadata.queryFlattened
      .map(s => s._1 + "=" + s._2)
      .mkString("?", "&", "")

}
