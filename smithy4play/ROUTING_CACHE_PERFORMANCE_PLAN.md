# Routing Handler Cache Performance Plan

Improve the per-request performance of the routing handler in `PlayPartialFunctionRouter`
and the surrounding codec/content-type resolution layer.

**Status:** Planned

---

## Current Architecture

When Play receives a request it flows through:

1. `BaseRouter.routes` (always `isDefinedAt = true`) -> `applyInternalHandler`
2. `chainedRoutes` (lazy `PartialFunction.orElse` chain of all controllers)
3. Each controller's `Smithy4PlayRouter.mapper` delegates to `PlayPartialFunctionRouter`
4. `PlayPartialFunctionRouter.isDefinedAt` groups endpoints by HTTP method,
   iterates candidates, matches path segments, and **caches** the result in a
   `ThreadLocal[CachedMatch]`
5. `PlayPartialFunctionRouter.apply` retrieves the cached match, resolves
   content types (ConcurrentHashMap cache), builds codecs (ConcurrentHashMap
   cache), and returns the handler
6. The handler runs through the pre-composed middleware chain, then through
   `Smithy4PlayServerEndpoint` (decode -> business logic -> encode)
7. The `ThreadLocal` cache is cleared in a `finally` block

### Relevant Files

| File | Role |
|------|------|
| `routing/internal/PlayPartialFunctionRouter.scala` | Core routing + ThreadLocal match cache |
| `routing/internal/Smithy4PlayRouter.scala` | Wraps `PlayPartialFunctionRouter`, builds server codecs |
| `routing/internal/BaseRouter.scala` | Chains controller `PartialFunction`s |
| `routing/internal/package.scala` | Path deconstruction, HTTP URI building |
| `codecs/Codec.scala` | Encoder/decoder ConcurrentHashMap caches |
| `codecs/CodecSupport.scala` | Content-type resolution cache |
| `codecs/EndpointContentTypes.scala` | Pre-computed `JsonOnly` singleton |
| `routing/middleware/Middleware.scala` | Pre-composed middleware chain |

---

## Planned Changes

### 1. Eliminate Redundant Path Parsing

**Priority:** High
**Impact:** Removes 1-2 redundant `deconstructPath` + `toSmithy4sHttpUri` calls per request

#### Problem

`Smithy4PlayRouter` passes `getUri` and `getMethod` lambdas to
`PlayPartialFunctionRouter` (`Smithy4PlayRouter.scala:67-75`). Each call to
`getUri` invokes `deconstructPath(requestHeader.path)` and
`toSmithy4sHttpUri(...)`, which allocate new `IndexedSeq` and `HttpUri`
objects. These lambdas are called in `findMatch` on every `isDefinedAt` call
that results in a cache miss -- and the same parsing is performed again later
inside `toSmithy4sHttpRequest` when the request body is assembled.

A `ParsedRequestHead` class already exists
(`PlayPartialFunctionRouter.scala:32-53`) and was designed to cache these
parsed results, but it is never used in the actual routing flow.

#### Changes

**`PlayPartialFunctionRouter.scala`:**
- Modify `findMatch` to perform path parsing internally (call
  `deconstructPath` and `getSmithy4sHttpMethod` once) instead of delegating
  to the `getMethod`/`getUri` lambdas, and store the parsed result in
  `CachedMatch`.
- Add the parsed `HttpUri` and `HttpMethod` to `CachedMatch` so that
  subsequent `apply` calls can reuse them.
- Alternatively, construct a `ParsedRequestHead` at the top of `findMatch`
  and thread it through.

**`Smithy4PlayRouter.scala`:**
- Simplify the `getUri` and `getMethod` lambdas. If parsing moves into
  `findMatch`, these lambdas can be removed or reduced to trivial
  accessors on a pre-parsed structure.
- Ensure `toSmithy4sHttpRequest` (called later in the handler) reuses
  the already-parsed path segments from the cached match rather than
  calling `deconstructPath` a second time.

#### Savings Per Request

- ~1 `String.split` + `IndexedSeq` allocation (path parsing)
- ~1 `HttpUri` allocation
- ~1 `HttpMethod.fromStringOrDefault` lookup

---

### 2. Pre-compute Codecs for JSON-only Endpoints

**Priority:** Medium
**Impact:** Eliminates per-request content-type resolution + codec construction for JSON-only endpoints

#### Problem

In `PlayPartialFunctionRouter.makeHttpEndpointHandler` (`lines 168-179`),
the `handler` lambda is invoked on every request. It calls:
1. `resolveContentType(endpoint.hints, service.hints, requestHeader)` --
   content type resolution
2. `codecs(contentType)` -- codec factory call
3. `codec(endpoint.schema)` -- schema-specific codec compilation

For JSON-only endpoints (the vast majority), the resolved content type is
always `EndpointContentTypes.JsonOnly` and the resulting codec is always
identical. The `CodecSupport.resolveEndpointContentTypes` fast path returns
`JsonOnly` quickly, but the surrounding codec construction is still
repeated.

#### Changes

**`PlayPartialFunctionRouter.scala`** (in `makeHttpEndpointHandler`):
- At endpoint registration time, call
  `CodecSupport.isJsonOnlyEndpoint(endpoint.hints, service.hints)`.
- If `true`, pre-compute `codecs(EndpointContentTypes.JsonOnly)` and
  `preCompiledCodec = codec(endpoint.schema)` once, and capture them in
  the `HttpEndpointHandler`.
- In the per-request handler lambda, check whether pre-compiled codecs
  exist and skip the `resolveContentType` + `codecs(...)` calls entirely.
- For non-JSON-only endpoints, keep the current per-request resolution
  unchanged.

#### Savings Per Request (JSON-only endpoints)

- ~1 `resolveContentType` call (including hint lookups)
- ~1 `codecs(contentType)` factory invocation
- ~1 `codec(endpoint.schema)` compilation

---

### 3. Use Array Instead of List for Endpoint Lookup

**Priority:** Low-Medium
**Impact:** Better CPU cache locality during sequential endpoint matching

#### Problem

`perMethodEndpoint` is typed as `Map[HttpMethod, List[HttpEndpointHandler]]`
(`PlayPartialFunctionRouter.scala:190-191`). During route matching, the list
is iterated sequentially via `.iterator.flatMap { ... }.nextOption()`.
`List` is a linked list -- each node is a separate heap object, leading to
pointer chasing and poor cache locality.

#### Changes

**`PlayPartialFunctionRouter.scala`:**
- Change `httpEndpointHandlers` from `List[HttpEndpointHandler]` to
  `Array[HttpEndpointHandler]`.
- Change `perMethodEndpoint` from `Map[HttpMethod, List[...]]` to
  `Map[HttpMethod, Array[...]]`.
- Update the construction:
  ```scala
  private val httpEndpointHandlers: Array[HttpEndpointHandler] =
    service.endpoints.toList.map(makeHttpEndpointHandler(_))
      .collect { case Right(ep) => ep }
      .toArray

  private val perMethodEndpoint: Map[HttpMethod, Array[HttpEndpointHandler]] =
    httpEndpointHandlers.groupBy(_.httpEndpoint.method)
  ```
- Update the `findMatch` iteration to use array indexing or
  `.iterator` (Array's iterator is already contiguous-memory).

#### Savings Per Request

- Eliminates linked-list pointer chasing during endpoint matching
- Better L1/L2 cache utilization for services with many endpoints

---

### 4. Guard Debug Logging with `isDebugEnabled`

**Priority:** Low
**Impact:** Avoids ~4 string allocations per request when debug logging is disabled

#### Problem

In `PlayPartialFunctionRouter.findMatch` (lines 106, 112, 114, 123),
`logger.debug(...)` calls use string concatenation (`+`):

```scala
logger.debug("Finding match for method: " + method + " and path segments: " + pathSegments)
logger.debug("per method endpoint map: " + perMethodEndpoint)
logger.debug("saving match result to cache: " + result)
```

These always allocate concatenated strings and call `.toString` on
complex objects (`perMethodEndpoint`, `result`), even when debug logging
is disabled. Other files in the codebase (e.g., `Smithy4PlayRouter.scala:87`)
already use `if (logger.isDebugEnabled)` guards correctly.

#### Changes

**`PlayPartialFunctionRouter.scala`** (in `findMatch`):
- Wrap all four `logger.debug(...)` calls in `if (logger.isDebugEnabled)`
  blocks:
  ```scala
  if (logger.isDebugEnabled) {
    logger.debug("Cache miss for request head, performing match")
    logger.debug("Finding match for method: " + method + " and path segments: " + pathSegments)
    logger.debug("per method endpoint map: " + perMethodEndpoint)
  }
  // ... matching logic ...
  if (logger.isDebugEnabled) {
    logger.debug("saving match result to cache: " + result)
  }
  ```

#### Savings Per Request

- ~4 string concatenations + `.toString` calls on complex objects avoided
- Particularly impactful for the `perMethodEndpoint.toString` call which
  serializes the entire endpoint map

---

## Impact Summary

| # | Change | Per-Request Savings | Complexity |
|---|--------|-------------------|------------|
| 1 | Eliminate redundant path parsing | ~1-2 `deconstructPath` + URI alloc | Medium |
| 2 | Pre-compute JSON codecs | ~1 content-type resolution + codec build | Medium |
| 3 | Array instead of List | Better CPU cache locality | Trivial |
| 4 | Debug log guards | ~4 string allocations | Trivial |

## Verification

- Existing tests in `RouterChainingRegressionTest` must continue to pass
- JMH benchmarks in `smithy4play-benchmarks` can be extended to measure
  the before/after impact of changes 1-3
- Manual verification that debug logging still works when enabled
