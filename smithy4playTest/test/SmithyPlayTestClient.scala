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

class SmithyPlayTestClient(additionalHeaders: Option[Map[String, Seq[String]]], baseUri: String = "/")(implicit
  ec: ExecutionContext,
  client: RequestClient
) extends TestControllerService[ClientResponse] {

  val smithyPlayClient = new SmithyPlayClient(baseUri, TestControllerService.service)

  override def test(): ClientResponse[SimpleTestResponse] =
    smithyPlayClient.send(TestControllerServiceGen.Test(), additionalHeaders)

  override def testWithOutput(
    pathParam: String,
    testQuery: String,
    testHeader: String,
    body: TestRequestBody
  ): ClientResponse[TestWithOutputResponse] = smithyPlayClient.send(
    TestControllerServiceGen.TestWithOutput(TestRequestWithQueryAndPathParams(pathParam, testQuery, testHeader, body)),
    additionalHeaders
  )

  override def health(): ClientResponse[Unit] =
    smithyPlayClient.send(TestControllerServiceGen.Health(), additionalHeaders)

  override def testWithBlob(body: ByteArray, contentType: String): ClientResponse[BlobResponse] =
    smithyPlayClient.send(TestControllerServiceGen.TestWithBlob(BlobRequest(body, contentType)), additionalHeaders)

  override def testWithQuery(testQuery: String): ClientResponse[Unit] =
    smithyPlayClient.send(TestControllerServiceGen.TestWithQuery(QueryRequest(testQuery)), additionalHeaders)

  override def testThatReturnsError(): ClientResponse[Unit] =
    smithyPlayClient.send(TestControllerServiceGen.TestThatReturnsError(), additionalHeaders)
}
