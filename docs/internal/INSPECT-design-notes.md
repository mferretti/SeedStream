# datagenerator inspect — Design Notes

> **⚠️ FROZEN ARCHIVE — not maintained.**
> Original design exploration for the `inspect` subcommand, kept for provenance (the "why" and
> discarded alternatives). **Superseded by** [`../INSPECT-V1-SPEC.md`](../INSPECT-V1-SPEC.md) (the
> single source of truth for implemented behavior) and the test suite. Not kept in sync with the
> code — do not rely on it as current.

## Problem

Writing YAML structure configs by hand is the main friction point when adopting SeedStream on
an existing system. A user with a live database schema or an OpenAPI spec has to manually translate
every table/entity into a `config/structures/*.yaml` file. This is tedious, error-prone, and
discourages adoption.

## Proposed feature: `datagenerator inspect`

A new `inspect` subcommand that reads an existing schema (DDL or OpenAPI) and emits ready-to-use
SeedStream structure YAML files.

```bash
# auto-detect from file extension
datagenerator inspect api.yaml --output config/structures/
datagenerator inspect schema.sql --output config/structures/

# explicit format override for ambiguous files
datagenerator inspect spec.yaml --format ddl --output config/structures/
```

Detection rules:
- `.sql` → DDL
- `.yaml` / `.json` → OpenAPI (auto-detected from content if ambiguous)
- `--format openapi|ddl` overrides detection

## Module design

New Gradle module `inspector`. Depends only on `schema` (to know the YAML model) and its own
parser libraries. Parser libs are isolated here and do not leak into the runtime generation chain.

```
cli → inspector → schema → core
cli → destinations → formats → generators → schema → core   (unchanged)
```

The `cli` module registers `InspectCommand` as a subcommand of the top-level `datagenerator`
command. No separate binary.

## OpenAPI mapping (priority 1)

OpenAPI provides rich semantic information that maps cleanly to SeedStream types:

| OpenAPI | SeedStream type |
|---|---|
| `type: string, format: email` | `email` |
| `type: string, format: date` | `date[2020-01-01..2030-12-31]` |
| `type: string, format: date-time` | `timestamp[now-1y..now]` |
| `type: string, format: uuid` | `uuid` |
| `type: string, enum: [A, B, C]` | `enum[A,B,C]` |
| `type: integer, minimum: 1, maximum: 100` | `int[1..100]` |
| `type: number` | `decimal[0.0..1000.0]` |
| `type: boolean` | `boolean` |
| `type: string` (no format) | `char[1..100]` (with name-hint heuristics) |

Name-hint heuristics for unformatted strings (applied when no `format` is present):
- field name contains `email` → `email`
- field name contains `phone` → `phone_number`
- field name matches `first_name`, `firstName` → `first_name`
- field name matches `last_name`, `lastName` → `last_name`
- field name matches `city` → `city`
- etc.

## DDL mapping (priority 2)

SQL DDL provides less semantic information. Type mapping is mechanical; semantic hints come from
column names.

| SQL type | SeedStream type |
|---|---|
| `BOOLEAN` | `boolean` |
| `DATE` | `date[2020-01-01..2030-12-31]` |
| `TIMESTAMP` / `DATETIME` | `timestamp[now-1y..now]` |
| `INT` / `BIGINT` / `SMALLINT` | `int[1..999999]` |
| `DECIMAL(p,s)` / `NUMERIC` | `decimal[0.0..9999.99]` |
| `VARCHAR(n)` / `CHAR(n)` | `char[1..n]` |
| `TEXT` | `char[1..500]` |

Same name-hint heuristics as OpenAPI to infer semantic type from column names.

## Open questions

1. **Scope for v1**: Start with OpenAPI only (richer, cleaner) or both in parallel?
2. **Unknown types**: fail with error, emit warning + `char[1..50]` fallback, or skip field?
3. **Output granularity**: one YAML file per table/schema object, or merged into one file?
4. **Nested objects / `$ref`**: resolve and inline, or emit `object[ref_name]` with separate file?
5. **Required vs optional fields**: map `required: false` to some nullable hint, or ignore?

## Relationship to `seedstream-config` skill

The existing `/seedstream-config` Claude Code skill does interactive YAML generation through
conversation. `inspect` is the automated, non-interactive version for users who already have
a machine-readable schema. They are complementary.
