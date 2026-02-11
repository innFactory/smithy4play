import models.TestBase
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class RouterChainingRegressionTest extends TestBase {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().build()

  "router chaining" must {

    "return 404 for unknown paths" in {
      val req = route(app, FakeRequest("GET", "/does-not-exist")).get
      status(req) mustBe 404
    }

    "route to XmlController (non-first controller)" in {
      val xml =
        <XmlTestInputBody serverzeit="05.02.2024">
          <requiredTest>Hello</requiredTest>
          <requiredInt>2</requiredInt>
        </XmlTestInputBody>

      val req = route(
        app,
        FakeRequest("POST", "/xml/Concat")
          .withHeaders(("content-type", "application/xml"))
          .withXmlBody(xml)
      ).get

      status(req) mustBe 200
    }

    "not match valid path with wrong http method" in {
      val req = route(app, FakeRequest("GET", "/xml/Concat")).get
      status(req) mustBe 404
    }
  }
}
