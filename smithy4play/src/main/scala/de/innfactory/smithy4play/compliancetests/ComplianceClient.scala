package de.innfactory.smithy4play.compliancetests

import de.innfactory.smithy4play.ClientResponse
import de.innfactory.smithy4play.client.{ SmithyPlayClientEndpointErrorResponse, SmithyPlayClientEndpointResponse }
import play.api.libs.json.Json
import smithy4s.http.HttpEndpoint
import smithy4s.kinds.{ FunctorAlgebra, Kind1 }
import smithy4s.{ Document, Endpoint, Service }
import smithy.test._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContext }

class ComplianceClient[
  Alg[_[_, _, _, _, _]]
](
  client: FunctorAlgebra[Alg, ClientResponse]
)(implicit
  service: Service[Alg],
  ec: ExecutionContext
) {

  private def clientRequest[I, E, O, SE, SO](
    endpoint: Endpoint[service.Operation, I, E, O, SE, SO],
    requestTestCase: Option[HttpRequestTestCase],
    responseTestCase: Option[HttpResponseTestCase]
  ) = {

    val inputFromDocument = Document.Decoder.fromSchema(endpoint.input)
    val input             = inputFromDocument.decode(requestTestCase.flatMap(_.params).getOrElse(Document.obj())).toOption.get

    val result = service
      .toPolyFunction[Kind1[ClientResponse]#toKind5](client)
      .apply(endpoint.wrap(input))
      .map(res => matchResponse(res, endpoint, responseTestCase))
    Await.result(result, 5.seconds)
  }

  private def matchResponse[I, E, O, SE, SO](
    response: Either[SmithyPlayClientEndpointErrorResponse, SmithyPlayClientEndpointResponse[O]],
    endpoint: Endpoint[service.Operation, I, E, O, SE, SO],
    responseTestCase: Option[HttpResponseTestCase]
  ) = {

    val httpEp             = HttpEndpoint.cast(endpoint.schema).toOption.get
    val responseStatusCode = response match {
      case Left(value)  => value.statusCode
      case Right(value) => value.statusCode
    }
    val expectedStatusCode = responseTestCase.map(_.code).getOrElse(httpEp.code)
    // val statusAssert       = expectedStatusCode == responseStatusCode

    val outputFromDocument = Document.Decoder.fromSchema(endpoint.output)
    val expectedOutput     =
      outputFromDocument.decode(responseTestCase.flatMap(_.params).getOrElse(Document.obj())).toOption

    // responseTestCase.forall(_ => expectedOutput == response.toOption.flatMap(_.body)) && statusAssert
    ComplianceResponse(
      expectedCode = expectedStatusCode,
      receivedCode = responseStatusCode,
      expectedBody = expectedOutput,
      receivedBody = response.toOption.flatMap(_.body),
      expectedError = responseTestCase match {
        case Some(value) => value.body.getOrElse("")
        case None        => ""
      },
      receivedError = response match {
        case Left(value)  => Json.parse(value.error).toString()
        case Right(value) => ""
      }
    )
  }

  case class ComplianceResponse[O](
    expectedCode: Int,
    receivedCode: Int,
    expectedBody: Option[O],
    receivedBody: Option[O],
    expectedError: String,
    receivedError: String
  )

  def tests(suite: Option[String] = None) =
    service.endpoints.flatMap { endpoint =>
      val requests  = endpoint.hints
        .get(HttpRequestTests)
        .map(_.value)
        .getOrElse(Nil)
        .filter(tc =>
          suite.isEmpty && tc.documentation.isEmpty || tc.documentation.getOrElse("") == suite.getOrElse("")
        )
      val responses = endpoint.hints
        .get(HttpResponseTests)
        .map(_.value)
        .getOrElse(Nil)
        .filter(tc =>
          suite.isEmpty && tc.documentation.isEmpty || tc.documentation.getOrElse("") == suite.getOrElse("")
        )
      val ids       = requests.map(_.id).toSet ++ responses.map(_.id).toSet

      ids
        .map(id => (requests.find(_.id == id), responses.find(_.id == id)))
        .map(x => clientRequest(endpoint, x._1, x._2))
    }
}
