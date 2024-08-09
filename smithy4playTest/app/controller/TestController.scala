package controller

import cats.data.{EitherT, Kleisli}
import controller.models.TestError
import de.innfactory.smithy4play.ContextRoute
import de.innfactory.smithy4play.routing.Controller
import play.api.mvc.ControllerComponents
import play.api.libs.ws.WSClient
import smithy4s.Blob
import testDefinitions.test.*
import testDefinitions.test.TestControllerServiceGen.serviceInstance

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestController @Inject() (implicit
  cc: ControllerComponents,
  executionContext: ExecutionContext,
  wsClient: WSClient
) extends TestControllerService[ContextRoute] with Controller {

  override def test(): ContextRoute[SimpleTestResponse] = Kleisli { rc =>
   EitherT.rightT[Future, Throwable](SimpleTestResponse(Some("TestWithSimpleResponse")))
  }

  override def testWithOutput(
    pathParam: String,
    testQuery: String,
    testHeader: String,
    body: TestRequestBody
  ): ContextRoute[TestWithOutputResponse] =
    Kleisli { rc =>
      EitherT.rightT[Future, Throwable](
        TestWithOutputResponse(TestResponseBody(testHeader, pathParam, testQuery, body.message))
      )
    }

  override def health(): ContextRoute[Unit] = Kleisli { rc =>
    rc.attributes.get("Test") match {
      case Some(_) =>
        rc.attributes.get("Not") match {
          case Some(_) => EitherT.leftT[Future, Unit](TestError("Not attribute is defined"))
          case None    => EitherT.rightT[Future, Throwable](())
        }
      case None    => EitherT.leftT[Future, Unit](TestError("Test attribute is not defined"))
    }
    EitherT.rightT[Future, Throwable](())
  }

  override def testWithBlob(body: Blob, contentType: String): ContextRoute[BlobResponse] = Kleisli { rc =>
    EitherT.rightT[Future, Throwable](BlobResponse(body, "image/png"))
  }

  override def testWithQuery(testQuery: String, testQueryTwo: String, testQueryList: List[String]): ContextRoute[QueryResponse] = Kleisli { rc =>
    EitherT.rightT[Future, Throwable](QueryResponse(Some(testQueryList)))
  }

  override def testThatReturnsError(): ContextRoute[Unit] = Kleisli { rc =>
    EitherT.leftT[Future, Unit](InternalServerError("this is supposed to fail"))
  }

  override def testAuth(): ContextRoute[Unit] = Kleisli { rc =>
    println("testAuth")
    EitherT.leftT[Future, Unit](new Throwable("Error"))
  }

  override def testWithOtherStatusCode(): ContextRoute[TestWithOtherStatus] = Kleisli { rc =>
    EitherT.rightT[Future, Throwable](TestWithOtherStatus(269))
  }
  override def testWithJsonInputAndBlobOutput(body: JsonInput): ContextRoute[BlobResponse] = Kleisli { rc =>
    EitherT.rightT[Future, Throwable](BlobResponse(Blob(body.message), "image/png"))
  }
}
