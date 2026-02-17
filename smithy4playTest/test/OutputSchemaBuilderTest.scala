import de.innfactory.smithy4play.mcp.server.util.OutputSchemaBuilder
import models.TestBase
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import smithy4s.Document
import smithy4s.Document.{ DArray, DObject, DString }
import smithy.test.AppliesTo
import testDefinitions.test.*

class OutputSchemaBuilderTest extends TestBase {

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  "OutputSchemaBuilder with recursive=false" must {

    "return JSON schema for simple struct" in {
      val result = OutputSchemaBuilder.build(TestCaseOne.schema)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "value" -> DObject(Map("type" -> DString("string")))
              )
            )
          )
        )
      )
    }

    "return JSON schema for struct with multiple fields" in {
      val result = OutputSchemaBuilder.build(JsonInput.schema)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "message" -> DObject(Map("type" -> DString("string")))
              )
            )
          )
        )
      )
    }

    "return None for unit schema (no output defined)" in {
      val result = OutputSchemaBuilder.build(smithy4s.schema.Schema.unit)

      result mustBe None
    }

    "unwrap httpPayload field and filter HTTP-bound fields" in {
      // TestWithOutputResponse has @httpPayload body: TestResponseBody
      // The payload is unwrapped, showing TestResponseBody's top-level properties
      val result = OutputSchemaBuilder.build(TestWithOutputResponse.schema)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "testHeader"  -> DObject(Map("type" -> DString("string"))),
                "pathParam"   -> DObject(Map("type" -> DString("string"))),
                "testQuery"   -> DObject(Map("type" -> DString("string"))),
                "bodyMessage" -> DObject(Map("type" -> DString("string")))
              )
            )
          )
        )
      )
    }

    "unwrap httpPayload field for list type" in {
      // QueryResponse has @httpPayload body: StringQueryList (a list)
      // Non-object schema wrapped in result property per MCP spec (type must be object)
      val result = OutputSchemaBuilder.build(QueryResponse.schema)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "result" -> DObject(
                  Map(
                    "type"  -> DString("array"),
                    "items" -> DObject(Map("type" -> DString("string")))
                  )
                )
              )
            )
          )
        )
      )
    }

    "filter out httpHeader fields from output schema" in {
      // BlobResponse has @httpPayload body: Blob and @httpHeader contentType: String
      // Non-object schema wrapped in result property per MCP spec
      val result = OutputSchemaBuilder.build(BlobResponse.schema)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "result" -> DObject(Map("type" -> DString("string")))
              )
            )
          )
        )
      )
    }

    "return oneOf for tagged union with shallow variants" in {
      val result = OutputSchemaBuilder.build(TaggedTestUnion.schema)

      result mustBe Some(
        DObject(
          Map(
            "type"  -> DString("object"),
            "oneOf" -> DArray(
              IndexedSeq(
                DObject(
                  Map(
                    "type"       -> DString("object"),
                    "properties" -> DObject(
                      Map("caseOne" -> DObject(Map("type" -> DString("object"))))
                    )
                  )
                ),
                DObject(
                  Map(
                    "type"       -> DString("object"),
                    "properties" -> DObject(
                      Map("caseTwo" -> DObject(Map("type" -> DString("object"))))
                    )
                  )
                )
              )
            )
          )
        )
      )
    }

    "return oneOf for untagged union with shallow variants" in {
      val result = OutputSchemaBuilder.build(UntaggedTestUnion.schema)

      result mustBe Some(
        DObject(
          Map(
            "type"  -> DString("object"),
            "oneOf" -> DArray(
              IndexedSeq(
                DObject(
                  Map(
                    "type"       -> DString("object"),
                    "properties" -> DObject(
                      Map("caseOne" -> DObject(Map("type" -> DString("object"))))
                    )
                  )
                ),
                DObject(
                  Map(
                    "type"       -> DString("object"),
                    "properties" -> DObject(
                      Map("caseTwo" -> DObject(Map("type" -> DString("object"))))
                    )
                  )
                )
              )
            )
          )
        )
      )
    }

    "return oneOf for discriminated union with shallow variants" in {
      val result = OutputSchemaBuilder.build(DiscriminatedTestUnion.schema)

      result mustBe Some(
        DObject(
          Map(
            "type"  -> DString("object"),
            "oneOf" -> DArray(
              IndexedSeq(
                DObject(
                  Map(
                    "type"       -> DString("object"),
                    "properties" -> DObject(
                      Map("caseOne" -> DObject(Map("type" -> DString("object"))))
                    )
                  )
                ),
                DObject(
                  Map(
                    "type"       -> DString("object"),
                    "properties" -> DObject(
                      Map("caseTwo" -> DObject(Map("type" -> DString("object"))))
                    )
                  )
                )
              )
            )
          )
        )
      )
    }

    "wrap enum in result property per MCP spec" in {
      val result = OutputSchemaBuilder.build(AppliesTo.schema)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "result" -> DObject(
                  Map(
                    "type"  -> DString("string"),
                    "oneOf" -> DArray(
                      IndexedSeq(
                        DObject(Map("const" -> DString("client"), "title" -> DString("CLIENT"))),
                        DObject(Map("const" -> DString("server"), "title" -> DString("SERVER")))
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    }
  }

  "OutputSchemaBuilder with recursive=true" must {

    "return JSON schema for simple struct" in {
      val result = OutputSchemaBuilder.build(TestCaseOne.schema, recursive = true)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "value" -> DObject(Map("type" -> DString("string")))
              )
            )
          )
        )
      )
    }

    "unwrap httpPayload field and resolve recursively" in {
      // TestWithOutputResponse has @httpPayload body: TestResponseBody
      // In recursive mode, the payload's struct is fully resolved
      val result = OutputSchemaBuilder.build(TestWithOutputResponse.schema, recursive = true)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "testHeader"  -> DObject(Map("type" -> DString("string"))),
                "pathParam"   -> DObject(Map("type" -> DString("string"))),
                "testQuery"   -> DObject(Map("type" -> DString("string"))),
                "bodyMessage" -> DObject(Map("type" -> DString("string")))
              )
            )
          )
        )
      )
    }

    "resolve list member types recursively with httpPayload unwrap" in {
      val result = OutputSchemaBuilder.build(QueryResponse.schema, recursive = true)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "result" -> DObject(
                  Map(
                    "type"  -> DString("array"),
                    "items" -> DObject(Map("type" -> DString("string")))
                  )
                )
              )
            )
          )
        )
      )
    }

    "return tagged union variants resolved recursively" in {
      val result = OutputSchemaBuilder.build(TaggedTestUnion.schema, recursive = true)

      result mustBe Some(
        DObject(
          Map(
            "type"  -> DString("object"),
            "oneOf" -> DArray(
              IndexedSeq(
                DObject(
                  Map(
                    "type"       -> DString("object"),
                    "properties" -> DObject(
                      Map(
                        "caseOne" -> DObject(
                          Map(
                            "type"       -> DString("object"),
                            "properties" -> DObject(
                              Map("value" -> DObject(Map("type" -> DString("string"))))
                            )
                          )
                        )
                      )
                    )
                  )
                ),
                DObject(
                  Map(
                    "type"       -> DString("object"),
                    "properties" -> DObject(
                      Map(
                        "caseTwo" -> DObject(
                          Map(
                            "type"       -> DString("object"),
                            "properties" -> DObject(
                              Map(
                                "int" -> DObject(Map("type" -> DString("integer"))),
                                "doc" -> DObject(Map("type" -> DString("string")))
                              )
                            )
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    }

    "wrap enum in result property per MCP spec (recursive)" in {
      val result = OutputSchemaBuilder.build(AppliesTo.schema, recursive = true)

      result mustBe Some(
        DObject(
          Map(
            "type"       -> DString("object"),
            "properties" -> DObject(
              Map(
                "result" -> DObject(
                  Map(
                    "type"  -> DString("string"),
                    "oneOf" -> DArray(
                      IndexedSeq(
                        DObject(Map("const" -> DString("client"), "title" -> DString("CLIENT"))),
                        DObject(Map("const" -> DString("server"), "title" -> DString("SERVER")))
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    }
  }
}
