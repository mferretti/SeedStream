# datagenerator inspect — v1 Spec

Status: implemented. Scope: OpenAPI **and** SQL DDL → SeedStream structure YAML.
Supersedes the open questions in [INSPECT.md](INSPECT.md) with locked decisions.

## 1. Scope

- **In**: OpenAPI 3.x specs (`.yaml` / `.json`, object schemas under `components.schemas`) and
  SQL DDL (`.sql`, `CREATE TABLE` statements incl. foreign keys → `ref[...]`).
- **Out**: `alias` emission, nullable/required hints, geolocation/locale, primary-key handling.
- One `inspect` subcommand on the existing `datagenerator` CLI. No separate binary.
- Format auto-detected from extension (`.sql` → DDL, `.yaml`/`.yml`/`.json` → OpenAPI);
  `--format openapi|ddl` overrides.

```bash
datagenerator inspect api.yaml   --output config/structures/
datagenerator inspect schema.sql --output config/structures/ --force
```

Flags:
| flag | default | meaning |
|---|---|---|
| `--output <dir>` | `config/structures/` | where YAML files are written |
| `--force` | off | overwrite existing files (else skip + warn — never silent clobber) |
| `--format openapi` | auto | reserved; only `openapi` valid in v1 |

## 2. Module

New Gradle module `inspector`, dependency edge `cli → inspector → schema → core`.
Parser lib (`swagger-parser` / `jackson`) isolated in `inspector`, never leaks into the
generation hot path. **Module creation requires confirmation** (per CLAUDE.md) — gate before build.

## 3. Type mapping

Resolution order per property:
1. explicit `format` (table below)
2. `enum` present → `enum[...]`
3. numeric bounds present → bounded `int`/`decimal`
4. name-hint heuristic (§5)
5. type-default fallback (§4)

| OpenAPI | SeedStream |
|---|---|
| `string` + `format: email` | `datafaker[internet.emailAddress]` |
| `string` + `format: date` | `date[<dateMin>..<dateMax>]` |
| `string` + `format: date-time` | `timestamp[now-<window>..now]` |
| `string` + `format: uuid` | `datafaker[internet.uuid]` |
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

No bound in source → use these documented defaults, and emit a `# inferred` YAML comment so
the user sees it was a guess:

| placeholder | value |
|---|---|
| `intMin..intMax` | `1..999999` |
| `decMin..decMax` | `0.0..9999.99` |
| `dateMin..dateMax` | `2020-01-01..2030-12-31` |
| `date-time window` | `now-1y..now` |
| default string | `char[1..50]` |
| default array bounds | `1..10` |

## 5. Name-hint heuristics

- Applied **only** when no `format` and no `enum`.
- Match on **word/token boundaries**, not raw substring. Split field name on
  camelCase / `snake_case` / `-` into tokens; match whole tokens.
  → `email` matches `email`, `userEmail`; does **not** match `email_verified_at`.
- First matching rule wins; rule order is the table order.

| token(s) | SeedStream |
|---|---|
| `email` | `datafaker[internet.emailAddress]` |
| `phone`, `mobile`, `cell` | `datafaker[phoneNumber.cellPhone]` |
| `firstName` / `first` + `name` | `datafaker[name.firstName]` |
| `lastName` / `last` + `name` | `datafaker[name.lastName]` |
| `city` | `datafaker[address.city]` |
| `country` | `datafaker[address.country]` |
| `street`, `address` | `datafaker[address.streetAddress]` |
| `zip`, `postal` | `datafaker[address.zipCode]` |
| `uuid`, `guid` | `datafaker[internet.uuid]` |
| `url`, `uri` | `datafaker[internet.url]` |

Finite set for v1. Extend later; no open-ended "etc."

## 6. Locked open-question decisions

| # | question | decision |
|---|---|---|
| 1 | scope | OpenAPI **and** DDL (DDL pulled forward from v2) |
| 2 | unknown type | warn + `char[1..50]` fallback, flagged inferred in the CLI summary. Never hard-fail, never silent skip |
| 3 | granularity | one YAML file per schema object. Matches `{entity}.yaml` + `object[...]` auto-load |
| 4 | `$ref` | emit `object[ref_name]` + separate file, recurse. Lean on existing circular-ref detection |
| 5 | required/optional | ignore in v1 (no nullable concept in type system yet). Follow-up if needed |

## 7. Output safety

- Existing target file → **skip + warn** unless `--force`.
- Filename = snake_case of schema object name + `.yaml` (e.g. `LineItem` → `line_item.yaml`),
  matching `object[line_item]` lookup.
- Print summary: files written, skipped, inferred-field count.

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
  datagenerator inspect schema.sql --faker-types config/datafaker-types.example.yaml
  datagenerator execute --job ...  --faker-types config/datafaker-types.example.yaml
  ```
  - The chain is resolved + validated at load (`DatafakerRegistry.registerExpression`), so a typo
    fails fast.
  - The inspector auto-targets a custom type when a **column name matches its key**
    (e.g. column `beer_style` → `beer_style`).
  - The same config must be passed to `execute` so the referenced types resolve at generation time.

## 7a. DDL specifics

- Parser: JSQLParser. One structure per `CREATE TABLE`; type table per [INSPECT.md](INSPECT.md).
- Type names arrive inline (e.g. `VARCHAR (255)`); base name + args are parsed off that string.
- Foreign keys → `ref[table.column]`: table-level `FOREIGN KEY` constraints (reliable) and inline
  column `REFERENCES table(col)` (best-effort token scan).
- String columns run through the same `NameHints` before falling back to `char[1..n]`.

## 8. Out of scope (tracked follow-ups)

- Inline `# inferred` YAML comments (currently surfaced in the CLI summary, not in the file).
- `alias` auto-emission when source name ≠ idiomatic.
- `geolocation`/locale selection (emits Datafaker hints with no locale; default applies).
- Primary-key / uniqueness handling; nullable/required mapping.
- DDL: `CHARACTER VARYING` / multi-word and vendor-specific types (fall back to `char[1..50]`).
