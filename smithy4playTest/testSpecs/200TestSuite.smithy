$version: "2"

namespace testDefinitions.test

use alloy#simpleRestJson
use smithy.test#httpRequestTests
use smithy.test#httpResponseTests

apply TestWithQuery @httpRequestTests([
    {
        id: "test1",
        method: "GET",
        uri: "/query",
        protocol: simpleRestJson,
        params: {
            "testQuery": "Hello there"
        },
    }
])
apply TestWithQuery @httpResponseTests([
    {
        id: "test1",
        protocol: simpleRestJson,
        code: 200
    }
])

apply Test @httpResponseTests([
    {
        id: "test2",
        protocol: simpleRestJson,
        params: {
            message: "TestWithSimpleResponse"
        },
        code: 200
    }
])