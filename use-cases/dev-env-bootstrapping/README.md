# Developer environment bootstrapping

**Persona:** an application developer joining a team, spinning up a feature branch, or standing up an
ephemeral Kubernetes preview environment.
**Outcome:** one script turns an empty Postgres into a realistic, fully linked task-tracker dataset
(`users · projects · tasks · comments · labels · task_labels`) — **no production dump, no GDPR/PII
risk, no hand-written fixtures.**

## The scenario

A new developer clones the repo and needs a populated database to run and debug against. The old
answer — "copy production and sanitise" — drags real customer PII onto a laptop and is increasingly
blocked by GDPR / DORA / EU AI Act obligations. Instead, SeedStream seeds from versioned YAML:

```bash
export DB_PASSWORD=devpass
./bootstrap.sh          # creates the schema, then seeds every table in FK order
```

Fixed seeds make the dataset **byte-for-byte identical for every developer**, so a bug reported on
one laptop reproduces on every other.

## How referential integrity holds — no magic, no new engine features

SeedStream's reference generator is **stateless and streaming**: `ref[t.id, min..max]` samples a
uniform id from a declared range; it never stores generated ids. Integrity therefore comes from a
convention, not enforcement:

1. **The database owns the PKs.** Every `id` is `GENERATED ALWAYS AS IDENTITY`, so Postgres assigns a
   **dense `1..N`** sequence. The structures **omit `id` entirely** — SeedStream generates no PK.
2. **Children reference that dense pool with a static range.** `projects.owner_id =
   ref[users.id, 1..200]` samples `1..200`; because users are exactly `1..200`, every FK resolves.
3. **Parents are seeded before children** (`bootstrap.sh` order).

That's the whole mechanism. Verified end-to-end against Postgres with all FK constraints enabled:
**zero orphan rows** across the multi-parent (`tasks`, `comments`) and M:N (`task_labels`) edges.

> **Keep counts and ranges in sync.** The static ranges in `structures/*.yaml` (`1..200`, `1..50`,
> `1..500`) must match the `--count` values in `bootstrap.sh`. Change them together, or a child can
> reference an id beyond the parent pool.

## Files

| File | Purpose |
|------|---------|
| `schema.sql` | Postgres DDL — 6 tables, IDENTITY PKs, FK constraints, `CHECK` enums |
| `structures/*.yaml` | One flat record per table, **no `id`**, FK columns as static-range `ref[]` |
| `jobs/db_*.yaml` | One database job per table (fixed seed, Postgres) |
| `bootstrap.sh` | Creates schema, seeds all tables parent-first with matching counts |

`structures_path` is omitted from the jobs on purpose: living in a dir ending `jobs`, the CLI
auto-resolves the sibling `../structures/`, so this folder is self-contained and relocatable.

## Prerequisites

- A reachable Postgres (jobs target `localhost:5432/devdb` — edit `jobs/*.yaml` for your own).
- `DB_PASSWORD` exported.
- SeedStream on `PATH` (or `export SEEDSTREAM=/path/to/bin/cli`).
- **JDBC driver dropped into the distribution's `extras/` dir.** Drivers are not bundled — the dist is
  vendor-neutral by design and `extras/*` is prepended to the classpath at startup (see
  `docs/CONTAINER.md`, `docs/PERFORMANCE.md`).

## Reseeding

`bootstrap.sh` re-runs `schema.sql`, which `DROP … CASCADE`s and recreates the tables — so it resets
to the known-good baseline every time. To reseed data without recreating the schema, add
`truncate_before_insert: true` to the jobs (TRUNCATE … CASCADE; local/disposable DB only).

## Limits (honest)

- **Fields are independent.** No cross-field correlation — a `comment.body` is unrelated to its task,
  a task's `status` unrelated to its `project`. SeedStream generates plausible shapes, not a coherent
  narrative.
- **`parent_task_id` is left NULL.** Self-referencing FKs need deferrable constraints or an ordered
  pass SeedStream does not do today → no subtask hierarchy is generated. Tracked in
  [#213](https://github.com/mferretti/SeedStream/issues/213).
- **`task_labels` pairs can repeat.** With random refs a `(task_id, label_id)` pair may appear twice;
  that's why the join table has a surrogate PK and no `UNIQUE(task_id,label_id)`. A `unique` generator
  ([#212](https://github.com/mferretti/SeedStream/issues/212)) would let you add that constraint.
- **Counts are uniform-ish / hand-tuned.** Ranges are static literals tied to the bootstrap counts,
  not auto-derived from parent volumes.
- **Postgres-shaped.** IDENTITY + `DROP … CASCADE`. Adapt DDL for other engines.

## Where this comes from

You don't have to hand-write the structures. `seedstream inspect schema.sql --output structures/`
generates one flat structure per `CREATE TABLE` and emits each FK as `ref[table.column, 1..count]`
already. After inspecting, you delete the emitted `id` lines (the DB assigns them) and switch the
`1..count` ranges to static parent volumes — exactly what ships here. Two inspect enhancements would
remove those manual steps: skip identity PKs on emit
([#215](https://github.com/mferretti/SeedStream/issues/215)) and map `CHECK IN(...)` to `enum[...]`
([#214](https://github.com/mferretti/SeedStream/issues/214)).
