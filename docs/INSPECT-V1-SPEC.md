# seedstream inspect — v1 Spec

Status: implemented. Scope: OpenAPI, standalone JSON Schema, SQL DDL **and** Protobuf → SeedStream
structure YAML. Supersedes the open questions from the design phase with locked decisions.

## 1. Scope

- **In**: OpenAPI 3.x specs (`.yaml` / `.json`, object schemas under `components.schemas`),
  standalone **JSON Schema** (Draft 7 / 2020-12 — root object schema + entries under
  `$defs`/`definitions`; see §10),
  SQL DDL (`.sql`, `CREATE TABLE` statements incl. foreign keys → `ref[...]`), and compiled
  Protobuf **FileDescriptorSet**s (`.desc` / `.binpb` / `.protoset` from `protoc --descriptor_set_out`
  or `buf build -o`; one structure per message, nested messages → `object[...]`, `repeated` → `array[...]`,
  `enum` → `enum[...]`). Protobuf carries no value bounds, so scalars use default ranges; `bytes`,
  `map<k,v>`, well-known dynamic types (`Any`/`Struct`/…) and `oneof` members are flagged for review.
  Parsing `.proto` source directly is not supported — pre-compile to a descriptor set.
- **Out**: `alias` emission, nullable/required hints, geolocation/locale, primary-key handling.
- One `inspect` subcommand on the existing `datagenerator` CLI. No separate binary.
- Format auto-detected from extension/content (`.sql` → DDL, `.desc`/`.binpb`/`.protoset` →
  Protobuf, `.schema.json` → JSON Schema; for plain `.yaml`/`.yml`/`.json` the root keys
  disambiguate — `openapi`/`swagger` → OpenAPI, else `$schema`/`$defs`/`definitions` → JSON Schema,
  else OpenAPI); `--format openapi|jsonschema|ddl|protobuf` overrides.

```bash
./seedstream inspect api.yaml            --output config/structures/
./seedstream inspect payload.schema.json --output config/structures/   # standalone JSON Schema
./seedstream inspect schema.sql          --output config/structures/ --force
./seedstream inspect schema.desc         --output config/structures/   # protoc/buf descriptor set
./seedstream inspect https://api.example.com/openapi.yaml --output config/structures/   # OpenAPI / JSON Schema over HTTP(S)
```

The input may also be an `http(s)://` URL (OpenAPI / JSON Schema); it is downloaded to a temp file and fed to the normal pipeline, so format auto-detection works the same. The fetch is hardened: TLS verified, redirects not followed, a 10 MiB size cap and request timeout, and an **SSRF guard** that refuses loopback / link-local / private-range hosts unless `--allow-private-urls` is given (needed for a homelab/internal schema URL). Protected specs authenticate with `--auth bearer|basic|api_key` plus the matching credential flags (mirrors the seed `remote` auth surface). Credentials are never logged.

Flags:
| flag | default | meaning |
|---|---|---|
| `--output <dir>` | `config/structures/` | where YAML files are written |
| `--force` | off | overwrite existing files (else skip + warn — never silent clobber) |
| `--format openapi\|jsonschema\|ddl\|protobuf` | auto | input format override; supports all four implemented inspectors |
| `--faker-types <file>` | unset | optional custom Datafaker types config loaded before inspection |
| `--best-effort` | off | DDL only: emit the parseable subset and warn on unparseable `CREATE TABLE`s instead of aborting |
| `--nest[=auto\|all\|none]` | `none` | DDL only: invert `1:n`/`1:1` FKs into nested `array[object[child]]`/`object[child]` — see §9 |
| `--nest-default-count <min..max>` | `1..10` | DDL only: multiplicity for synthesized nested arrays — see §9 |
| `--allow-private-urls` | off | URL input only: permit loopback / link-local / private-range hosts (SSRF guard otherwise refuses them) |
| `--auth bearer\|basic\|api_key` | unset | URL input only: auth scheme for a protected spec |
| `--token <t>` | unset | bearer token (with `--auth bearer`) |
| `--username <u>` / `--password <p>` | unset | basic credentials (with `--auth basic`) |
| `--header-name <h>` / `--header-value <v>` | unset | custom header name/value (with `--auth api_key`) |

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
| `string` + `format: uuid` | `uuid` — or `char[min..max]` + review comment when `maxLength < 36` or `minLength > 36` (a UUID is always 36 chars) |
| `string` + `format: email` + `maxLength < 64` or `minLength > 6` | `char[min..max]` + review comment (Datafaker emails: longest of 1M samples was 36 chars; 64 leaves headroom for other locales; see #440 for length caps) |
| `string` + `enum:[A,B,C]` | `enum[A,B,C]` (lengths ignored) |
| `string` + `minLength: m` and/or `maxLength: n` | `char[m..n]`; a missing side defaults to `1` / `max(m, 50)`; `minLength: 0` → `char[0..n]` |
| `string` + `minLength > maxLength` (schema error) | `char[1..maxLength]` (`char[0..0]` when `maxLength` is 0) + review comment + a warning naming the field |
| `string` (no format/len) | name-hint → else `char[1..50]`; with `maxLength` the declared length wins over the hint |
| `integer` + min/max | `int[min..max]` |
| `integer` (no bounds) | `int[<intMin>..<intMax>]` |
| `number` + min/max | `decimal[min..max]` |
| `number` (no bounds) | `decimal[<decMin>..<decMax>]` |
| `boolean` | `boolean` |
| `array` of T, `minItems`/`maxItems` | `array[<T>, min..max]` |
| `$ref: #/.../Foo` | `object[foo]` + emit `foo.yaml` (recurse) |
| `$ref: Foo.yaml` (bare file) | `object[foo]` (base name, extension dropped) |

Notes:
- `type: [T, "null"]` (OpenAPI 3.1 / JSON Schema nullability) maps as `T`.
- `enum` applies to `integer`/`number` too (before bounds).
- `exclusiveMinimum`/`exclusiveMaximum` (numeric, or the OpenAPI 3.0 boolean modifiers) move the
  bound inward: by 1 for `int`, by one unit at the bound's scale (at least `0.01`) for `decimal`.
- With only one side declared, the other takes its default unless that would invert the range; then
  it keeps the default span from the declared side (`minimum: 2000000` → `int[2000000..2999998]`).
  Arrays (`minItems`/`maxItems`) follow the same rule.
- Decimal bounds are written in plain notation (`0.0000001`, never `1.0E-7`).
- Schema names that snake-case to the same structure name (`LineItem`, `line_item`) stop the
  inspection with an error listing the clash (§7: never silent clobber).

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

DDL columns with a narrower declared capacity get the default capped to fit (#377), so generated
values always insert: `SMALLINT`/`INT2`/`SMALLSERIAL` → `int[1..32767]`, `TINYINT` → `int[1..127]`
(fits signed MySQL and unsigned SQL Server), and fixed-point `DECIMAL`/`NUMERIC`/`NUMBER(p,s)` →
`decimal[0..10^(p-s) − 10^-s]` at scale `s` (e.g. `DECIMAL(4,2)` → `decimal[0.00..99.99]`) when
that is below `decMax`. Floating types (`FLOAT`, `REAL`, `MONEY`, …) keep the default.

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
| `REVIEW` | `NUMBER(5,-2)`→`int[...]`, MySQL `SET(...)`→`enum[...]`, an ENUM member that `enum[...]` cannot express, `format: uuid` that cannot fit `maxLength` | a specific note, e.g. `# uuid format cannot fit maxLength 20 — verify` |
| `SCHEMA_ERROR` | `minLength > maxLength` (the source schema contradicts itself) | a specific note, plus a warning naming the field |

Arrays carry their item's comment: `array[char[1..5], 1..10]` built from items with `minLength > maxLength`
keeps the `SCHEMA_ERROR` note and warning whether or not the array declares `minItems`/`maxItems`.

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
  - `TIMESTAMP WITH[OUT] TIME ZONE`, `TIMESTAMPTZ`, `DATETIME`, `SMALLDATETIME`, `DATETIME2`,
    `DATETIMEOFFSET` → `TIMESTAMP`
  - `BIT` / `BIT(1)` (SQL Server's 0/1 flag) → `boolean`; `BIT(n>1)` is a bit string and stays an
    unknown type
  - `SERIAL`/`BIGSERIAL`/`SMALLSERIAL`, `INT2/4/8` → `INT`
  - `CLOB`, `NCLOB`, `TINY/MEDIUM/LONGTEXT`, `NTEXT`, `LONG VARCHAR` → `TEXT`
  - native `UUID` / `UNIQUEIDENTIFIER` → the `uuid` datafaker key (or `char[36..36]` fallback)
  Genuinely opaque types (`JSON`, `JSONB`, `BYTEA`, `GEOMETRY`, …) still fall back to `char[1..50]`
  flagged `UNKNOWN_TYPE`. Oracle `RAW(n)`, which JSQLParser cannot parse, is rewritten to
  `VARBINARY(n)` by the preprocessor and lands on the same flagged fallback.
- **Type spellings.** Quoted type names (`"int"`, `` `int` ``, `[int]` — SQL Server's original
  scripts quote them) map exactly like the bare name. Oracle length semantics are ignored for the
  length: `VARCHAR2(15 BYTE)` / `CHAR(5 CHAR)` → `char[1..15]` / `char[1..5]`. A zero-length
  `VARCHAR(0)` / `CHAR(0)` → `char[0..0]` (empty string), never an inverted range.
- **MySQL types.** `ENUM('a','b')` → `enum[a,b]` (`''` and, for double-quoted members, `""` are
  unescaped; double-quoted members are rewritten to SQL literals before parsing). `SET('x','y')` →
  `enum[x,y]` with a review comment (one member per row). A member `enum[...]` cannot express — one
  containing `,` or `]`, an empty member, or one with leading/trailing whitespace (the engine trims
  members) — falls back to `char[1..longest]` with a review comment. `TINYINT(1)` (MySQL's boolean
  convention) → `boolean`; other `TINYINT` widths stay integers.
- **Exact numerics with scale 0 are integers.** `NUMBER(p)`, `NUMBER(p,0)`, `DECIMAL(p,0)`,
  `NUMERIC(p,0)` → `int[1..min(10^p − 1, 999999)]`, so they take part in key detection (`serial`,
  `unique`, `ref`) — this is what makes Oracle keys (`NUMBER(9,0)`) enforced. `NUMBER` /
  `NUMBER(*)` (no precision) stay decimal; `NUMBER(*,0)` → the default int range; a negative scale
  (`NUMBER(5,-2)`) → the default int range with a review comment. `NUMBER(*,0)` and negative scales
  are mapped correctly but JSQLParser 5.4 cannot parse them inside a `CREATE TABLE` yet.
  Decimals with scale > 2 whose capacity exceeds the default cap keep the default bounds rendered at
  the declared scale (`DECIMAL(19,4)` → `decimal[0.0000..9999.9900]`); scales 0–2 keep
  `decimal[0.0..9999.99]` (databases round on insert).
- **Script handling.** Runs of two or more empty lines (`pg_dump`, SSMS) are collapsed outside
  quotes, comments and dollar bodies (empty lines inside string literals are preserved). psql
  meta-command lines (`\connect`, `\restrict`, `\.` …) are dropped. A leading UTF-8 BOM is ignored.
  Statements are classified on their text with comments and literals removed:
  - no `CREATE … TABLE` left, a routine body (`CREATE FUNCTION|PROCEDURE|TRIGGER|PACKAGE|EVENT …`),
    or `GRANT|REVOKE|DENY …` → skipped silently;
  - a table without a column list — `CREATE TABLE … AS SELECT`, `… PARTITION OF …`, MySQL
    `… LIKE other`, PostgreSQL `(LIKE other)` → skipped with a warning naming the table;
  - anything else that still mentions `CREATE … TABLE` is parsed: it is modelled, or fails strict
    mode loudly (best-effort: skipped with a warning). A real table is never dropped silently.
- **Tables inside other statements.** A chunk starting with `CREATE SCHEMA`, `IF`, `BEGIN` or `DROP` is split into
  its statements first, so `CREATE SCHEMA s CREATE TABLE t (…)`, `IF … DROP TABLE x CREATE TABLE x (…)` with no `GO`
  or `;` between them, and `IF … BEGIN CREATE TABLE z (…) END` yield what the same statements standing alone yield.
  A routine (`CREATE|ALTER PROC|FUNCTION|TRIGGER …`, `CREATE TYPE … AS TABLE`) ends the split: it and everything after
  it stay one statement, skipped silently, since in T-SQL what follows belongs to its batch. A chunk that can't be
  split reaches the parser unchanged. `GRANT CREATE TABLE TO u` with a table glued after it is not split, and that
  table is lost.
- **SQL Server `IDENTITY`.** `IDENTITY`, `IDENTITY(seed, increment)` and `… NOT FOR REPLICATION`
  after a column's type mark the column as database-assigned (SQL Server rejects explicit values);
  it then follows the same rules as `GENERATED ALWAYS AS IDENTITY` (see the key mapping in §8).
  The word `identity` as a table, column, constraint or FK-target name is not affected.
- Key constraints ignore column sort direction: `PRIMARY KEY CLUSTERED ([id] ASC)` (SSMS's default
  scripting) is the same key as `PRIMARY KEY (id)`.
- **Constraints declared after the table.** All statements are read first, then applied: `ALTER TABLE
  [ONLY] … ADD [CONSTRAINT n] PRIMARY KEY | UNIQUE | FOREIGN KEY` (the `pg_dump`, SSMS, Oracle and MySQL
  export layouts, in any order relative to the table) yields exactly what the same constraint declared
  inline yields; `ALTER COLUMN … ADD GENERATED ALWAYS AS IDENTITY` counts as DB-assigned. A constraint
  declared both ways counts once. Other statements are ignored silently (`OWNER TO`, `CHECK CONSTRAINT`,
  triggers, `SET STATISTICS`, `CHECK` / `NOT NULL` for now). Structure changes (`ADD`/`DROP COLUMN`,
  `RENAME`, `ALTER COLUMN … TYPE`) are warned about and not applied. An `ALTER` that cannot be parsed, or
  that targets a table not in the script, is a warning and never aborts strict mode; so is a foreign key
  from an `ALTER` to a table not in the script (the column keeps its plain type).
- **`CREATE UNIQUE INDEX`** yields what the same inline `UNIQUE (…)` yields, wherever the statement sits in
  the script; `CLUSTERED`, `USING`, `INCLUDE`, `NULLS [NOT] DISTINCT`, sort direction and vendor storage clauses
  don't change the key. An index repeating a declared PRIMARY KEY or UNIQUE, in any column order, counts once.
  A partial index (`WHERE`) or one on an expression, a column prefix, or a column with `COLLATE` or an operator
  class declares no key and gets a warning; so does an index on an unknown table or column, or one that cannot
  be read. Neither aborts strict mode. An index on a parent does not apply to its `INHERITS` children.
- **`REFERENCES t` without a column list** resolves to `t`'s primary key, positionally for composite
  keys, wherever that key is declared. If `t` is unknown, has no primary key, or the column counts
  differ, a warning is logged and the column keeps its plain type (never a guessed `id`).
- **PostgreSQL `INHERITS`.** A child gets its parents' columns first (recursively, parents in clause
  order, in any order relative to the child), then its own; a same-named column appears once, at its
  first position, and a child that redeclares it wins on the type. Only columns are inherited, as in
  PostgreSQL: not PRIMARY KEY, UNIQUE, FOREIGN KEY or the identity property. Keys the child declares
  itself, inline or by `ALTER`, may use inherited columns. A parent not in the script or an inheritance
  cycle is a warning and the child keeps its own columns; a child left with no columns at all
  (`CREATE TABLE c () INHERITS (missing)`) is skipped with a warning. Neither aborts strict mode.
- Foreign keys → `ref[table.column, 1..count]`: table-level `FOREIGN KEY` constraints (reliable)
  and inline
  column `REFERENCES table(col)` (best-effort token scan). The `1..count` ID-pool range is appended
  by default — a bare `ref[t.col]` is rejected by the engine — so the pool scales with `--count` and
  the emitted YAML runs at any size. Optionally inverted into nested documents — see §9.
  The referenced **column** in the target is snake-cased (`REFERENCES [dbo].[Employees] ([EmployeeID])`
  → `ref[employees.employee_id, …]`) and a schema prefix on the referenced table is dropped, so
  PascalCase / UPPERCASE schemas produce loadable structures; the structure's own field keys keep the
  declared case (they are the database column names). If two columns of a referenced table
  snake-case to the same name (`OrderId`, `ORDER_ID`), a warning names both.
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
  (above). The engine now provides the missing piece: `serial` for database auto-increment columns,
  `unique[1..count]` for collision-free keys, and `ref[…, unique]` for 1:1 FKs (#212). The inspector
  emits these types as follows:

  **DDL key mapping:**
  - Single-column integer PK (e.g. `id BIGINT PRIMARY KEY`) → `serial` (independent of seed, ordered by record index).
  - `SERIAL` / `BIGSERIAL` / `SMALLSERIAL` column not in any key → `serial` (same semantics).
  - Single-column `GENERATED ALWAYS AS IDENTITY` PK → **omitted** from the structure (the database rejects a client-supplied value, so the DB assigns it); a warning is logged. If another table's FK references it, the column is instead **kept** as `serial` with a warning, because omitting it would dangle that reference — the referenced case needs DB-assigned ordered generation (#400). `GENERATED BY DEFAULT AS IDENTITY` accepts a client value and stays `serial`.
  - SQL Server `IDENTITY` single-column PK → same rule as `GENERATED ALWAYS` (omitted, or kept as `serial` with the #400 warning when referenced). An `IDENTITY` column outside the PK (SQL Server only) is omitted when nothing references it and kept otherwise, with a warning either way; an `IDENTITY` member of a composite PK is kept with a warning. Warnings say "kept as serial" only when the emitted type actually is `serial`.
  - Single-column integer `UNIQUE` constraint → `unique[1..count]` (collision-free, **not** prefix-stable when --count changes).
  - FK column that is a single-column PK/UNIQUE (1:1 relationship) → `ref[parent.column, 1..count, unique]` (documents referential intent).
  - Composite PK or composite `UNIQUE` constraint → members tagged `unique[pk, 1..count]` / `unique[uq1, 1..count]` / etc. (pk for primary key, uq1/uq2/… for UNIQUE constraints in declaration order; the group name ensures the tuple is unique).
  - FK members in a composite PK/UNIQUE → `ref[parent.col, 1..count, unique=pk]` / `ref[parent.col, 1..count, unique=uq1]` (group name matches the composite constraint).
  - A column in multiple keys (e.g. a PK that is also part of a UNIQUE) → PK wins, then first UNIQUE; a warning is logged.
  - Key containing any non-integer, non-FK column (varchar, decimal, date, etc.) → types unchanged, field comment `# not enforced — values may collide`, and a warning.
  - Single UUID key → unchanged, no special handling.
  - Tables folded as nested children (`object[child]`, `array[object[child], ...]`) → PK and UNIQUE handling unchanged within the nested structure.
  - Composite groups of k members on a `1..count` range have count^k combinations; if that overflows a long (e.g. 3+ members on >~2M rows), the job fails at startup with a size-exceeded error.

  **Limitation**: `ref[…]` table and column names cannot contain digits (TypeParser pattern constraint, pre-existing).

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

## 10. JSON Schema specifics

Standalone JSON Schema (Draft 7 / 2020-12) is mapped by the same `SchemaTypeMapper` the OpenAPI
inspector uses — the per-property vocabulary (`type`, `format`, `enum`, `minimum`/`maximum`,
`maxLength`, `minItems`/`maxItems`, `$ref`, `items`) is identical (see §3). Only the **entry point**
differs.

**Entry points → structures:**
- The **root** object schema (any schema with `properties`) → one structure. Its name is taken from
  `title` → `$id` (last path segment) → the file-name stem, then run through the same safe-name
  guard as the emitted YAML file (`Names.requireSafeStructureName`).
- Every entry under **`$defs`** and **`definitions`** (both supported) → one structure each, named
  `snake_case(key)`.
- Local `$ref` (`#/$defs/Foo`, `#/definitions/Foo`) → `object[foo]`, resolved by last path segment.
- No root object schema and no `$defs`/`definitions` → error (`not a recognizable JSON Schema`).

### Composition & conditionals — merged, because we generate data

`inspect` produces **data**, not a validator, so it emits every field that could appear rather than
dropping constructs it can't fully honour. At the **structure level** (keywords sitting beside
`properties`) the subschemas of `allOf` / `oneOf` / `anyOf` / `if` / `then` / `else` /
`dependentSchemas` — and any local `$ref` among them — are **merged into one record** (union,
first-declared field wins). Local `$ref`s are resolved against `$defs`/`definitions`.

- **`allOf`** is an exact merge (the "extends" idiom) — no comment, no warning.
- The **lossy** branches (`oneOf`/`anyOf`/`if`/`then`/`else`/`dependentSchemas`) still get merged so
  their fields are emitted, but the conditional/polymorphic relationship is dropped. Each such field
  gets an inline `# review:` comment, and the structure gets a warning: the union record **may not
  validate** against the source (mutually-exclusive variants co-populated, one branch's bounds
  chosen on conflict). `not` / `patternProperties` / `additionalProperties: true` /
  `dependentRequired` add nothing mergeable but still warn.

Example — a root `if`/`then`/`else` that tightens `membershipNumber` per branch: both `isMember` and
`membershipNumber` are emitted and flagged, with a structure warning that the conditional isn't
enforced.

*Per-property* composition (the same keywords on a single field, which occupies one datatype slot and
so **can't** be unioned) is still flagged with a `# review:` comment + placeholder `char[1..50]`:
`oneOf`/`anyOf`/`allOf`, `if`/`then`/`else`, `const`, `not`, tuple-form `items`, `patternProperties`,
`additionalProperties: true`, external `$ref`, recursive `$ref`.

### Regex (`pattern`) → suggested faker-type + rerun

SeedStream has no inline regex datatype; regex generation is a **custom Datafaker type** registered
via `--faker-types` (`name: "regex:<pattern>"` → `DatafakerRegistry.registerRegex`). So on a
`string` + `pattern` field, `inspect`:

1. maps the field to `char[1..N]` and adds a `# review:` comment naming the pattern,
2. accumulates the pattern into a companion **`inspect-faker-types.yaml`** in the output dir, keyed
   by the (snake_case) field name,
3. on rerun with `--faker-types inspect-faker-types.yaml`, the field resolves by name-hint to the
   registered regex generator (datatype becomes the type name), producing matching values.

The companion file **never clobbers**: an existing file is left untouched unless `--force`, and the
target is refused outright if it is the very `--faker-types` input the run was given (the suggestions
are logged instead, so nothing is lost).

**Positioning.** `inspect` is a **bootstrap + sync** for large / nested / constraint-rich schemas.
For a small flat structure (≤ ~10 primitive fields), hand-writing the YAML stays the blessed path —
the inspect → review → fix loop costs more than just typing it.

**Implementation:** `JsonSchemaInspector`, `JsonSchemaGaps`, `FakerTypeSuggestionsWriter`; shared
`SchemaTypeMapper` (formerly `OpenApiTypeMapper`).
