package models

import de.innfactory.smithy4play.EndpointRequest
import de.innfactory.smithy4play.client.{ matchStatusCodeForResponse, FinishedClientResponse, RunnableClientResponse }
import org.scalatestplus.play.{ BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec }
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{ route, writeableOf_AnyContentAsEmpty }
import smithy4s.{ Blob, Hints, Service }
import smithy4s.http.{ CaseInsensitive, HttpResponse }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.materializer
import smithy4s.Endpoint.Middleware
import smithy4s.kinds.Kind1

trait TestBase extends PlaySpec with BaseOneAppPerSuite with FakeApplicationFactory {

  def client[Alg[_[_, _, _, _, _]]](
    service: Service[Alg],
    requestIsSuccessful: (Hints, HttpResponse[Blob]) => Boolean = matchStatusCodeForResponse
  ): Alg[Kind1[RunnableClientResponse]#toKind5] =
    Smithy4PlayTestClient(service = service, middleware = Middleware.noop, requestIsSuccessful = requestIsSuccessful)

}
