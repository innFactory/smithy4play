package de.innfactory.smithy4play.client

import cats.data.Kleisli
import de.innfactory.smithy4play.{ClientResponse, RunnableClientRequest}
import smithy4s.Service
import smithy4s.http.CaseInsensitive
import smithy4s.kinds.{FunctorK, FunctorK5, Kind1, PolyFunction5}

import scala.concurrent.ExecutionContext

private class GenericAPIClient[Alg[_[_, _, _, _, _]]](
  service: Service[Alg],
  client: RequestClient,
  additionalSuccessCodes: List[Int] = List.empty
)(implicit ec: ExecutionContext) {

  private val smithyPlayClient = new SmithyPlayClient("/", service, client, additionalSuccessCodes)

  /* Takes a service and creates a Transformation[Op, ClientRequest] */
  private def transformer(): Alg[Kind1[RunnableClientRequest]#toKind5] =
    smithyPlayClient.service.fromPolyFunction(this.opToResponse())

  private def transformer(additionalHeaders: Option[Map[CaseInsensitive, Seq[String]]]): Alg[Kind1[ClientResponse]#toKind5] =
    smithyPlayClient.service.fromPolyFunction(this.opToResponse(additionalHeaders))

  /* uses the SmithyPlayClient to transform a Operation to a ClientResponse */
  private def opToResponse(): PolyFunction5[smithyPlayClient.service.Operation, Kind1[RunnableClientRequest]#toKind5] =
    new PolyFunction5[smithyPlayClient.service.Operation, Kind1[RunnableClientRequest]#toKind5] {
      override def apply[I, E, O, SI, SO](
        fa: smithyPlayClient.service.Operation[I, E, O, SI, SO]
      ): Kleisli[ClientResponse, Option[Map[CaseInsensitive, Seq[String]]], O] =
        Kleisli[ClientResponse, Option[Map[CaseInsensitive, Seq[String]]], O] { additionalHeaders =>
          smithyPlayClient.send(fa, additionalHeaders)
        }
    }

  private def opToResponse(
    additionalHeaders: Option[Map[CaseInsensitive, Seq[String]]]
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
      additionalHeaders: Option[Map[CaseInsensitive, Seq[String]]],
      additionalSuccessCodes: List[Int] = List.empty
    )(implicit ec: ExecutionContext): Alg[Kind1[ClientResponse]#toKind5] =
      apply(service, additionalHeaders, additionalSuccessCodes, client)

    def withClient(
      client: RequestClient,
      additionalSuccessCodes: List[Int] = List.empty
    )(implicit ec: ExecutionContext): Alg[Kind1[RunnableClientRequest]#toKind5] =
      apply(service, client, additionalSuccessCodes)

  }

  def apply[Alg[_[_, _, _, _, _]]](
    serviceI: Service[Alg],
    client: RequestClient,
    additionalSuccessCodes: List[Int]
  )(implicit ec: ExecutionContext): Alg[Kind1[RunnableClientRequest]#toKind5] =
    new GenericAPIClient(serviceI, client, additionalSuccessCodes).transformer()

  def apply[Alg[_[_, _, _, _, _]]](
    serviceI: Service[Alg],
    additionalHeaders: Option[Map[CaseInsensitive, Seq[String]]],
    additionalSuccessCodes: List[Int],
    client: RequestClient
  )(implicit ec: ExecutionContext): Alg[Kind1[ClientResponse]#toKind5] =
    new GenericAPIClient(serviceI, client, additionalSuccessCodes).transformer(additionalHeaders)

}
