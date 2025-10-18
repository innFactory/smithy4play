$version: "2.0"

namespace de.innfactory.mcp

@trait(selector: "operation")
structure mcpTool {}

@trait(selector: "operation")
string mcpName

@trait(selector: "operation")
string mcpDescription

@trait(selector: "operation")
list mcpCategories {
    member: String
}

@trait(selector: "operation")
string mcpVersion

@trait(selector: "member")
structure mcpParameter {
    example: Document
}

@trait(selector: "operation")
list mcpExamples {
    member: Example
}

structure Example {
    @required
    title: String

    @required
    input: Document

    @required
    output: Document
}