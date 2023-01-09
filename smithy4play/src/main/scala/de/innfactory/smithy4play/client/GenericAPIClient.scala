package de.innfactory.smithy4play.client

import cats.data.Kleisli
import de.innfactory.smithy4play.{ ClientResponse, RunnableClientRequest }
import smithy4s.Service
import smithy4s.kinds.{ FunctorK, FunctorK5, Kind1, PolyFunction5 }
import scala.concurrent.ExecutionContext

private class GenericAPIClient[Alg[_[_, _, _, _, _]]](
  service: Service[Alg],
  client: RequestClient
)(implicit ec: ExecutionContext) {

  private val smithyPlayClient = new SmithyPlayClient("/", service, client)

  /* Takes a service and creates a Transformation[Op, ClientRequest] */
  private def transformer(): Alg[Kind1[RunnableClientRequest]#toKind5] =
    smithyPlayClient.service.fromPolyFunction(this.opToResponse())

  private def transformer(additionalHeaders: Option[Map[String, Seq[String]]]): Alg[Kind1[ClientResponse]#toKind5] =
    smithyPlayClient.service.fromPolyFunction(this.opToResponse(additionalHeaders))

  /* uses the SmithyPlayClient to transform a Operation to a ClientResponse */
  private def opToResponse(): PolyFunction5[smithyPlayClient.service.Operation, Kind1[RunnableClientRequest]#toKind5] =
    new PolyFunction5[smithyPlayClient.service.Operation, Kind1[RunnableClientRequest]#toKind5] {
      override def apply[I, E, O, SI, SO](
        fa: smithyPlayClient.service.Operation[I, E, O, SI, SO]
      ): Kleisli[ClientResponse, Option[Map[String, Seq[String]]], O] =
        Kleisli[ClientResponse, Option[Map[String, Seq[String]]], O] { additionalHeaders =>
          smithyPlayClient.send(fa, additionalHeaders)
        }
    }

  private def opToResponse(
    additionalHeaders: Option[Map[String, Seq[String]]]
  ): PolyFunction5[smithyPlayClient.service.Operation, Kind1[ClientResponse]#toKind5] =
    new PolyFunction5[smithyPlayClient.service.Operation, Kind1[ClientResponse]#toKind5] {
      override def apply[I, E, O, SI, SO](
        fa: smithyPlayClient.service.Operation[I, E, O, SI, SO]
      ): Kind1[ClientResponse]#toKind5[I, E, O, SI, SO] =
        smithyPlayClient.send(fa, additionalHeaders)
    }
}

object GenericAPIClient {

  implicit class EnhancedGenericAPIClient[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](service: Service[Alg]) {
    def withClientAndHeaders(
      client: RequestClient,
      additionalHeaders: Option[Map[String, Seq[String]]]
    )(implicit ec: ExecutionContext) = apply(service, additionalHeaders, client)

    def withClient(
      client: RequestClient
    )(implicit ec: ExecutionContext) = apply(service, client)
  }
  def apply[Alg[_[_, _, _, _, _]]](
    serviceI: Service[Alg],
    additionalHeaders: Option[Map[String, Seq[String]]] = None,
    client: RequestClient
  )(implicit ec: ExecutionContext): Alg[Kind1[ClientResponse]#toKind5] =
    new GenericAPIClient(serviceI, client).transformer(additionalHeaders)

  def apply[Alg[_[_, _, _, _, _]]](
    serviceI: Service[Alg],
    client: RequestClient
  )(implicit ec: ExecutionContext): Alg[Kind1[RunnableClientRequest]#toKind5] =
    new GenericAPIClient(serviceI, client).transformer()

}
