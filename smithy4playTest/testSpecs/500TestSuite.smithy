$version: "2"

namespace testDefinitions.test

use alloy#simpleRestJson
use smithy.test#httpRequestTests
use smithy.test#httpResponseTests


apply TestThatReturnsError @httpRequestTests([
    {
        id: "test3",
        method: "GET",
        uri: "/error",
        protocol: simpleRestJson,
        documentation: "500",
        code: 200
    }
])

apply TestThatReturnsError @httpResponseTests([
    {
        id: "test3",
        protocol: simpleRestJson,
        body: "{\"message\":\"this is supposed to fail\"}",
        documentation: "500",
        code: 200
    }
])