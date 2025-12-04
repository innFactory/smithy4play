$version: "2"

namespace testDefinitions.test

use alloy#simpleRestJson
use de.innfactory.smithy4play.mcp#exposeMcp

@simpleRestJson
service McpControllerService {
    version: "0.0.1",
    operations: [ReverseString]
}

@http(method: "POST", uri: "/reverseString", code: 200)
@exposeMcp(description: "Gets MCP data for testing purposes")
operation ReverseString {
    input: ReverseStringInput
    output: ReverseStringOutput
}

structure ReverseStringInput {
    @required
    text: String
}

structure ReverseStringOutput {
    @required
    reversed: String
}
