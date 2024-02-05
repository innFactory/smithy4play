package de.innfactory.smithy4play.client

import cats.implicits.toBifunctorOps
import de.innfactory.smithy4play.ClientResponse
import smithy4s.Blob
import smithy4s.http.{CaseInsensitive, HttpEndpoint}

import scala.concurrent.{ExecutionContext, Future}

class SmithyPlayClient[Alg[_[_, _, _, _, _]], F[_]](
  baseUri: String,
  val service: smithy4s.Service[Alg],
  client: RequestClient,
  additionalSuccessCodes: List[Int] = List.empty
)(implicit executionContext: ExecutionContext) {

  def send[I, E, O, SI, SO](
    op: service.Operation[I, E, O, SI, SO],
    additionalHeaders: Option[Map[CaseInsensitive, Seq[String]]]
  ): ClientResponse[O] = {

    val endpoint = service.endpoint(op)
    HttpEndpoint
      .cast(endpoint.schema)
      .map(httpEndpoint =>
        new SmithyPlayClientEndpoint(
          endpoint = endpoint,
          baseUri = baseUri,
          additionalHeaders = additionalHeaders,
          additionalSuccessCodes = additionalSuccessCodes,
          httpEndpoint = httpEndpoint,
          input = service.input(op),
          client = client
        ).send()
      )
      .toOption
      .get
  }

}
