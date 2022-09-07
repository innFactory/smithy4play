package de.innfactory.smithy4play.client

import smithy4s.Endpoint
import smithy4s.http.{ HttpEndpoint, PayloadError }

import scala.concurrent.{ ExecutionContext, Future }

class SmithyPlayClient[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _], F[_]](
  baseUri: String,
  service: smithy4s.Service[Alg, Op]
)(implicit executionContext: ExecutionContext, client: RequestClient) {

  def send[I, E, O, SI, SO](
    op: Op[I, E, O, SI, SO],
    authHeader: Option[String]
  ): Future[Either[SmithyPlayClientEndpointErrorResponse, SmithyPlayClientEndpointResponse[O]]] = {

    val (input, endpoint) = service.endpoint(op)
    HttpEndpoint
      .cast(endpoint)
      .map(httpEndpoint => new SmithyPlayClientEndpoint(endpoint, baseUri, authHeader, httpEndpoint, input).send())
      .get
  }

}
