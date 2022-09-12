import de.innfactory.smithy4play.ClientResponse
import de.innfactory.smithy4play.client.{ RequestClient, SmithyPlayClient }
import smithy4s.ByteArray
import testDefinitions.test.{
  BlobRequest,
  BlobResponse,
  QueryRequest,
  SimpleTestResponse,
  TestControllerService,
  TestControllerServiceGen,
  TestRequestBody,
  TestRequestWithQueryAndPathParams,
  TestWithOutputResponse
}

import scala.concurrent.ExecutionContext

class TestControllerClient(additionalHeaders: Map[String, Seq[String]] = Map.empty, baseUri: String = "/")(implicit
                                                                                                       ec: ExecutionContext,
                                                                                                       client: RequestClient
) extends TestControllerService[ClientResponse] {

  val smithyPlayClient = new SmithyPlayClient(baseUri, TestControllerService.service)

  override def test(): ClientResponse[SimpleTestResponse] =
    smithyPlayClient.send(TestControllerServiceGen.Test(), Some(additionalHeaders))

  override def testWithOutput(
    pathParam: String,
    testQuery: String,
    testHeader: String,
    body: TestRequestBody
  ): ClientResponse[TestWithOutputResponse] = smithyPlayClient.send(
    TestControllerServiceGen.TestWithOutput(TestRequestWithQueryAndPathParams(pathParam, testQuery, testHeader, body)),
    Some(additionalHeaders ++ Map("Content-Type" -> Seq("application/json")))
  )

  override def health(): ClientResponse[Unit] =
    smithyPlayClient.send(TestControllerServiceGen.Health(), Some(additionalHeaders))

  override def testWithBlob(body: ByteArray, contentType: String): ClientResponse[BlobResponse] =
    smithyPlayClient.send(TestControllerServiceGen.TestWithBlob(BlobRequest(body, contentType)), Some(additionalHeaders))

  override def testWithQuery(testQuery: String): ClientResponse[Unit] =
    smithyPlayClient.send(TestControllerServiceGen.TestWithQuery(QueryRequest(testQuery)), Some(additionalHeaders))

  override def testThatReturnsError(): ClientResponse[Unit] =
    smithyPlayClient.send(TestControllerServiceGen.TestThatReturnsError(), Some(additionalHeaders))
}
