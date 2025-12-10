$version: "2"

namespace testDefinitions.test

use alloy#discriminated
use alloy#simpleRestJson
use alloy#untagged
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
    taggedTestUnion: TaggedTestUnion
    untaggedTestUnion: UntaggedTestUnion
    discriminatedTestUnion: DiscriminatedTestUnion
}

structure ReverseStringOutput {
    @required
    reversed: String
    @required
    @documentation("Describes the text length without spaces times two")
    v: Integer
}

@discriminated("tpe")
union DiscriminatedTestUnion {
    caseOne: TestCaseOne,
    caseTwo: TestCaseTwo,
}

structure TestCaseOne {
    @required
    value: String
}

structure TestCaseTwo {
    @required
    int: Integer
    @required
    doc: Document
}

union TaggedTestUnion {
    caseOne: TestCaseOne,
    caseTwo: TestCaseTwo,
}

@untagged
union UntaggedTestUnion {
    caseOne: TestCaseOne,
    caseTwo: TestCaseTwo,
}