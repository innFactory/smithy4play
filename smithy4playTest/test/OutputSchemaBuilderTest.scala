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

      result mustBe DObject(
        Map(
          "type"       -> DString("object"),
          "properties" -> DObject(
            Map(
              "value" -> DObject(Map("type" -> DString("string")))
            )
          )
        )
      )
    }

    "return JSON schema for struct with multiple fields" in {
      val result = OutputSchemaBuilder.build(JsonInput.schema)

      result mustBe DObject(
        Map(
          "type"       -> DString("object"),
          "properties" -> DObject(
            Map(
              "message" -> DObject(Map("type" -> DString("string")))
            )
          )
        )
      )
    }

    "return shallow types for nested struct fields" in {
      val result = OutputSchemaBuilder.build(TestRequestWithQueryAndPathParams.schema)

      result mustBe DObject(
        Map(
          "type"       -> DString("object"),
          "properties" -> DObject(
            Map(
              "pathParam"  -> DObject(Map("type" -> DString("string"))),
              "testQuery"  -> DObject(Map("type" -> DString("string"))),
              "testHeader" -> DObject(Map("type" -> DString("string"))),
              "body"       -> DObject(Map("type" -> DString("object")))
            )
          )
        )
      )
    }

    "return shallow type for list fields" in {
      val result = OutputSchemaBuilder.build(QueryResponse.schema)

      result mustBe DObject(
        Map(
          "type"       -> DString("object"),
          "properties" -> DObject(
            Map(
              "body" -> DObject(Map("type" -> DString("array")))
            )
          )
        )
      )
    }

    "wrap oneOf union in root object for tagged union with shallow variants" in {
      val result = OutputSchemaBuilder.build(TaggedTestUnion.schema)

      result mustBe DObject(
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
    }

    "wrap oneOf union in root object for untagged union with shallow variants" in {
      val result = OutputSchemaBuilder.build(UntaggedTestUnion.schema)

      result mustBe DObject(
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
    }

    "wrap oneOf union in root object for discriminated union with shallow variants" in {
      val result = OutputSchemaBuilder.build(DiscriminatedTestUnion.schema)

      result mustBe DObject(
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
    }

    "wrap enum in root object with oneOf const/title format" in {
      val result = OutputSchemaBuilder.build(AppliesTo.schema)

      result mustBe DObject(
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
    }
  }

  "OutputSchemaBuilder with recursive=true" must {

    "return JSON schema for simple struct" in {
      val result = OutputSchemaBuilder.build(TestCaseOne.schema, recursive = true)

      result mustBe DObject(
        Map(
          "type"       -> DString("object"),
          "properties" -> DObject(
            Map(
              "value" -> DObject(Map("type" -> DString("string")))
            )
          )
        )
      )
    }

    "resolve nested struct fields recursively" in {
      val result = OutputSchemaBuilder.build(TestRequestWithQueryAndPathParams.schema, recursive = true)

      result mustBe DObject(
        Map(
          "type"       -> DString("object"),
          "properties" -> DObject(
            Map(
              "pathParam"  -> DObject(Map("type" -> DString("string"))),
              "testQuery"  -> DObject(Map("type" -> DString("string"))),
              "testHeader" -> DObject(Map("type" -> DString("string"))),
              "body"       -> DObject(
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
          )
        )
      )
    }

    "resolve list member types recursively" in {
      val result = OutputSchemaBuilder.build(QueryResponse.schema, recursive = true)

      result mustBe DObject(
        Map(
          "type"       -> DString("object"),
          "properties" -> DObject(
            Map(
              "body" -> DObject(
                Map(
                  "type"  -> DString("array"),
                  "items" -> DObject(Map("type" -> DString("string")))
                )
              )
            )
          )
        )
      )
    }

    "wrap tagged union variants in root object when resolved recursively" in {
      val result = OutputSchemaBuilder.build(TaggedTestUnion.schema, recursive = true)

      result mustBe DObject(
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
    }

    "wrap enum in root object with oneOf const/title format" in {
      val result = OutputSchemaBuilder.build(AppliesTo.schema, recursive = true)

      result mustBe DObject(
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
    }
  }
}
