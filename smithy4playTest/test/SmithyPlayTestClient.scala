import de.innfactory.smithy4play.ClientResponse
import de.innfactory.smithy4play.client.{ RequestClient, SmithyPlayClient, SmithyPlayClientEndpointResponse }
import smithy4s.ByteArray
import smithy4s.http.PayloadError
import testDefinitions.test.{
  BlobRequest,
  BlobResponse,
  SimpleTestResponse,
  TestControllerService,
  TestControllerServiceGen,
  TestControllerServiceOperation,
  TestRequestBody,
  TestRequestWithQueryAndPathParams,
  TestWithOutputResponse
}

import scala.concurrent.{ ExecutionContext, Future }

class SmithyPlayTestClient(authHeader: Option[String], baseUri: String = "/")(implicit
  ec: ExecutionContext,
  client: RequestClient
) extends TestControllerService[ClientResponse] {

  val smithyPlayClient = new SmithyPlayClient(baseUri, TestControllerService.service)

  override def test(): ClientResponse[SimpleTestResponse] =
    smithyPlayClient.send(TestControllerServiceGen.Test(), authHeader)

  override def testWithOutput(
    pathParam: String,
    testQuery: String,
    testHeader: String,
    body: TestRequestBody
  ): ClientResponse[TestWithOutputResponse] = smithyPlayClient.send(
    TestControllerServiceGen.TestWithOutput(TestRequestWithQueryAndPathParams(pathParam, testQuery, testHeader, body)),
    authHeader
  )

  override def health(): ClientResponse[Unit] = smithyPlayClient.send(TestControllerServiceGen.Health(), authHeader)

  override def testWithBlob(body: ByteArray, contentType: String): ClientResponse[BlobResponse] =
    smithyPlayClient.send(TestControllerServiceGen.TestWithBlob(BlobRequest(body,contentType)), authHeader)
}
