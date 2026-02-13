import de.innfactory.smithy4play.mcp.server.util.OutputSchemaBuilder
import models.TestBase
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import smithy4s.Document
import smithy.test.AppliesTo
import testDefinitions.test.*

class OutputSchemaBuilderTest extends TestBase {

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  private def toStringList(doc: Document): List[String] =
    doc match {
      case Document.DArray(values) => values.toList.collect { case Document.DString(s) => s }
      case _                       => List.empty
    }

  "OutputSchemaBuilder" must {

    "return field names for simple struct" in {
      val result = OutputSchemaBuilder.build(TestCaseOne.schema)

      toStringList(result) mustBe List("value")
    }

    "return field names for struct with multiple fields" in {
      val result = OutputSchemaBuilder.build(JsonInput.schema)

      toStringList(result) mustBe List("message")
    }

    "return field names for struct with nested struct" in {
      val result = OutputSchemaBuilder.build(TestRequestWithQueryAndPathParams.schema)

      toStringList(result) mustBe List("pathParam", "testQuery", "testHeader", "body")
    }

    "return field names for struct with optional list" in {
      val result = OutputSchemaBuilder.build(QueryResponse.schema)

      toStringList(result) mustBe List("body")
    }

    "return alternative labels for tagged union" in {
      val result = OutputSchemaBuilder.build(TaggedTestUnion.schema)

      toStringList(result) mustBe List("caseOne", "caseTwo")
    }

    "return alternative labels for untagged union" in {
      val result = OutputSchemaBuilder.build(UntaggedTestUnion.schema)

      toStringList(result) mustBe List("caseOne", "caseTwo")
    }

    "return alternative labels for discriminated union" in {
      val result = OutputSchemaBuilder.build(DiscriminatedTestUnion.schema)

      toStringList(result) mustBe List("caseOne", "caseTwo")
    }

    "return empty list for enum" in {
      val result = OutputSchemaBuilder.build(AppliesTo.schema)

      toStringList(result) mustBe List.empty
    }

    "not expand nested struct fields" in {
      val result = OutputSchemaBuilder.build(TestRequestWithQueryAndPathParams.schema)

      val fields = toStringList(result)
      fields must contain("body")
      fields must not contain "body.message"
    }

    "not expand list member fields" in {
      val result = OutputSchemaBuilder.build(QueryResponse.schema)

      val fields = toStringList(result)
      fields mustBe List("body")

    }
  }
}
