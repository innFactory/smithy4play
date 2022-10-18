package de.innfactory.smithy4play.client

import de.innfactory.smithy4play.{ ClientRequest, ClientResponse }
import smithy4s.{ Service, Transformation }

import scala.concurrent.ExecutionContext

private class GenericAPIClient[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
  service: Service[Alg, Op],
  client: RequestClient
)(implicit ec: ExecutionContext) {

  private val smithyPlayClient = new SmithyPlayClient("/", service, client)

  /* Takes a service and creates a Transformation[Op, ClientRequest] */
  private def transformer(additionalHeaders: Option[Map[String, Seq[String]]]): Alg[ClientRequest] =
    service.transform(this.opToResponse(additionalHeaders))

  /* uses the SmithyPlayClient to transform a Operation to a ClientResponse */
  private def opToResponse(additionalHeaders: Option[Map[String, Seq[String]]]): Transformation[Op, ClientRequest] =
    new Transformation[Op, ClientRequest] {
      def apply[I, E, O, SI, SO](op: Op[I, E, O, SI, SO]): ClientResponse[O] =
        smithyPlayClient.send(op, additionalHeaders)
    }
}

object GenericAPIClient {

  implicit class EnhancedGenericAPIClient[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](service: Service[Alg, Op]) {
    def withClient(
      client: RequestClient,
      additionalHeaders: Option[Map[String, Seq[String]]] = None
    )(implicit ec: ExecutionContext) = apply(service, additionalHeaders, client)
  }
  def apply[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
    serviceI: Service[Alg, Op],
    additionalHeaders: Option[Map[String, Seq[String]]] = None,
    client: RequestClient
  )(implicit ec: ExecutionContext): Alg[ClientRequest] =
    new GenericAPIClient(serviceI, client).transformer(additionalHeaders)

}
