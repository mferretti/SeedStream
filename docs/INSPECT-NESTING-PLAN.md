# Plan: FK-inversion nesting for `inspect` (follow-up)

**Status:** proposed — not implemented. Tracking issue: [#87](https://github.com/mferretti/SeedStream/issues/87).
**Owner:** unassigned.
**Depends on:** current `inspect` v1 (DDL + OpenAPI inspectors, `ref[]` mapping).

## 1. Problem

The v1 inspector maps every foreign key to a **flat** scalar reference
(`child.parent_id → ref[parent.column]`). Direction is child → parent, matching the DB.

SeedStream's nesting goes the **other way**: a parent *embeds* its children via
`array[object[child], min..max]` (1:n) or `object[child]` (1:1). So for the classic
`customer -(1:n)-> invoice -(1:n)-> invoice_item` the inspector emits three flat structures
and never an `invoice` that embeds `line_items: array[object[invoice_item], 1..N]`.

This is correct for relational/per-table generation, but users who want a single embedded
document (one invoice JSON carrying its line items) must hand-edit. This plan adds **opt-in**
FK-inversion so the inspector can synthesize the nested form.

## 2. Goal

Add an opt-in mode that inverts `1:n` (and `1:1`) foreign keys into nested
`array[object[child], min..max]` / `object[child]` fields on the parent structure, while
preserving the existing flat `ref[]` behavior as the default.

Non-goal: changing default behavior. Flat `ref[]` stays the default; nesting is requested
explicitly.

## 3. Scope

In scope:
- DDL inspector (JSQLParser) — FK metadata is explicit and reliable.
- OpenAPI inspector — only where `$ref` / `x-` relationship hints make the parent→child link
  unambiguous (see §7). May land DDL-first, OpenAPI later.

Out of scope (this iteration): cross-file schemas, M:N auto-pivot into two nested arrays,
polymorphic/inheritance relations, configurable per-relationship multiplicity tuning beyond a
global default.

## 4. CLI surface

```
inspect <input> [--nest[=auto|all|none]] [--nest-default-count <min..max>] ...
```

- `--nest` (alias `--nest=auto`): invert FKs into nesting where safe; keep flat `ref[]` where
  not (cycles, M:N, multi-parent — see §6). Default when flag absent: `none` (today's behavior).
- `--nest=all`: aggressive — nest every invertible FK, error on a true cycle instead of falling
  back.
- `--nest-default-count <min..max>`: multiplicity for synthesized arrays when the source gives
  no hint (default `1..10`). 1:1 relations emit `object[child]` (no count).

## 5. Algorithm (DDL)

1. **Parse** all `CREATE TABLE` → structures + collect FK edges
   `edge(childTable, childCol, parentTable, parentCol)` (already gathered by
   `tableForeignKeys` / `inlineForeignKey` in `DdlInspector`).
2. **Build a directed graph** parent → child (invert each FK edge).
3. **Classify each relation**:
   - `1:1` if the child FK column is also UNIQUE/PK → emit `object[child]`.
   - `1:n` otherwise → emit `array[object[child], <count>]`.
   - **M:N** if a table is a pure junction (PK = exactly its two FK columns, no other
     non-FK payload columns) → do **not** nest; keep both flat refs; warn.
4. **Cycle detection** (DFS / Tarjan SCC). Any back-edge → leave that FK flat `ref[]`, emit a
   warning (`auto`) or error (`all`). Reuses the spirit of
   `core/.../exception/CircularReferenceException`.
5. **Embedding decision**: a child is embedded into **at most one** parent (the first parent in
   declaration order, deterministic). Additional parents referencing the same child keep flat
   `ref[]` to avoid duplicating/contradicting generated data. Warn on the demoted edges.
6. **Demotion**: a child embedded into a parent is **removed from the top-level output set**
   (it now only exists nested), unless it is *also* referenced flatly by another parent (then it
   stays top-level too). Controlled and logged so the file count is explained.
7. **Emit**: replace the parent's FK-less view — the parent gains the nested field; the child's
   `parent_id` column is dropped from the embedded copy (redundant once nested) but kept in any
   surviving top-level copy.

Output for the invoice example under `--nest`:

```yaml
# customer.yaml
name: customer
data:
  id: { datatype: int[1..999999] }
  name: { datatype: "name" }
  email: { datatype: "email" }
  invoices:
    datatype: array[object[invoice], 1..10]   # inverted customer<-invoice FK

# invoice.yaml  (still top-level: embedded under customer AND its own object reused)
name: invoice
data:
  id: { datatype: int[1..999999] }
  total: { datatype: decimal[0.0..9999.99] }
  issued: { datatype: date[2020-01-01..2030-12-31] }
  items:
    datatype: array[object[invoice_item], 1..10]  # inverted invoice<-invoice_item FK
  # customer_id dropped from the embedded copy

# invoice_item.yaml
name: invoice_item
data:
  id: { datatype: int[1..999999] }
  description: { datatype: char[1..200] }
  qty: { datatype: int[1..999999] }
  unit_price: { datatype: decimal[0.0..9999.99] }
  # invoice_id dropped from the embedded copy
```

Field name for the synthesized array: pluralized child table name (`invoice → invoices`,
`invoice_item → invoice_items`) via a small pluralizer; collision with an existing column →
suffix `_set` and warn.

## 6. Edge cases & decisions

| case | handling |
|---|---|
| Self-referencing FK (`employee.manager_id → employee`) | cycle → stay flat `ref[]`, warn |
| Cycle A→B→A | break at back-edge, flat `ref[]` on that edge, warn (`auto`) / error (`all`) |
| Composite FK (multi-column) | SeedStream `ref`/nesting is single-field → stay flat, warn |
| M:N junction table | keep both flat refs, do not nest, warn |
| Child referenced by 2+ parents | embed into first parent only; others flat `ref[]`, warn |
| 1:1 (unique/PK FK) | `object[child]` instead of array |
| Orphan table (no FK either way) | unchanged, top-level |

All warnings flow through the existing `Inspection.warnings` channel and the CLI summary.

## 7. OpenAPI notes

OpenAPI already expresses nesting natively (`$ref` inside `properties` / `items`). The current
OpenApiInspector flattens or refs these. FK-inversion is less relevant; the work there is to
**preserve** existing nested `$ref`s as `object[...]` / `array[object[...], min..max]` rather than
inventing inversions. Treat as a separate sub-task; DDL is the primary target of this plan.

## 8. Touch points (implementation)

- `inspector/src/main/java/.../ddl/DdlInspector.java` — collect FK edges into a graph instead of
  resolving each FK in isolation; add a post-pass `NestingPlanner`.
- New `inspector/src/main/java/.../NestingPlanner.java` — graph build, classification, cycle/SCC,
  embedding decisions, demotion set. Pure, unit-testable.
- New `inspector/src/main/java/.../Pluralizer.java` — table → field name.
- `cli/.../InspectCommand.java` — `--nest`, `--nest-default-count` options.
- `StructureYamlWriter` — already emits arbitrary datatype strings; verify it round-trips
  `array[object[...]]` and nested comments.
- Spec: promote this from §8 out-of-scope to an implemented section once shipped.

## 9. Testing

- Unit: `NestingPlannerTest` — invoice 3-table chain, self-ref, A↔B cycle, M:N junction,
  shared child (2 parents), 1:1 unique FK, composite FK, name collision.
- Integration: `DdlInspectorIT` with `--nest` on a real DDL → assert emitted YAML parses via the
  existing `TypeParser` and the nested structure generates without a `CircularReferenceException`.
- Round-trip: emitted nested YAML → `execute` a small job → valid output.
- Coverage target consistent with repo (≥80% new-code lines; Sonar gate).

## 10. Open questions

- Default multiplicity: fixed `1..10`, or derive from anything in the schema? (No reliable DDL
  signal — propose fixed, configurable.)
- Should embedded children always be demoted from top-level, or keep both by default? (Propose:
  demote unless also flatly referenced elsewhere; expose `--nest-keep-top-level` later if needed.)
- M:N: future `--nest=all` could pivot a junction into two nested arrays — deferred.
