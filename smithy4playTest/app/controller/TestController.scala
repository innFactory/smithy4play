package controller

import cats.data.{EitherT, Kleisli}
import controller.models.TestError
import de.innfactory.smithy4play.{AutoRoutableController, AutoRouting, CodecUtils, ContextRoute, ContextRouteError, MiddlewareBase, SmithyPlayRouter}
import play.api.mvc.ControllerComponents
import play.api.routing.Router.Routes
import smithy4s.ByteArray
import testDefinitions.test._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
@AutoRouting
class TestController @Inject() (implicit
  cc: ControllerComponents,
  executionContext: ExecutionContext
) extends TestControllerService[ContextRoute] {

  override def test(): ContextRoute[SimpleTestResponse] = Kleisli { rc =>
    EitherT.rightT[Future, ContextRouteError](SimpleTestResponse(Some("TestWithSimpleResponse")))
  }

  override def testWithOutput(
    pathParam: String,
    testQuery: String,
    testHeader: String,
    body: TestRequestBody
  ): ContextRoute[TestWithOutputResponse] =
    Kleisli { rc =>
      EitherT.rightT[Future, ContextRouteError](
        TestWithOutputResponse(TestResponseBody(testHeader, pathParam, testQuery, body.message))
      )
    }

  override def health(): ContextRoute[Unit] = Kleisli { rc =>
    EitherT.rightT[Future, ContextRouteError](())
  }

  override def testWithBlob(body: ByteArray, contentType: String): ContextRoute[BlobResponse] = Kleisli { rc =>
    EitherT.rightT[Future, ContextRouteError](BlobResponse(body, "image/png"))
  }

  override def testWithQuery(testQuery: String): ContextRoute[Unit] = Kleisli { rc =>
    EitherT.rightT[Future, ContextRouteError](())
  }

  override def testThatReturnsError(): ContextRoute[Unit] = Kleisli { rc =>
    EitherT.leftT[Future, Unit](TestError("this is supposed to fail"))
  }

  override def testAuth(): ContextRoute[Unit] = Kleisli { rc =>
    EitherT.rightT[Future, ContextRouteError](())
  }
}
