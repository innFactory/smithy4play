$version: "2"
namespace testDefinitions.test

use alloy#simpleRestJson
use de.innfactory.smithy4play.meta#contentTypes
use smithy.test#StringList

@testMiddleware
@httpBearerAuth
@simpleRestJson
service TestControllerService {
    version: "0.0.1",
    errors: [UnauthorizedError],
    operations: [
        Test
        TestWithOutput
        Health
        TestWithBlob
        TestWithQuery
        TestWithJsonInputAndBlobOutput
        TestThatReturnsError
        TestAuth
        TestWithOtherStatusCode
    ]
}

@auth([])
@readonly
@changeStatusCode
@http(method: "GET", uri: "/other/status/code", code: 200)
operation TestWithOtherStatusCode {
    output: TestWithOtherStatus
}

structure TestWithOtherStatus {
   @required
   @httpResponseCode
   code: Integer = 200
}

@auth([])
@http(method: "POST", uri: "/jsoninput/bloboutput", code: 200)
operation TestWithJsonInputAndBlobOutput {
    input:= {
        @httpPayload
        @required
        body: JsonInput
    }
    output: BlobResponse
}

@auth([])
@http(method: "POST", uri: "/blob", code: 200)
operation TestWithBlob {
    input: BlobRequest,
    output: BlobResponse,
}

@error("client")
@httpError(426)
structure Error426 {
    @required
    message: String
}

@error("client")
@httpError(401)
structure UnauthorizedError {
    @required
    message: String
}

@error("server")
@httpError(500)
structure InternalServerError {
    @required
    message: String
}

@auth([])
@readonly
@http(method: "GET", uri: "/error", code: 200)
operation TestThatReturnsError {
    errors: [InternalServerError]
}

@auth([])
@readonly
@http(method: "GET", uri: "/query", code: 200)
operation TestWithQuery {
    input: QueryRequest,
    output: QueryResponse
}

structure QueryResponse {
    @httpPayload
    body: StringQueryList
}

structure QueryRequest {
    @httpQuery("testQuery")
    @required
    testQuery: String
    @httpQuery("testQueryTwo")
    @required
    testQueryTwo: String
    @httpQuery("testQueryList")
    @required
    testQueryList: StringQueryList
}

list StringQueryList {
    member: String
}

structure BlobRequest {
    @httpPayload
    @required
    body: Blob,
    @httpHeader("cOnTeNt-TyPe")
    @required
    contentType: String
}

structure BlobResponse {
    @httpPayload
    @required
    body: Blob,
    @httpHeader("cOnTeNt-TyPe")
    @required
    contentType: String
}

@auth([])
@readonly
@disableTestMiddleware
@http(method: "GET", uri: "/health", code: 200)
operation Health {
}

@auth([])
@readonly
@http(method: "GET", uri: "/", code: 200)
operation Test {
    output: SimpleTestResponse
}

@auth([])
@contentTypes(general: ["application/xml", "application/json"])
@http(method: "POST", uri: "/test/{pathParam}", code: 200)
operation TestWithOutput {
    input: TestRequestWithQueryAndPathParams,
    output: TestWithOutputResponse
}

structure SimpleTestResponse {
    message: String
}

structure TestRequestWithQueryAndPathParams {
    @httpLabel
    @required
    pathParam: String,
    @httpQuery("testQuery")
    @required
    testQuery: String,
    @httpHeader("Test-Header")
    @required
    testHeader: String,
    @httpPayload
    @required
    body: TestRequestBody
}

structure TestRequestBody {
    @required
    message: String
}

structure TestWithOutputResponse {
    @httpPayload
    @required
    body: TestResponseBody
}

structure TestResponseBody {
    @required
    testHeader: String,
    @required
    pathParam: String,
    @required
    testQuery: String,
    @required
    bodyMessage: String
}


structure JsonInput {
    @required
    message: String
}

@http(method: "GET", uri: "/auth", code: 200)
operation TestAuth {
}


