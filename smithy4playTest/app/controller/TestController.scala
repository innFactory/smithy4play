package controller

import cats.data.{EitherT, Kleisli}
import controller.models.TestError
import de.innfactory.smithy4play.ContextRoute
import de.innfactory.smithy4play.client.Smithy4PlayWsClient
import de.innfactory.smithy4play.routing.Controller
import play.api.mvc.ControllerComponents
import play.api.libs.ws.WSClient
import smithy4s.Endpoint.Middleware
import smithy4s.Blob
import testDefinitions.test.*
import TestControllerService.serviceInstance
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestController @Inject() (implicit
  cc: ControllerComponents,
  executionContext: ExecutionContext,
  wsClient: WSClient
) extends TestControllerService[ContextRoute] with Controller {

  override def test(): ContextRoute[SimpleTestResponse] = Kleisli { rc =>
    rc.attributes.get("Not") match {
      case Some(_) => EitherT.rightT[Future, Throwable](SimpleTestResponse(Some("TestWithSimpleResponse")))
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

  }

  override def testWithBlob(body: Blob, contentType: String): ContextRoute[BlobResponse] = Kleisli { rc =>
    EitherT.rightT[Future, Throwable](BlobResponse(body, "image/png"))
  }

  override def testWithQuery(testQuery: String): ContextRoute[Unit] = Kleisli { rc =>
    EitherT.rightT[Future, Throwable](())
  }

  override def testThatReturnsError(): ContextRoute[Unit] = Kleisli { rc =>
    EitherT.leftT[Future, Unit](TestError("this is supposed to fail"))
  }

  val client = Smithy4PlayWsClient("http://0.0.0.0:9000/", TestControllerServiceGen.service, Middleware.noop)
  override def testAuth(): ContextRoute[Unit] = Kleisli { rc =>


    val result = client.testWithJsonInputAndBlobOutput(JsonInput.apply("My Message")).run(None)
    println("test auth")
    val v = result.value.map {
      case Left(value) => println(value.getCause)
      case Right(value) => println(value.contentType)
    }
    EitherT.right(v)
  }

  override def testWithOtherStatusCode(): ContextRoute[Unit] = Kleisli { rc =>
    EitherT.rightT[Future, Throwable](())
  }

  override def testWithJsonInputAndBlobOutput(body: JsonInput): ContextRoute[BlobResponse] = Kleisli { rc =>
    EitherT.rightT[Future, Throwable](BlobResponse(Blob(body.message), "image/png"))
  }
}
