$version: "2"

namespace testDefinitions.test

@trait(
    selector: "service",
    breakingChanges: [{change: "remove"}]
)
structure testMiddleware {}

@trait(
    selector: "operation",
    breakingChanges: [{change: "remove"}]
)
structure disableTestMiddleware {}
@trait(
    selector: "operation",
    breakingChanges: [{change: "remove"}]
)
structure changeStatusCode {}



