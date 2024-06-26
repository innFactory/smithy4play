package testDefinitions.test

import smithy4s.ByteArray
import smithy4s.Endpoint
import smithy4s.Hints
import smithy4s.Schema
import smithy4s.Service
import smithy4s.ShapeId
import smithy4s.StreamingSchema
import smithy4s.Transformation
import smithy4s.kinds.PolyFunction5
import smithy4s.kinds.toPolyFunction5.const5
import smithy4s.schema.Schema.unit

trait TestControllerServiceGen[F[_, _, _, _, _]] {
  self =>

  def test(): F[Unit, Nothing, SimpleTestResponse, Nothing, Nothing]
  def testWithOutput(
    pathParam: String,
    testQuery: String,
    testHeader: String,
    body: TestRequestBody
  ): F[TestRequestWithQueryAndPathParams, Nothing, TestWithOutputResponse, Nothing, Nothing]
  def health(): F[Unit, Nothing, Unit, Nothing, Nothing]
  def testWithBlob(body: ByteArray, contentType: String): F[BlobRequest, Nothing, BlobResponse, Nothing, Nothing]
  def testWithQuery(testQuery: String): F[QueryRequest, Nothing, Unit, Nothing, Nothing]
  def testThatReturnsError(): F[Unit, Nothing, Unit, Nothing, Nothing]
  def testAuth(): F[Unit, Nothing, Unit, Nothing, Nothing]

  def transform: Transformation.PartiallyApplied[TestControllerServiceGen[F]] =
    Transformation.of[TestControllerServiceGen[F]](this)
}

object TestControllerServiceGen extends Service.Mixin[TestControllerServiceGen, TestControllerServiceOperation] {

  def apply[F[_]](implicit F: Impl[F]): F.type = F

  object ErrorAware {
    def apply[F[_, _]](implicit F: ErrorAware[F]): F.type = F
    type Default[F[+_, +_]] = Constant[smithy4s.kinds.stubs.Kind2[F]#toKind5]
  }

  val id: ShapeId = ShapeId("testDefinitions.test", "TestControllerService")

  val hints: Hints = Hints(
    alloy.SimpleRestJson(),
    smithy.api.HttpBearerAuth()
  )

  val endpoints: List[TestControllerServiceGen.Endpoint[_, _, _, _, _]] = List(
    Test,
    TestWithOutput,
    Health,
    TestWithBlob,
    TestWithQuery,
    TestThatReturnsError,
    TestAuth
  )

  val version: String = "0.0.1"

  def endpoint[I, E, O, SI, SO](op: TestControllerServiceOperation[I, E, O, SI, SO]) = op.endpoint

  object reified extends TestControllerServiceGen[TestControllerServiceOperation] {
    def test()                                                                                          = Test()
    def testWithOutput(pathParam: String, testQuery: String, testHeader: String, body: TestRequestBody) =
      TestWithOutput(TestRequestWithQueryAndPathParams(pathParam, testQuery, testHeader, body))
    def health()                                                                                        = Health()
    def testWithBlob(body: ByteArray, contentType: String)                                              = TestWithBlob(BlobRequest(body, contentType))
    def testWithQuery(testQuery: String)                                                                = TestWithQuery(QueryRequest(testQuery))
    def testThatReturnsError()                                                                          = TestThatReturnsError()
    def testAuth()                                                                                      = TestAuth()
  }

  def mapK5[P[_, _, _, _, _], P1[_, _, _, _, _]](
    alg: TestControllerServiceGen[P],
    f: PolyFunction5[P, P1]
  ): TestControllerServiceGen[P1] = new Transformed(alg, f)

  def fromPolyFunction[P[_, _, _, _, _]](
    f: PolyFunction5[TestControllerServiceOperation, P]
  ): TestControllerServiceGen[P] = new Transformed(reified, f)
  class Transformed[P[_, _, _, _, _], P1[_, _, _, _, _]](alg: TestControllerServiceGen[P], f: PolyFunction5[P, P1])
      extends TestControllerServiceGen[P1] {
    def test()                                                                                          = f[Unit, Nothing, SimpleTestResponse, Nothing, Nothing](alg.test())
    def testWithOutput(pathParam: String, testQuery: String, testHeader: String, body: TestRequestBody) =
      f[TestRequestWithQueryAndPathParams, Nothing, TestWithOutputResponse, Nothing, Nothing](
        alg.testWithOutput(pathParam, testQuery, testHeader, body)
      )
    def health()                                                                                        = f[Unit, Nothing, Unit, Nothing, Nothing](alg.health())
    def testWithBlob(body: ByteArray, contentType: String)                                              =
      f[BlobRequest, Nothing, BlobResponse, Nothing, Nothing](alg.testWithBlob(body, contentType))
    def testWithQuery(testQuery: String)                                                                =
      f[QueryRequest, Nothing, Unit, Nothing, Nothing](alg.testWithQuery(testQuery))
    def testThatReturnsError()                                                                          = f[Unit, Nothing, Unit, Nothing, Nothing](alg.testThatReturnsError())
    def testAuth()                                                                                      = f[Unit, Nothing, Unit, Nothing, Nothing](alg.testAuth())
  }

  class Constant[P[-_, +_, +_, +_, +_]](value: P[Any, Nothing, Nothing, Nothing, Nothing])
      extends Transformed[TestControllerServiceOperation, P](reified, const5(value))
  type Default[F[+_]] = Constant[smithy4s.kinds.stubs.Kind1[F]#toKind5]

  def toPolyFunction[P[_, _, _, _, _]](
    impl: TestControllerServiceGen[P]
  ): PolyFunction5[TestControllerServiceOperation, P] = new PolyFunction5[TestControllerServiceOperation, P] {
    def apply[I, E, O, SI, SO](op: TestControllerServiceOperation[I, E, O, SI, SO]): P[I, E, O, SI, SO] = op.run(impl)
  }
  case class Test()                 extends TestControllerServiceOperation[Unit, Nothing, SimpleTestResponse, Nothing, Nothing]     {
    def run[F[_, _, _, _, _]](
      impl: TestControllerServiceGen[F]
    ): F[Unit, Nothing, SimpleTestResponse, Nothing, Nothing] = impl.test()
    def endpoint: (Unit, Endpoint[Unit, Nothing, SimpleTestResponse, Nothing, Nothing]) = ((), Test)
  }
  object Test                       extends TestControllerServiceGen.Endpoint[Unit, Nothing, SimpleTestResponse, Nothing, Nothing]  {
    val id: ShapeId                              = ShapeId("testDefinitions.test", "Test")
    val input: Schema[Unit]                      = unit.addHints(smithy4s.internals.InputOutput.Input.widen)
    val output: Schema[SimpleTestResponse]       =
      SimpleTestResponse.schema.addHints(smithy4s.internals.InputOutput.Output.widen)
    val streamedInput: StreamingSchema[Nothing]  = StreamingSchema.nothing
    val streamedOutput: StreamingSchema[Nothing] = StreamingSchema.nothing
    val hints: Hints                             = Hints(
      smithy.api.Auth(Set()),
      smithy.api.Http(method = smithy.api.NonEmptyString("GET"), uri = smithy.api.NonEmptyString("/"), code = 200),
      smithy.api.Readonly(),
      smithy.test.HttpResponseTests(
        List(
          smithy.test.HttpResponseTestCase(
            id = "test2",
            protocol = "alloy#simpleRestJson",
            code = 200,
            authScheme = None,
            headers = None,
            forbidHeaders = None,
            requireHeaders = None,
            body = None,
            bodyMediaType = None,
            params = Some(smithy4s.Document.obj("message" -> smithy4s.Document.fromString("TestWithSimpleResponse"))),
            vendorParams = None,
            vendorParamsShape = None,
            documentation = None,
            tags = None,
            appliesTo = None
          )
        )
      )
    )
    def wrap(input: Unit)                        = Test()
  }
  case class TestWithOutput(input: TestRequestWithQueryAndPathParams)
      extends TestControllerServiceOperation[
        TestRequestWithQueryAndPathParams,
        Nothing,
        TestWithOutputResponse,
        Nothing,
        Nothing
      ] {
    def run[F[_, _, _, _, _]](
      impl: TestControllerServiceGen[F]
    ): F[TestRequestWithQueryAndPathParams, Nothing, TestWithOutputResponse, Nothing, Nothing] =
      impl.testWithOutput(input.pathParam, input.testQuery, input.testHeader, input.body)
    def endpoint: (
      TestRequestWithQueryAndPathParams,
      Endpoint[TestRequestWithQueryAndPathParams, Nothing, TestWithOutputResponse, Nothing, Nothing]
    ) = (input, TestWithOutput)
  }
  object TestWithOutput
      extends TestControllerServiceGen.Endpoint[
        TestRequestWithQueryAndPathParams,
        Nothing,
        TestWithOutputResponse,
        Nothing,
        Nothing
      ] {
    val id: ShapeId                                      = ShapeId("testDefinitions.test", "TestWithOutput")
    val input: Schema[TestRequestWithQueryAndPathParams] =
      TestRequestWithQueryAndPathParams.schema.addHints(smithy4s.internals.InputOutput.Input.widen)
    val output: Schema[TestWithOutputResponse]           =
      TestWithOutputResponse.schema.addHints(smithy4s.internals.InputOutput.Output.widen)
    val streamedInput: StreamingSchema[Nothing]          = StreamingSchema.nothing
    val streamedOutput: StreamingSchema[Nothing]         = StreamingSchema.nothing
    val hints: Hints                                     = Hints(
      smithy.api.Http(
        method = smithy.api.NonEmptyString("POST"),
        uri = smithy.api.NonEmptyString("/test/{pathParam}"),
        code = 200
      ),
      smithy.api.Auth(Set())
    )
    def wrap(input: TestRequestWithQueryAndPathParams)   = TestWithOutput(input)
  }
  case class Health()               extends TestControllerServiceOperation[Unit, Nothing, Unit, Nothing, Nothing]                   {
    def run[F[_, _, _, _, _]](impl: TestControllerServiceGen[F]): F[Unit, Nothing, Unit, Nothing, Nothing] =
      impl.health()
    def endpoint: (Unit, Endpoint[Unit, Nothing, Unit, Nothing, Nothing])                                  = ((), Health)
  }
  object Health                     extends TestControllerServiceGen.Endpoint[Unit, Nothing, Unit, Nothing, Nothing]                {
    val id: ShapeId                              = ShapeId("testDefinitions.test", "Health")
    val input: Schema[Unit]                      = unit.addHints(smithy4s.internals.InputOutput.Input.widen)
    val output: Schema[Unit]                     = unit.addHints(smithy4s.internals.InputOutput.Output.widen)
    val streamedInput: StreamingSchema[Nothing]  = StreamingSchema.nothing
    val streamedOutput: StreamingSchema[Nothing] = StreamingSchema.nothing
    val hints: Hints                             = Hints(
      smithy.api.Auth(Set()),
      smithy.api
        .Http(method = smithy.api.NonEmptyString("GET"), uri = smithy.api.NonEmptyString("/health"), code = 200),
      smithy.api.Readonly()
    )
    def wrap(input: Unit)                        = Health()
  }
  case class TestWithBlob(input: BlobRequest)
      extends TestControllerServiceOperation[BlobRequest, Nothing, BlobResponse, Nothing, Nothing] {
    def run[F[_, _, _, _, _]](
      impl: TestControllerServiceGen[F]
    ): F[BlobRequest, Nothing, BlobResponse, Nothing, Nothing] = impl.testWithBlob(input.body, input.contentType)
    def endpoint: (BlobRequest, Endpoint[BlobRequest, Nothing, BlobResponse, Nothing, Nothing]) = (input, TestWithBlob)
  }
  object TestWithBlob               extends TestControllerServiceGen.Endpoint[BlobRequest, Nothing, BlobResponse, Nothing, Nothing] {
    val id: ShapeId                              = ShapeId("testDefinitions.test", "TestWithBlob")
    val input: Schema[BlobRequest]               = BlobRequest.schema.addHints(smithy4s.internals.InputOutput.Input.widen)
    val output: Schema[BlobResponse]             = BlobResponse.schema.addHints(smithy4s.internals.InputOutput.Output.widen)
    val streamedInput: StreamingSchema[Nothing]  = StreamingSchema.nothing
    val streamedOutput: StreamingSchema[Nothing] = StreamingSchema.nothing
    val hints: Hints                             = Hints(
      smithy.api.Http(method = smithy.api.NonEmptyString("POST"), uri = smithy.api.NonEmptyString("/blob"), code = 200),
      smithy.api.Auth(Set())
    )
    def wrap(input: BlobRequest)                 = TestWithBlob(input)
  }
  case class TestWithQuery(input: QueryRequest)
      extends TestControllerServiceOperation[QueryRequest, Nothing, Unit, Nothing, Nothing] {
    def run[F[_, _, _, _, _]](impl: TestControllerServiceGen[F]): F[QueryRequest, Nothing, Unit, Nothing, Nothing] =
      impl.testWithQuery(input.testQuery)
    def endpoint: (QueryRequest, Endpoint[QueryRequest, Nothing, Unit, Nothing, Nothing])                          = (input, TestWithQuery)
  }
  object TestWithQuery              extends TestControllerServiceGen.Endpoint[QueryRequest, Nothing, Unit, Nothing, Nothing]        {
    val id: ShapeId                              = ShapeId("testDefinitions.test", "TestWithQuery")
    val input: Schema[QueryRequest]              = QueryRequest.schema.addHints(smithy4s.internals.InputOutput.Input.widen)
    val output: Schema[Unit]                     = unit.addHints(smithy4s.internals.InputOutput.Output.widen)
    val streamedInput: StreamingSchema[Nothing]  = StreamingSchema.nothing
    val streamedOutput: StreamingSchema[Nothing] = StreamingSchema.nothing
    val hints: Hints                             = Hints(
      smithy.test.HttpRequestTests(
        List(
          smithy.test.HttpRequestTestCase(
            id = "test1",
            protocol = "alloy#simpleRestJson",
            method = "GET",
            uri = "/query",
            host = None,
            resolvedHost = None,
            authScheme = None,
            queryParams = None,
            forbidQueryParams = None,
            requireQueryParams = None,
            headers = None,
            forbidHeaders = None,
            requireHeaders = None,
            body = None,
            bodyMediaType = None,
            params = Some(smithy4s.Document.obj("testQuery" -> smithy4s.Document.fromString("Hello there"))),
            vendorParams = None,
            vendorParamsShape = None,
            documentation = None,
            tags = None,
            appliesTo = None
          )
        )
      ),
      smithy.api.Auth(Set()),
      smithy.api.Http(method = smithy.api.NonEmptyString("GET"), uri = smithy.api.NonEmptyString("/query"), code = 200),
      smithy.api.Readonly(),
      smithy.test.HttpResponseTests(
        List(
          smithy.test.HttpResponseTestCase(
            id = "test1",
            protocol = "alloy#simpleRestJson",
            code = 200,
            authScheme = None,
            headers = None,
            forbidHeaders = None,
            requireHeaders = None,
            body = None,
            bodyMediaType = None,
            params = None,
            vendorParams = None,
            vendorParamsShape = None,
            documentation = None,
            tags = None,
            appliesTo = None
          )
        )
      )
    )
    def wrap(input: QueryRequest)                = TestWithQuery(input)
  }
  case class TestThatReturnsError() extends TestControllerServiceOperation[Unit, Nothing, Unit, Nothing, Nothing]                   {
    def run[F[_, _, _, _, _]](impl: TestControllerServiceGen[F]): F[Unit, Nothing, Unit, Nothing, Nothing] =
      impl.testThatReturnsError()
    def endpoint: (Unit, Endpoint[Unit, Nothing, Unit, Nothing, Nothing])                                  = ((), TestThatReturnsError)
  }
  object TestThatReturnsError       extends TestControllerServiceGen.Endpoint[Unit, Nothing, Unit, Nothing, Nothing]                {
    val id: ShapeId                              = ShapeId("testDefinitions.test", "TestThatReturnsError")
    val input: Schema[Unit]                      = unit.addHints(smithy4s.internals.InputOutput.Input.widen)
    val output: Schema[Unit]                     = unit.addHints(smithy4s.internals.InputOutput.Output.widen)
    val streamedInput: StreamingSchema[Nothing]  = StreamingSchema.nothing
    val streamedOutput: StreamingSchema[Nothing] = StreamingSchema.nothing
    val hints: Hints                             = Hints(
      smithy.test.HttpRequestTests(
        List(
          smithy.test.HttpRequestTestCase(
            id = "test3",
            protocol = "alloy#simpleRestJson",
            method = "GET",
            uri = "/error",
            host = None,
            resolvedHost = None,
            authScheme = None,
            queryParams = None,
            forbidQueryParams = None,
            requireQueryParams = None,
            headers = None,
            forbidHeaders = None,
            requireHeaders = None,
            body = None,
            bodyMediaType = None,
            params = None,
            vendorParams = None,
            vendorParamsShape = None,
            documentation = Some("500"),
            tags = None,
            appliesTo = None
          )
        )
      ),
      smithy.api.Auth(Set()),
      smithy.api.Http(method = smithy.api.NonEmptyString("GET"), uri = smithy.api.NonEmptyString("/error"), code = 200),
      smithy.api.Readonly(),
      smithy.test.HttpResponseTests(
        List(
          smithy.test.HttpResponseTestCase(
            id = "test3",
            protocol = "alloy#simpleRestJson",
            code = 200,
            authScheme = None,
            headers = None,
            forbidHeaders = None,
            requireHeaders = None,
            body = Some("{\"message\":\"this is supposed to fail\"}"),
            bodyMediaType = None,
            params = None,
            vendorParams = None,
            vendorParamsShape = None,
            documentation = Some("500"),
            tags = None,
            appliesTo = None
          )
        )
      )
    )
    def wrap(input: Unit)                        = TestThatReturnsError()
  }
  case class TestAuth()             extends TestControllerServiceOperation[Unit, Nothing, Unit, Nothing, Nothing]                   {
    def run[F[_, _, _, _, _]](impl: TestControllerServiceGen[F]): F[Unit, Nothing, Unit, Nothing, Nothing] =
      impl.testAuth()
    def endpoint: (Unit, Endpoint[Unit, Nothing, Unit, Nothing, Nothing])                                  = ((), TestAuth)
  }
  object TestAuth                   extends TestControllerServiceGen.Endpoint[Unit, Nothing, Unit, Nothing, Nothing]                {
    val id: ShapeId                              = ShapeId("testDefinitions.test", "TestAuth")
    val input: Schema[Unit]                      = unit.addHints(smithy4s.internals.InputOutput.Input.widen)
    val output: Schema[Unit]                     = unit.addHints(smithy4s.internals.InputOutput.Output.widen)
    val streamedInput: StreamingSchema[Nothing]  = StreamingSchema.nothing
    val streamedOutput: StreamingSchema[Nothing] = StreamingSchema.nothing
    val hints: Hints                             = Hints(
      smithy.api.Http(method = smithy.api.NonEmptyString("GET"), uri = smithy.api.NonEmptyString("/auth"), code = 200)
    )
    def wrap(input: Unit)                        = TestAuth()
  }
}

sealed trait TestControllerServiceOperation[Input, Err, Output, StreamedInput, StreamedOutput] {
  def run[F[_, _, _, _, _]](impl: TestControllerServiceGen[F]): F[Input, Err, Output, StreamedInput, StreamedOutput]
  def endpoint: (Input, Endpoint[TestControllerServiceOperation, Input, Err, Output, StreamedInput, StreamedOutput])
}
