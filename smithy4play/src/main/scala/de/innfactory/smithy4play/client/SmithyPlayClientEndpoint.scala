package de.innfactory
package smithy4play
package client
import smithy4s.http.json.codecs
import smithy4s.{ Endpoint, HintMask, Schema }
import smithy4s.http.{ CaseInsensitive, CodecAPI, HttpEndpoint, Metadata, MetadataError, PayloadError }
import cats.implicits._
import smithy4s.internals.InputOutput

import scala.concurrent.{ ExecutionContext, Future }

private[smithy4play] class SmithyPlayClientEndpoint[Op[_, _, _, _, _], I, E, O, SI, SO](
  endpoint: Endpoint[Op, I, E, O, SI, SO],
  baseUri: String,
  additionalHeaders: Option[Map[String, Seq[String]]],
  httpEndpoint: HttpEndpoint[I],
  input: I
)(implicit executionContext: ExecutionContext, client: RequestClient) {

  private val codecs: codecs =
    smithy4s.http.json.codecs(smithy4s.api.SimpleRestJson.protocol.hintMask ++ HintMask(InputOutput))

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
    val headers            = metadata.headers.map(x => (x._1.toString, x._2))
    val headersWithAuth    = if (additionalHeaders.isDefined) headers.combine(additionalHeaders.get) else headers
    val code               = httpEndpoint.code
    val codecApi: CodecAPI = extractCodec(headers)
    val send               = client.send(httpEndpoint.method.toString, path, headersWithAuth, _)
    val response           = if (inputHasBody) {
      val codec       = codecApi.compileCodec(inputSchema)
      val bodyEncoded = codecApi.writeToArray(codec, input)
      send(Some(bodyEncoded))
    } else send(None)
    decodeResponse(response, code)
  }

  private def extractCodec(headers: Map[String, Seq[String]]): CodecAPI = {
    val contentType =
      headers.getOrElse("Content-Type", List("application/json"))
    val codecApi    = contentType match {
      case List("application/json") => codecs
      case _                        => CodecAPI.nativeStringsAndBlob(codecs)
    }
    codecApi
  }

  private def decodeResponse(
    response: Future[SmithyClientResponse],
    expectedCode: Int
  ): ClientResponse[O] =
    for {
      res     <- response
      metadata = Metadata(headers = res.headers.map(headers => (CaseInsensitive(headers._1), headers._2)))
      output  <- if (res.statusCode == expectedCode) handleSuccess(metadata, res, expectedCode)
                 else handleError(res, expectedCode)
    } yield output

  def handleSuccess(metadata: Metadata, response: SmithyClientResponse, expectedCode: Int) = {
    val headers = response.headers
    val output  = outputMetadataDecoder.total match {
      case Some(totalDecoder) =>
        totalDecoder.decode(metadata)
      case None               =>
        for {
          metadataPartial <- outputMetadataDecoder.decode(metadata)
          codecApi         = extractCodec(headers)
          bodyPartial     <-
            codecApi.decodeFromByteArrayPartial(codecApi.compileCodec(outputSchema), response.body.get)
        } yield metadataPartial.combine(bodyPartial)
    }
    Future(
      output.map(o => SmithyPlayClientEndpointResponse(Some(o), headers, response.statusCode, expectedCode)).left.map {
        case error: PayloadError  =>
          SmithyPlayClientEndpointErrorResponse(error.expected.getBytes, response.statusCode, expectedCode)
        case error: MetadataError =>
          SmithyPlayClientEndpointErrorResponse(error.getMessage().getBytes(), response.statusCode, expectedCode)
      }
    )
  }
  def handleError(response: SmithyClientResponse, expectedCode: Int)                       = Future(
    Left {
      SmithyPlayClientEndpointErrorResponse(
        response.body.getOrElse(Array.emptyByteArray),
        response.statusCode,
        expectedCode
      )
    }
  )

  def buildPath(metadata: Metadata): String =
    baseUri + httpEndpoint.path(input).mkString("/") + metadata.queryFlattened
      .map(s => s._1 + "=" + s._2)
      .mkString("?", "&", "")

}
