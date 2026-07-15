# seedstream inspect — v1 Spec

Status: implemented. Scope: OpenAPI, SQL DDL **and** Protobuf → SeedStream structure YAML.
Supersedes the open questions from the design phase with locked decisions.

## 1. Scope

- **In**: OpenAPI 3.x specs (`.yaml` / `.json`, object schemas under `components.schemas`),
  SQL DDL (`.sql`, `CREATE TABLE` statements incl. foreign keys → `ref[...]`), and compiled
  Protobuf **FileDescriptorSet**s (`.desc` / `.binpb` / `.protoset` from `protoc --descriptor_set_out`
  or `buf build -o`; one structure per message, nested messages → `object[...]`, `repeated` → `array[...]`,
  `enum` → `enum[...]`). Protobuf carries no value bounds, so scalars use default ranges; `bytes`,
  `map<k,v>`, well-known dynamic types (`Any`/`Struct`/…) and `oneof` members are flagged for review.
  Parsing `.proto` source directly is not supported — pre-compile to a descriptor set.
- **Out**: `alias` emission, nullable/required hints, geolocation/locale, primary-key handling.
- One `inspect` subcommand on the existing `datagenerator` CLI. No separate binary.
- Format auto-detected from extension (`.sql` → DDL, `.desc`/`.binpb`/`.protoset` → Protobuf,
  `.yaml`/`.yml`/`.json` → OpenAPI); `--format openapi|ddl|protobuf` overrides.

```bash
./seedstream inspect api.yaml      --output config/structures/
./seedstream inspect schema.sql    --output config/structures/ --force
./seedstream inspect schema.desc   --output config/structures/   # protoc/buf descriptor set
```

Flags:
| flag | default | meaning |
|---|---|---|
| `--output <dir>` | `config/structures/` | where YAML files are written |
| `--force` | off | overwrite existing files (else skip + warn — never silent clobber) |
| `--format openapi\|ddl\|protobuf` | auto | input format override; supports all three implemented inspectors |
| `--faker-types <file>` | unset | optional custom Datafaker types config loaded before inspection |
| `--best-effort` | off | DDL only: emit the parseable subset and warn on unparseable `CREATE TABLE`s instead of aborting |
| `--nest[=auto\|all\|none]` | `none` | DDL only: invert `1:n`/`1:1` FKs into nested `array[object[child]]`/`object[child]` — see §9 |
| `--nest-default-count <min..max>` | `1..10` | DDL only: multiplicity for synthesized nested arrays — see §9 |

## 2. Module

New Gradle module `inspector`, dependency edge `cli → inspector → schema → core`.
Parser lib (`swagger-parser` / `jackson`) isolated in `inspector`, never leaks into the
generation hot path. **Module creation requires maintainer confirmation** — gate before build.

## 3. Type mapping

Resolution order per property:
1. explicit `format` (table below)
2. `enum` present → `enum[...]`
3. numeric bounds present → bounded `int`/`decimal`
4. name-hint heuristic (§5)
5. type-default fallback (§4)

| OpenAPI | SeedStream |
|---|---|
| `string` + `format: email` | `email` |
| `string` + `format: date` | `date[<dateMin>..<dateMax>]` |
| `string` + `format: date-time` | `timestamp[now-<window>..now]` |
| `string` + `format: uuid` | `uuid` |
| `string` + `enum:[A,B,C]` | `enum[A,B,C]` |
| `string` + `maxLength: n` | `char[1..n]` |
| `string` (no format/len) | name-hint → else `char[1..50]` |
| `integer` + min/max | `int[min..max]` |
| `integer` (no bounds) | `int[<intMin>..<intMax>]` |
| `number` + min/max | `decimal[min..max]` |
| `number` (no bounds) | `decimal[<decMin>..<decMax>]` |
| `boolean` | `boolean` |
| `array` of T, `minItems`/`maxItems` | `array[<T>, min..max]` |
| `$ref: #/.../Foo` | `object[foo]` + emit `foo.yaml` (recurse) |

## 4. Default ranges (configurable later; constants for v1)

No bound in source → use these documented defaults. In v1, these are treated as expected
`DEFAULT_RANGE` mappings (silent by design), while only `NAME_HINT` and `UNKNOWN_TYPE`
mappings receive inline review comments (§7a):

| placeholder | value |
|---|---|
| `intMin..intMax` | `1..999999` |
| `decMin..decMax` | `0.0..9999.99` |
| `dateMin..dateMax` | `2020-01-01..2030-12-31` |
| `date-time window` (`<window>`) | `365d` → `timestamp[now-365d..now]` |
| default string | `char[1..50]` |
| default array bounds | `1..10` |

## 5. Name-hint heuristics

- Applied **only** when no `format` and no `enum`.
- Match on **word/token boundaries**, not raw substring. Split field name on
  camelCase / `snake_case` / `-` into tokens; match whole tokens.
  → `email` matches the `email` token in `email`, `userEmail`, and `email_verified_at`; it does
  **not** match a bare substring inside a single token such as `emailish`.
- First matching rule wins; rule order is the table order.

| token(s) | SeedStream |
|---|---|
| `email` | `email` |
| `phone`, `mobile`, `cell` | `phone_number` |
| `firstName` / `first` + `name` | `first_name` |
| `lastName` / `last` + `name` | `last_name` |
| `city` | `city` |
| `country` | `country` |
| `street` | `street_name` |
| `address` | `address` |
| `zip`, `postal` | `postal_code` |
| `uuid`, `guid` | `uuid` |
| `url`, `uri` | `url` |

Finite set for v1. Extend later; no open-ended "etc."

## 6. Locked open-question decisions

| # | question | decision |
|---|---|---|
| 1 | scope | OpenAPI **and** DDL (DDL pulled forward from v2) |
| 2 | unknown type | warn + `char[1..50]` fallback, flagged with an inline review comment. Never hard-fail, never silent skip |
| 3 | granularity | one YAML file per schema object. Matches `{entity}.yaml` + `object[...]` auto-load |
| 4 | `$ref` | emit `object[ref_name]` + separate file, recurse. Lean on existing circular-ref detection |
| 5 | required/optional | ignore in v1 (no nullable concept in type system yet). Follow-up if needed |

## 7. Output safety

- Existing target file → **skip + warn** unless `--force`.
- Filename = snake_case of schema object name + `.yaml` (e.g. `LineItem` → `line_item.yaml`),
  matching `object[line_item]` lookup.
- Print summary: files written, skipped, count of fields flagged for review.

## 7a. Review comments (inferred-reason taxonomy)

Every mapping is tagged with a reason; only the two **guesses** get an inline `# ...` comment on the
field's `datatype:` line. The **certainties** stay silent so a real DB isn't buried in noise.

| reason | example | comment |
|---|---|---|
| `DECLARED` | `format:email`, `enum`, bounded `int`, `VARCHAR(40)`, `boolean`, `DATE` | none |
| `DEFAULT_RANGE` | `BIGINT`→`int[1..999999]`, `TEXT`→`char[1..500]` (inherent SQL/OpenAPI gap) | none |
| `NAME_HINT` | `city`→`city`, custom `beer_style`→`beer_style` (guessed from the name) | `# guessed from column name — verify` |
| `UNKNOWN_TYPE` | `JSONB`→`char[1..50]` (type not recognized) | `# unrecognized source type, defaulted — verify` |

Comments are appended after Jackson serialization (the body is serialized normally; only the comment
text is added, matched positionally so a repeated datatype value is never mis-tagged). SeedStream's
YAML parser ignores `#` comments, so annotated files round-trip cleanly.

## 7b. Datafaker types & custom types

- Emitted datafaker datatypes are **bare registry keys** (`email`, `city`, `uuid`), the only form
  `core/type/TypeParser` accepts. There is no `datafaker[...]` bracket syntax in the engine.
- Every candidate key is validated against `DatafakerRegistry` before emission
  (`FakerTypes.canonical`); an unregistered key falls back to a primitive, so the inspector can
  never emit an unparseable structure.
- **Custom types**: Datafaker exposes far more providers than the built-ins. Define extras in a
  YAML config mapping a key to a Datafaker method path, and pass it to both commands:

  ```yaml
  # config/datafaker-types.example.yaml
  types:
    beer_style: beer.style      # faker.beer().style()
    pokemon:    pokemon.name
  aliases:
    beerstyle:  beer_style
  ```
  ```bash
  ./seedstream inspect schema.sql --faker-types config/datafaker-types.example.yaml
  ./seedstream execute --job ...  --faker-types config/datafaker-types.example.yaml
  ```
  - The chain is resolved + validated at load (`DatafakerRegistry.registerExpression`), so a typo
    fails fast.
  - The inspector auto-targets a custom type when a **column name matches its key**
    (e.g. column `beer_style` → `beer_style`).
  - The same config must be passed to `execute` so the referenced types resolve at generation time.
  - Only **no-arg** method chains resolve from config (each dot-segment is a zero-arg call, result coerced to `String`). Providers needing arguments — `number().numberBetween(...)`, `options().option(...)`, `regexify(...)`, bounded `date()` calls — or non-String formatting still require a Java lambda via `DatafakerRegistry.register`.

## 7c. DDL specifics

- Parser: JSQLParser. One structure per `CREATE TABLE`; type table per `DdlTypeMapper`.
- Type names arrive inline (e.g. `VARCHAR (255)`); base name + args are parsed off that string.
- **Type folding.** Before mapping, each SQL type name is upper-cased, its internal whitespace is
  collapsed, and it is resolved through a synonym table (`DdlTypeMapper.SYNONYMS`). This lets
  multi-word ANSI forms and vendor aliases land on the same branch as their common synonym instead
  of the unknown-type default:
  - `CHARACTER VARYING`, `CHAR VARYING`, `VARCHAR2`, `NATIONAL CHARACTER VARYING`, `STRING` → `VARCHAR`
  - `DOUBLE PRECISION`, `FLOAT4/8`, `BINARY_FLOAT/DOUBLE`, `MONEY`, `NUMERIC`, `NUMBER`, `DEC` → `DECIMAL`
  - `TIMESTAMP WITH[OUT] TIME ZONE`, `TIMESTAMPTZ`, `DATETIME`, `SMALLDATETIME` → `TIMESTAMP`
  - `SERIAL`/`BIGSERIAL`/`SMALLSERIAL`, `INT2/4/8` → `INT`
  - `CLOB`, `NCLOB`, `TINY/MEDIUM/LONGTEXT`, `NTEXT`, `LONG VARCHAR` → `TEXT`
  - native `UUID` / `UNIQUEIDENTIFIER` → the `uuid` datafaker key (or `char[36..36]` fallback)
  Genuinely opaque types (`JSON`, `JSONB`, `BYTEA`, `GEOMETRY`, …) still fall back to `char[1..50]`
  flagged `UNKNOWN_TYPE`.
- Foreign keys → `ref[table.column, 1..count]`: table-level `FOREIGN KEY` constraints (reliable)
  and inline
  column `REFERENCES table(col)` (best-effort token scan). The `1..count` ID-pool range is appended
  by default — a bare `ref[t.col]` is rejected by the engine — so the pool scales with `--count` and
  the emitted YAML runs at any size. Optionally inverted into nested documents — see §9.
- String columns run through the same `NameHints` before falling back to `char[1..n]`.

## 8. Out of scope (with rationale)

These stay out of v1 — but the reason matters, because two of them are *not* inspector gaps at all:

- **`alias` auto-emission.** The structure model has an `alias` slot, but the generation engine
  **drops it** today (`ExecuteCommand` builds `Map<String, DataType>` from `datatype` only, never
  `alias`), so an emitted alias would be silently ignored at generation time. Auto-emitting one is
  pointless until the engine honors `alias` end-to-end. **Blocked on an engine change, not an
  inspector feature.**
- **`geolocation` / locale.** Locale is *not derivable from a schema* — an OpenAPI spec or DDL never
  says "this is Italian data." It is a generation-time choice the user makes per job/structure, so
  the inspector correctly cannot and does not infer it. (The earlier note about "Datafaker hints
  with no locale" was misleading and has been removed.) **Out of scope by nature, not a gap.**
- **nullable / required mapping.** Inapplicable. The structure YAML is a *recipe*: every field is
  always generated by Datafaker and is never null, so a `NOT NULL` source constraint is already
  satisfied by construction and an optional/`required:false` field has no meaningful recipe
  representation (the inspector always emits the field). **Nothing to map.**
- **Primary-key / uniqueness handling.** A PK is `NOT NULL` + `UNIQUE`; the not-null half is free
  (above), but the engine has **no uniqueness guarantee** — an `int[1..999999]` PK can emit
  duplicate ids. Enforcing uniqueness is a *generation-engine* policy (a new `unique` concept in the
  type system), not a recipe-shape the inspector can express. **Tracked engine follow-up.**

Now implemented (previously listed here):
- DDL multi-word / vendor-specific types — see §7c type folding.
- FK-inversion nesting — see §9.

## 9. FK-inversion nesting (opt-in)

Foreign keys default to flat `ref[parent.column, 1..count]` (child → parent), matching the DB. With `--nest`
the DDL inspector inverts `1:n` / `1:1` FKs into the direction SeedStream embeds — a parent carrying
its children as nested documents:

| flag | behavior |
|---|---|
| (absent) | `none` — every FK stays a flat `ref[]` (default) |
| `--nest` / `--nest=auto` | invert FKs where safe; keep flat on cycles / M:N / shared children (warn) |
| `--nest=all` | aggressive — nest every invertible FK, **error** on a true cycle instead of falling back |
| `--nest-default-count <min..max>` | array multiplicity when the schema gives no hint (default `1..10`) |

```bash
./seedstream inspect schema.sql --nest --output config/structures/
```

- **1:1** (child FK column is `UNIQUE`/PK) → `object[child]`; **1:n** → `array[object[child], min..max]`.
- Nested field name = pluralized child table (`invoice → invoices`, `invoice_item → invoice_items`);
  a collision with an existing parent column appends `_set` (warn).
- A child is embedded into **at most one** parent (first by declaration order); other parents keep a
  flat `ref[]` (warn). The embedded child's FK column to that parent is dropped (redundant once
  nested).
- **Never nested** (kept flat + warned): self-references and cycles (broken at the back-edge),
  composite/multi-column FKs, and pure M:N junction tables.
- **Every structure is still written to disk**, even when embedded: `object[child]` auto-loads
  `child.yaml`, so suppressing the file would break the reference. "Demotion" here is a warning, not
  file suppression — a deliberate deviation from the original plan's §5.6 to keep refs resolvable.
- OpenAPI already nests natively (`$ref` → `object`, `array` of `$ref` → `array[object[…]]`), so
  `--nest` is **ignored** for OpenAPI input (a warning is logged). DDL is the sole target.

#### Algorithm (DDL)

1. **Parse** all `CREATE TABLE` → structures + collect FK edges `edge(childTable, childCol, parentTable, parentCol)`
   (gathered by `tableForeignKeys` / `inlineForeignKey` in `DdlInspector`).
2. **Build a directed graph** parent → child, inverting each FK edge.
3. **Classify each relation**:
   - `1:1` if the child FK column is also `UNIQUE`/PK → emit `object[child]`.
   - `1:n` otherwise → emit `array[object[child], <count>]`.
   - **M:N** if the table is a pure junction (PK = exactly its two FK columns, no other non-FK payload
     columns) → do **not** nest; keep both refs flat; warn.
4. **Cycle detection** (DFS / Tarjan SCC). Any back-edge → leave that FK flat `ref[]`, warn (`auto`) or
   error (`all`).
5. **Embedding decision**: a child is embedded into **at most one** parent (first in declaration order —
   deterministic). Additional parents referencing the same child keep a flat `ref[]`, so generated data is
   never duplicated or contradictory. Warn on the demoted edges.
6. **Emit**: the parent gains the nested field; the child's FK column to that parent is dropped from the
   embedded copy (redundant once nested) but kept in any surviving top-level copy.

Note the shipped behaviour deliberately departs from the original design on one point: **every structure is
still written to disk**, even when embedded, because `object[child]` auto-loads `child.yaml` and suppressing
the file would break the reference. "Demotion" is a warning, never file suppression.

#### Edge cases

| Case | Handling |
|---|---|
| Self-referencing FK (`employee.manager_id → employee`) | cycle → stay flat `ref[]`, warn |
| Cycle A→B→A | break at the back-edge, flat `ref[]` on that edge; warn (`auto`) / error (`all`) |
| Composite FK (multi-column) | SeedStream `ref`/nesting is single-field → stay flat, warn |
| M:N junction table | keep both refs flat, do not nest, warn |
| Child referenced by 2+ parents | embed into the first parent only; others flat `ref[]`, warn |
| 1:1 (`UNIQUE`/PK FK) | `object[child]` instead of an array |
| Orphan table (no FK either way) | unchanged, top-level |

All warnings flow through the existing `Inspection.warnings` channel and the CLI summary.

**Implementation:** `NestingOptions`, `NestingPlanner`, `Pluralizer`; entry point
`DdlInspector.inspect(Path, NestingOptions)`.
