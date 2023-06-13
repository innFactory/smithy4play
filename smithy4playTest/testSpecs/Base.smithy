$version: "2"

namespace testDefinitions.test

@trait(
    selector: "service",
    breakingChanges: [{change: "remove"}]
)
structure testMiddleware {}


@trait(selector: ":is(service, operation)")
@uniqueItems
list middleware {
    member: MiddlewareTraitReference
}

@idRef(selector: "[trait]")
@private
string MiddlewareTraitReference
