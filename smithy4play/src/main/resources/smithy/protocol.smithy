$version: "1.0"

namespace de.innfactory.smithy4play.openapi.protocols

@protocolDefinition(
traits: [
smithy.api#default
smithy.api#error
smithy.api#http
smithy.api#httpError
smithy.api#httpHeader
smithy.api#httpLabel
smithy.api#httpPayload
smithy.api#httpPrefixHeaders
smithy.api#httpQuery
smithy.api#httpQueryParams
smithy.api#httpResponseCode
smithy.api#jsonName
smithy.api#length
smithy.api#pattern
smithy.api#range
smithy.api#required
smithy.api#timestampFormat
alloy#uuidFormat
alloy#discriminated
alloy#nullable
alloy#untagged
]
)
@trait(selector: "service")
structure smithy4PlayService {}