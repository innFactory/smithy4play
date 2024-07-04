$version: "1.0"

namespace de.innfactory.smithy4play.meta

@trait(selector: ":is(operation,service)")
structure contentTypes {
    @required
    input: ContentTypeList,
    @required
    output: ContentTypeList,
    @required
    error: ContentTypeList
}

list ContentTypeList {
    member: String
}
