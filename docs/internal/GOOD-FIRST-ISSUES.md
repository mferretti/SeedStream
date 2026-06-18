# Good First Issues — drafts

Staging file for `good first issue` tickets to file on GitHub. Each is small,
well-scoped, has clear acceptance criteria, and points at the real files a
newcomer needs. Copy a block into a new issue and apply the `good first issue`
(and suggested area) labels. Keep this file in sync as issues are filed/closed.

> The companion "Your First Contribution" on-ramp lives in
> [docs/CONTRIBUTING.md](../CONTRIBUTING.md).

---

## GFI-1 — Register additional Datafaker semantic types

**Labels:** `good first issue`, `area:generators`, `enhancement`
**Effort:** ~1–2 h

**Context.** SeedStream exposes Datafaker's providers as YAML "semantic" types
(e.g. `name.fullName`, `address.city`). The mapping lives in
[`generators/src/main/java/com/datagenerator/generators/semantic/DatafakerGenerator.java`](../../generators/src/main/java/com/datagenerator/generators/semantic/DatafakerGenerator.java).
Datafaker ships far more providers than are currently registered, so good
candidates (e.g. `Color`, `Animal`, `Book`, `Currency`, `IndustrySegments`) are
not yet reachable from a structure YAML.

**Tasks.**
- Pick 3–5 useful unregistered Datafaker providers.
- Register them following the existing pattern in `DatafakerGenerator`.
- Add a case for each to `DatafakerNewTypesTest` asserting a non-empty,
  locale-sensitive value is produced and that the **same seed gives the same
  value** (determinism).
- Document the new type names in [config/README.md](../../config/README.md)'s
  semantic-type list.

**Acceptance criteria.**
- [ ] New types resolve from a structure YAML and generate values.
- [ ] Each new type has a unit test, including a same-seed determinism assertion.
- [ ] `./gradlew :generators:test spotlessCheck` passes.
- [ ] config/README type list updated.

---

## GFI-2 — Add an example structure + job for a new domain

**Labels:** `good first issue`, `area:config`, `documentation`
**Effort:** ~1 h

**Context.** `config/structures/*.yaml` + `config/jobs/*.yaml` are the
copy-paste starting points for new users. The set is broad but missing some
everyday domains (e.g. `employee`, `flight_booking`, `support_ticket`). Adding
one is a great way to learn the type system without touching Java.

**Tasks.**
- Add `config/structures/<entity>.yaml` using primitives, an `enum[...]`, a
  `date[...]`, and at least one nested `object[...]` or `array[...]`.
- Add `config/jobs/file_<entity>.yaml` writing JSON to a file.
- Run it: `./gradlew :cli:run --args="execute --job config/jobs/file_<entity>.yaml --count 20"`.

**Acceptance criteria.**
- [ ] The job generates valid output with `--count 20`.
- [ ] Re-running with the same seed produces byte-for-byte identical output.
- [ ] Files follow the naming conventions in
      [CLAUDE.md](../../CLAUDE.md#naming-conventions).

---

## GFI-3 — Showcase non-default locale via `geolocation`

**Labels:** `good first issue`, `area:config`, `documentation`
**Effort:** ~1 h

**Context.** `geolocation` in a structure drives the Datafaker locale (e.g.
`italy` → Italian names/addresses). The 62-locale support is under-demonstrated.
See `DatafakerGeolocationTest` for how locale resolution works.

**Tasks.**
- Add a small structure + job that sets a non-English `geolocation` (e.g.
  `japan`, `germany`, `brazil`).
- Add a short "Locale-aware data" example to the README or config/README
  showing the same structure under two locales.

**Acceptance criteria.**
- [ ] Generated names/addresses are visibly locale-specific.
- [ ] Same seed + same locale → identical output documented in the example.
- [ ] `spotlessCheck` passes (docs only need no build, but verify links).

---

## GFI-4 — Friendlier error when a job or structure file is missing

**Labels:** `good first issue`, `area:cli`, `enhancement`
**Effort:** ~2 h

**Context.** Fail-fast at boundaries is a project principle (see
[CLAUDE.md](../../CLAUDE.md#error-handling)). Verify the message produced when
`--job` points at a non-existent file, or when a referenced
`object[structure_name]` has no matching structure file, is clear and
actionable (path + hint), not a raw stack trace. Entry points:
[`ExecuteCommand.java`](../../cli/src/main/java/com/datagenerator/cli/ExecuteCommand.java)
and the schema parsers.

**Tasks.**
- Reproduce both cases; note the current output.
- If unclear, throw/translate to a `ConfigurationException` with the offending
  path and a one-line hint.
- Add a unit test asserting the message (use the picocli test harness pattern in
  `ExecuteCommandTest`).

**Acceptance criteria.**
- [ ] Both missing-file cases print a clear message naming the path; no raw
      stack trace at default log level.
- [ ] A unit test locks the behaviour.
- [ ] `./gradlew :cli:test spotlessCheck` passes.

---

## GFI-5 — Raise unit-test coverage for an under-covered class

**Labels:** `good first issue`, `area:testing`
**Effort:** ~1–2 h

**Context.** The build enforces a 70% JaCoCo line-coverage minimum. Pick one
class sitting below the line and bring it up — a focused way to learn a module.

**Tasks.**
- Run `./gradlew test jacocoTestReport` and open the HTML report
  (`<module>/build/reports/jacoco/test/html/index.html`).
- Choose one under-covered, pure-logic class (avoid I/O-heavy ones).
- Add unit tests (JUnit 5 + AssertJ + Mockito) covering the gaps, including at
  least one same-seed determinism case where relevant.

**Acceptance criteria.**
- [ ] Chosen class's line coverage rises meaningfully (state before/after).
- [ ] Tests follow the `shouldDoXWhenY` naming from
      [CLAUDE.md](../../CLAUDE.md#testing-strategy).
- [ ] `./gradlew test spotlessCheck` passes.

---

## GFI-6 — Document and test the file-destination gzip option

**Labels:** `good first issue`, `area:destinations`, `documentation`
**Effort:** ~1–2 h

**Context.** The file destination supports compression (see
[`FileDestinationConfig.java`](../../destinations/src/main/java/com/datagenerator/destinations/file/FileDestinationConfig.java)
and `FileDestination`), but it's under-documented. Make it discoverable.

**Tasks.**
- Confirm how gzip output is enabled in job YAML; add an example job that writes
  a `.json.gz`.
- Document the option in config/README (and the README formats note if useful).
- Add/extend a test asserting a gzip-compressed file is produced and round-trips
  to the expected records.

**Acceptance criteria.**
- [ ] An example job produces a valid gzip file.
- [ ] Config docs describe the option.
- [ ] A test verifies compressed output decompresses to the expected records.
- [ ] `./gradlew :destinations:test spotlessCheck` passes.

---

### Labels to create (if absent)
`good first issue`, `area:generators`, `area:config`, `area:cli`,
`area:destinations`, `area:testing`.
