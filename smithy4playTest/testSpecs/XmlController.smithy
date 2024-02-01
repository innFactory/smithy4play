$version: "2"
namespace testDefinitions.test


service XmlControllerDef {
    version: "0.0.1",
    operations: [
        XmlTestWithInputAndOutput
    ]
}

@http(method: "POST", uri: "/xml/{xmlTest}")
operation XmlTestWithInputAndOutput {
    input: XmlTestInput
    output := {
        @httpHeader("content-type")
        @required
        contentType: String
        @required
        @httpPayload
        body: XmlTestOutput
    }
}

structure XmlTestInput {
    @httpLabel
    @required
    xmlTest: String
    @required
    @httpPayload
    body: XmlTestInputBody
}

structure XmlTestInputBody {
    @required
    requiredTest: String
    requiredInt: Integer

}


structure XmlTestOutput {
    @required
    requiredTestStringConcat: String
    requiredIntSquared: Integer
}

