package de.innfactory.smithy4play.client

import de.innfactory.smithy4play.ClientResponse
import smithy4s.{ GenLift, Service, Transformation }

import scala.concurrent.ExecutionContext

class GenericAPIClient[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
  service: Service[Alg, Op]
)(implicit ec: ExecutionContext, client: RequestClient) {

  val smithyPlayClient                                                                                 = new SmithyPlayClient("/", service)
  def transformer(additionalHeaders: Option[Map[String, Seq[String]]]): Alg[GenLift[ClientResponse]#λ] =
    service.transform(this.opToResponse(additionalHeaders))

  def opToResponse(additionalHeaders: Option[Map[String, Seq[String]]]): Transformation[Op, GenLift[ClientResponse]#λ] =
    new Transformation[Op, GenLift[ClientResponse]#λ] {
      def apply[I, E, O, SI, SO](op: Op[I, E, O, SI, SO]): ClientResponse[O] =
        smithyPlayClient.send(op, additionalHeaders)
    }
}

object GenericAPIClient {
  def apply[Alg[_[_, _, _, _, _]], Op[_, _, _, _, _]](
    serviceI: Service[Alg, Op],
    additionalHeaders: Option[Map[String, Seq[String]]] = None
  )(implicit ec: ExecutionContext, client: RequestClient): Alg[GenLift[ClientResponse]#λ] =
    new GenericAPIClient(serviceI).transformer(additionalHeaders)

}
