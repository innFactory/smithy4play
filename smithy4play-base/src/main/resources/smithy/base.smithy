$version: "1.0"

namespace de.innfactory.smithy4play.meta

@trait(selector: ":is(operation,service)")
structure contentTypes {
    general: ContentTypeList,
    input: ContentTypeList,
    output: ContentTypeList,
    error: ContentTypeList
}

list ContentTypeList {
    member: String
}
