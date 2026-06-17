# sbt 2 migration status

sbt 2.0.0 went GA on 2026-06-14. It is a Scala 3-based rewrite: build definitions and
plugins compile against a Scala 3 API, every plugin needs a dedicated sbt 2 artifact, and
all tasks are cached by default (results need a `sjsonnew.JsonFormat`, or must opt out via
`Def.uncached`). Migrating the whole build is currently **blocked** on upstream releases,
but the groundwork that can be done safely on sbt 1.x has been done.

> ⚠️ **Pre-release versions in use.** To get onto the sbt 2-capable lines, this branch now
> depends on **Play 3.1.0-M9** (milestone — no GA yet), **scalatestplus-play 8.0.0-M2**
> (milestone), and **Scala 3.8.4** (the "Next" line, *not* the 3.3 LTS). These are not
> production-stable; the branch is a migration staging ground until those lines reach GA.

## Done (already on this branch)

The sbt-1.x-only prep below is fully backward compatible. The Play 3.1.0 upgrade after it
pulls in the pre-release versions noted above. Everything is verified: all modules compile,
`scalafmt*Check` is clean, all 50 tests pass, and the codegen plugin compiles on both the
sbt 1.x and sbt 2.0.0 APIs.

- **Upgraded to the latest sbt 1.x** — `project/build.properties` `1.9.8 → 1.12.12`. This
  is the recommended first migration step and unblocks plugins that now require sbt 1.12.9+.
- **Bumped sbt-scalafmt** `2.5.2 → 2.6.1` (cross-published for sbt 1.x and 2.x; requires
  sbt ≥ 1.12.9). The scalafmt *engine* stays pinned at 3.7.15 in `.scalafmt.conf`, so
  formatting output is unchanged.
- **Removed `com.lucidchart % sbt-cross`** — it was unused (no `crossBuild`/`CrossPlugin`
  references anywhere) and is abandoned (last release 2019) with no sbt 2 build. One blocker
  eliminated outright.
- **Cross-built our own sbt plugin `smithy4play-sbt-codegen` for sbt 2.** It now compiles
  against both the sbt 1.x and sbt 2.0.0 APIs:
  - `crossScalaVersions := Seq("2.12.21", "3.8.4")` with a `pluginCrossBuild / sbtVersion`
    mapping (`2.12 → 1.12.12`, else `2.0.0`). Scala **3.8.4** matches sbt 2.0.0's own build
    (TASTy forward-compat requires our plugin's Scala ≥ sbt's).
  - A small hand-rolled `PluginCompat` shim in `src/main/scala-2` and `src/main/scala-3`
    bridges the two API differences we hit:
    1. `Attributed#data` on a classpath is `File` (sbt 1) vs `xsbti.HashedVirtualFileRef`
       (sbt 2) — `PluginCompat.toFiles(...)` resolves both to `Seq[File]` via `fileConverter`.
    2. `Def.uncached { ... }` opts the redefined `Compile / compile` task out of sbt 2's
       result cache (its `CompileAnalysis` return type has no `JsonFormat`). On sbt 1.x the
       shim provides `Def.uncached` as a no-op identity extension.
  - The meta-build compiles the plugin sources directly (`project/build.sbt`), so it also
    picks up the `scala-2` shim dir and depends on nothing new.

### Play 3.1.0 upgrade (pulls in pre-release versions — see warning above)

- **Play `3.0.11 → 3.1.0-M9`** (`project/Dependencies.scala` + `project/plugins.sbt`). This
  is the only sbt 2-capable Play line; GA is not out. Its sbt-plugin still publishes an
  sbt 1.x axis, so it works on sbt 1.12.12 today.
- **Scala `3.3.8 → 3.8.4`** (`project/Dependencies.scala`). *Forced* by Play 3.1.0-M9, which
  is built with Scala 3.8.3 — the 3.3.8 compiler cannot read its newer TASTy (manifests as
  `AssertionError: ... has non-class parent` while reading `scala3-library` 3.8.3). 3.8.4 is
  the latest Next and matches sbt 2.0.0's own Scala version. **Leaves the 3.3 LTS line.**
- **scalatestplus-play `7.0.2 → 8.0.0-M2`** — 7.0.x targets Play 3.0.x; 8.0.0-Mx tracks 3.1.0.
- **Jackson pin `2.14.3 → 2.21.2`** (`build.sbt`, both override blocks) — Play 3.1.0 / Pekko
  1.5.0 require `jackson-core`'s `StreamReadConstraints` (added in 2.15); the old 2.14.3 pin
  threw `ClassNotFoundException` at Guice injector creation. (`jackson-annotations` is `2.21`.)
- **`javax.inject` → `jakarta.inject`** in 9 hand-written sources (core, mcp, test
  controllers/middlewares) — Play 3.1 completed the Jakarta EE namespace migration.

> We deliberately did **not** use the Scala Center `sbt2-compat` library even though it
> provides exactly these helpers (`toFiles`, `Def.uncached`). When cross-building from an
> sbt 1.x launcher, `addSbtPlugin` generates the wrong sbt-2 cross-coordinate
> (`sbt2-compat_3_2.0`) while the artifact is published as `sbt2-compat_sbt2_3`, so it
> fails to resolve. The hand-rolled shim is tiny, dependency-free, and avoids that.

## Blockers (cannot migrate until these ship)

| Dependency | Needs | Status (2026-06-16) |
|---|---|---|
| `smithy4s-sbt-codegen` + `smithy4s-core` | sbt 2 codegen plugin | **No release.** sbt 2 support targets the 0.19.x line — PRs [disneystreaming/smithy4s#1974](https://github.com/disneystreaming/smithy4s/pull/1974) (series/0.19) and [#1973](https://github.com/disneystreaming/smithy4s/pull/1973) (backport to 0.18) opened 2026-06-15, unmerged. **Primary blocker.** |
| `io.gatling % gatling-sbt` | sbt 2 cross-build | No sbt 2 build started (still pinned to Scala 2.12 / sbt 1.x). Used by `smithy4play-gatling`. |
| `com.codecommit %% sbt-github-packages` | sbt 2 build or replacement | Abandoned since 2021, no sbt 2. Used for publishing — see replacement note below. |

Ready / no longer blocking: `sbt-scalafmt` 2.6.1, `sbt-scoverage` 2.4.4, `sbt-jmh` 0.4.8
(all publish an sbt 2 axis); `sbt-cross` removed; Play moved to the 3.1.0 line (sbt 2-capable,
currently on milestone M9 — wait for 3.1.0 GA before considering this production-stable).

## Remaining steps for the actual switch (when blockers clear)

1. Bump `smithy4s` to the first 0.19.x that publishes an sbt 2 codegen plugin; regenerate
   and fix any generated-code/runtime changes (treat as its own migration — see the
   dependency check, this is a breaking minor).
2. Move Play from milestone **3.1.0-M9 → 3.1.0 GA** once released (and scalatestplus-play
   8.0.0-M2 → GA); re-pin Jackson if the GA bumps it. Re-evaluate staying on Scala 3.8.x
   vs. a future LTS that supports the same TASTy.
3. Resolve `gatling-sbt` — either wait for an sbt 2 build or move `smithy4play-gatling` to
   run Gatling another way.
4. Replace `sbt-github-packages`. GitHub Packages is a plain Maven repo, so the plugin can
   be dropped in favour of native sbt config (no sbt 2 plugin needed):
   ```scala
   publishTo := Some("GitHub" at "https://maven.pkg.github.com/innFactory/smithy4play")
   credentials += Credentials(
     "GitHub Package Registry", "maven.pkg.github.com", "innFactory", sys.env("GITHUB_TOKEN")
   )
   ```
   (plus a matching resolver for consumers). Verify against the existing
   `publishSmithy4Play` / `publishLocalBundle` aliases in `build.sbt`.
5. Set `project/build.properties` `sbt.version = 2.x`, convert build definitions per the
   [migration guide](https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html),
   and re-run the whole build.
6. **Publishing the codegen plugin for sbt 2:** the cross-build config is in place, but the
   sbt-2 artifact must be *published from an sbt 2.x launcher* so it gets the correct
   `_sbt2_3` coordinate. An sbt 1.x launcher would publish it as `_3_2.0`, which sbt 2
   consumers won't resolve. Wire this into the release once the project itself runs on sbt 2.

## References

- sbt 2.0.0 release / "Last mile towards sbt 2": <https://www.scala-lang.org/blog/2026/04/14/last-mile-towards-sbt2.html>
- Migrating plugins with sbt2-compat: <https://www.scala-lang.org/blog/2026/03/02/sbt2-compat.html>
- Migrating from sbt 1.x: <https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html>
- sbt 2.x plugin migration tracker: <https://github.com/sbt/sbt/wiki/sbt-2.x-plugin-migration>
