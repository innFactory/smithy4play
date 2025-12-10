$version: "2"
namespace testDefinitions.test

use aws.protocols#restXml
use de.innfactory.smithy4play.meta#contentTypes

@restXml
@contentTypes(general: ["application/xml", "application/json"])
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
        contentType: String
        @required
        @httpPayload
        body: XmlTestOutput
    }
}

structure XmlTestInput {
    @httpHeader("content-type")
    contentType: String
    @httpLabel
    @required
    xmlTest: String
    @required
    @httpPayload
    body: XmlTestInputBody
}


structure XmlTestInputBody {
    @xmlAttribute
    @required
    serverzeit: String
    @required
    requiredTest: String
    requiredInt: Integer
}


structure XmlTestOutput {
    @xmlAttribute
    @required
    serverzeit: String
    @required
    requiredTestStringConcat: String
    requiredIntSquared: Integer
}

