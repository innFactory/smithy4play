$version: "2"

namespace testDefinitions.test

use alloy#discriminated
use alloy#simpleRestJson
use alloy#untagged
use de.innfactory.smithy4play.meta#exposeMcp
use de.innfactory.smithy4play.meta#exposeMcpService
use de.innfactory.smithy4play.meta#hideMcp

@simpleRestJson
@exposeMcpService(description: "A controller for string manipulation operations")
service McpControllerService {
    version: "0.0.1",
    operations: [ReverseString, HiddenOperation]
}

@http(method: "POST", uri: "/reverseString", code: 200)
@exposeMcp(description: "Gets MCP data for testing purposes")
operation ReverseString {
    input: ReverseStringInput
    output: ReverseStringOutput
}

@http(method: "POST", uri: "/hidden", code: 200)
@hideMcp
operation HiddenOperation {
    input: HiddenOperationInput
    output: HiddenOperationOutput
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

structure HiddenOperationInput {
    @required
    value: String
}

structure HiddenOperationOutput {
    @required
    result: String
}