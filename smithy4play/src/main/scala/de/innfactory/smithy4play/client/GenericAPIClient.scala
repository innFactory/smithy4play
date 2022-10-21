package de.innfactory.smithy4play.client

import cats.data.Kleisli
import de.innfactory.smithy4play.{ ClientRequest, ClientResponse, RunnableClientRequest }
import smithy4s.{ Service, Transformation }

import scala.concurrent.ExecutionContext

private class GenericAPIClient[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
  service: Service[Alg, Op],
  client: RequestClient
)(implicit ec: ExecutionContext) {

  private val smithyPlayClient = new SmithyPlayClient("/", service, client)

  /* Takes a service and creates a Transformation[Op, ClientRequest] */
  private def transformer(): Alg[RunnableClientRequest] =
    service.transform(this.opToResponse())

  private def transformer(additionalHeaders: Option[Map[String, Seq[String]]]): Alg[ClientRequest] =
    service.transform(this.opToResponse(additionalHeaders))

  /* uses the SmithyPlayClient to transform a Operation to a ClientResponse */
  private def opToResponse(): Transformation[Op, RunnableClientRequest] =
    new Transformation[Op, RunnableClientRequest] {
      override def apply[I, E, O, SI, SO](fa: Op[I, E, O, SI, SO]): RunnableClientRequest[I, E, O, SI, SO] =
        Kleisli[ClientResponse, Option[Map[String, Seq[String]]], O] { additionalHeaders =>
          smithyPlayClient.send(fa, additionalHeaders)
        }
    }

  private def opToResponse(additionalHeaders: Option[Map[String, Seq[String]]]): Transformation[Op, ClientRequest] =
    new Transformation[Op, ClientRequest] {
      override def apply[I, E, O, SI, SO](fa: Op[I, E, O, SI, SO]): ClientRequest[I, E, O, SI, SO] =
        smithyPlayClient.send(fa, additionalHeaders)
    }
}

object GenericAPIClient {

  implicit class EnhancedGenericAPIClient[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](service: Service[Alg, Op]) {
    def withClientAndHeaders(
      client: RequestClient,
      additionalHeaders: Option[Map[String, Seq[String]]]
    )(implicit ec: ExecutionContext) = apply(service, additionalHeaders, client)

    def withClient(
      client: RequestClient
    )(implicit ec: ExecutionContext) = apply(service, client)
  }
  def apply[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
    serviceI: Service[Alg, Op],
    additionalHeaders: Option[Map[String, Seq[String]]] = None,
    client: RequestClient
  )(implicit ec: ExecutionContext): Alg[ClientRequest] =
    new GenericAPIClient(serviceI, client).transformer(additionalHeaders)

  def apply[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
    serviceI: Service[Alg, Op],
    client: RequestClient
  )(implicit ec: ExecutionContext): Alg[RunnableClientRequest] =
    new GenericAPIClient(serviceI, client).transformer()

}
