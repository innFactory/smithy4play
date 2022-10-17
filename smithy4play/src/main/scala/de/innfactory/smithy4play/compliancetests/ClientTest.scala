package de.innfactory.smithy4play.compliancetests

import de.innfactory.smithy4play.ClientResponse
import de.innfactory.smithy4play.client.{
  SmithyPlayClientEndpointErrorResponse,
  SmithyPlayClientEndpointResponse
}
import smithy.test._
import smithy4s.http.HttpEndpoint
import smithy4s.{ Document, Endpoint, GenLift, Monadic, Service }

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContext }

class ClientTest[
  Alg[_[_, _, _, _, _]],
  Op[_, _, _, _, _]
](
  client: Monadic[Alg, ClientResponse]
)(implicit
  service: Service[Alg, Op],
  ec: ExecutionContext
) {

  def clientRequest[I, E, O, SE, SO](
    endpoint: Endpoint[Op, I, E, O, SE, SO],
    requestTestCase: Option[HttpRequestTestCase],
    responseTestCase: Option[HttpResponseTestCase]
  ): Boolean = {

    val inputFromDocument = Document.Decoder.fromSchema(endpoint.input)
    val input             = inputFromDocument.decode(requestTestCase.flatMap(_.params).getOrElse(Document.obj())).toOption.get

    val result = service
      .asTransformation[GenLift[ClientResponse]#λ](client)
      .apply(endpoint.wrap(input))
      .map(res => matchResponse(res, endpoint, responseTestCase))
    Await.result(result, 5.seconds)
  }

  def matchResponse[I, E, O, SE, SO](
    response: Either[SmithyPlayClientEndpointErrorResponse, SmithyPlayClientEndpointResponse[O]],
    endpoint: Endpoint[Op, I, E, O, SE, SO],
    responseTestCase: Option[HttpResponseTestCase]
  ) = {

    val httpEp             = HttpEndpoint.cast(endpoint).get
    val responseStatusCode = response match {
      case Left(value)  => value.statusCode
      case Right(value) => value.statusCode
    }
    val statusAssert       = responseTestCase.map(_.code).getOrElse(httpEp.code) == responseStatusCode

    val outputFromDocument = Document.Decoder.fromSchema(endpoint.output)
    val expectedOutput     =
      outputFromDocument.decode(responseTestCase.flatMap(_.params).getOrElse(Document.obj())).toOption

    println(statusAssert + responseStatusCode.toString)
    println(expectedOutput)
    responseTestCase.forall(_ => expectedOutput == response.toOption.flatMap(_.body)) && statusAssert
  }

  def tests(): Seq[Boolean] =
    service.endpoints.flatMap { endpoint =>
      val requests  = endpoint.hints
        .get(HttpRequestTests)
        .map(_.value)
        .getOrElse(Nil)
      val responses = endpoint.hints
        .get(HttpResponseTests)
        .map(_.value)
        .getOrElse(Nil)
      val ids       = requests.map(_.id).toSet ++ responses.map(_.id).toSet

      ids
        .map(id => (requests.find(_.id == id), responses.find(_.id == id)))
        .map(x => clientRequest(endpoint, x._1, x._2))
    }
}
