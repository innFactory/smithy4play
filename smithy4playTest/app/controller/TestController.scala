package controller

import cats.data.{ EitherT, Kleisli }
import controller.models.TestError
import de.innfactory.smithy4play.{ AutoRouting, ContextRoute, ContextRouteError }
import play.api.mvc.ControllerComponents
import smithy4s.{ Blob, Document }
import testDefinitions.test._

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
@AutoRouting
class TestController @Inject() (implicit
  cc: ControllerComponents,
  executionContext: ExecutionContext
) extends TestControllerService[ContextRoute] {

  override def test(): ContextRoute[SimpleTestResponse] = Kleisli { rc =>
    rc.attributes.get("Not") match {
      case Some(_) => EitherT.rightT[Future, ContextRouteError](SimpleTestResponse(Some("TestWithSimpleResponse")))
      case None    => EitherT.leftT[Future, SimpleTestResponse](TestError("Not attribute is not defined"))
    }
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
    rc.attributes.get("Test") match {
      case Some(_) =>
        rc.attributes.get("Not") match {
          case Some(_) => EitherT.leftT[Future, Unit](TestError("Not attribute is defined"))
          case None    => EitherT.rightT[Future, ContextRouteError](())
        }
      case None    => EitherT.leftT[Future, Unit](TestError("Test attribute is not defined"))
    }

  }

  override def testWithBlob(body: Blob, contentType: String): ContextRoute[BlobResponse] = Kleisli { rc =>
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

  override def testWithOtherStatusCode(): ContextRoute[Unit] = Kleisli { rc =>
    EitherT.rightT[Future, ContextRouteError](())
  }

  override def testWithJsonInputAndBlobOutput(body: JsonInput): ContextRoute[BlobResponse] = Kleisli { rc =>
    EitherT.rightT[Future, ContextRouteError](BlobResponse(Blob(body.message), "image/png"))
  }
}
