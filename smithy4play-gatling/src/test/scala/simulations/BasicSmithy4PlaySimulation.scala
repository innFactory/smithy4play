package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class BasicSmithy4PlaySimulation extends Simulation {
  private val baseUrl = System.getProperty("baseUrl", "http://127.0.0.1:9000")

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // --- Scenarios ---

  // 1. POST /test/{pathParam} — path param + query + header + JSON body (original scenario)
  private val postTestWithOutput = scenario("POST testWithOutput")
    .exec(
      http("testWithOutput")
        .post("/test/thisIsAPathParam?testQuery=thisIsATestQuery")
        .header("Test-Header", "thisIsATestHeader")
        .body(StringBody("""{"message":"thisIsARequestBody"}"""))
        .asJson
        .check(status.is(200))
    )

  // 2. GET / — simplest possible route, no params
  private val getRoot = scenario("GET root")
    .exec(
      http("test (GET /)")
        .get("/")
        .check(status.is(200))
    )

  // 3. GET /health — middleware-exercised route, no params
  private val getHealth = scenario("GET health")
    .exec(
      http("health")
        .get("/health")
        .check(status.is(200))
    )

  // 4. GET /query — multiple query parameters
  private val getWithQuery = scenario("GET query")
    .exec(
      http("testWithQuery")
        .get("/query?testQuery=q1&testQueryTwo=q2&testQueryList=a&testQueryList=b&testQueryList=c")
        .check(status.is(200))
    )

  // 5. Mixed workload — round-robins through all the above in a single virtual user
  private val mixedWorkload = scenario("Mixed workload")
    .exec(
      http("mixed-root")
        .get("/")
        .check(status.is(200))
    )
    .exec(
      http("mixed-health")
        .get("/health")
        .check(status.is(200))
    )
    .exec(
      http("mixed-query")
        .get("/query?testQuery=q1&testQueryTwo=q2&testQueryList=x")
        .check(status.is(200))
    )
    .exec(
      http("mixed-post")
        .post("/test/param123?testQuery=value")
        .header("Test-Header", "hdr")
        .body(StringBody("""{"message":"hello"}"""))
        .asJson
        .check(status.is(200))
    )

  // --- Load profile (configurable via system properties) ---
  private val users  = Integer.getInteger("users", 20).toInt
  private val ramp   = Integer.getInteger("rampSeconds", 5).toInt
  private val steady = Integer.getInteger("steadySeconds", 30).toInt
  private val rate   = math.max(1, users / 2.0)

  setUp(
    postTestWithOutput.inject(
      rampUsers(users) during (ramp),
      constantUsersPerSec(rate) during (steady)
    ),
    getRoot.inject(
      rampUsers(users) during (ramp),
      constantUsersPerSec(rate) during (steady)
    ),
    getHealth.inject(
      rampUsers(users) during (ramp),
      constantUsersPerSec(rate) during (steady)
    ),
    getWithQuery.inject(
      rampUsers(users) during (ramp),
      constantUsersPerSec(rate) during (steady)
    ),
    mixedWorkload.inject(
      rampUsers(users) during (ramp),
      constantUsersPerSec(rate) during (steady)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.successfulRequests.percent.gte(99)
    )
}
