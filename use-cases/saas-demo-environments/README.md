# SaaS demo environments

**Persona:** sales / solutions engineering, spinning up a prospect-facing demo tenant or refreshing
a standing sandbox before a call.
**Outcome:** one script turns an empty Postgres into a convincing, fully linked CRM dataset
(`users · accounts · contacts · deals · activities`) — **no customer data, no PII, identical on
every environment, and cheap to regenerate on a schedule.**

## The scenario

A prospect wants to click around a live instance before they buy. Hand-built fixtures go stale and
look fake; a sanitised copy of a real customer's data drags PII and NDA risk into a sales demo.
Instead, SeedStream seeds from versioned YAML:

```bash
export DB_PASSWORD=demopass
psql -h localhost -U demouser -d demodb -f schema.sql   # once
./seed.sh                                                 # every reseed
```

Fixed seeds make the dataset **byte-for-byte identical on every demo environment**, so a rep who
rehearsed a pitch against one tenant sees the exact same accounts, deals, and pipeline on the one a
prospect actually clicks through — and a nightly reseed (see below) clears out anything a previous
demo session left behind.

## How referential integrity holds — no magic, no new engine features

SeedStream's reference generator is **stateless and streaming**: `ref[t.id, min..max]` samples a
uniform id from a declared range; it never stores generated ids. Integrity therefore comes from a
convention, not enforcement:

1. **The database owns the PKs.** Every `id` is `GENERATED ALWAYS AS IDENTITY`, so Postgres assigns a
   **dense `1..N`** sequence. The structures **omit `id` entirely** — SeedStream generates no PK.
2. **Children reference that dense pool with a static range.** `contacts.account_id =
   ref[accounts.id, 1..100]` samples `1..100`; because accounts are exactly `1..100`, every FK
   resolves.
3. **Parents are seeded before children** (`seed.sh` order: `users, accounts, contacts, deals,
   activities`).

That's the whole mechanism. Verified end-to-end against Postgres with all FK constraints enabled:
**zero orphan rows** across every FK edge (`contacts.account_id`, `deals.account_id`,
`deals.owner_id`, `activities.contact_id`, `activities.deal_id`).

> **Keep counts and ranges in sync.** The static ranges in `structures/*.yaml` (`1..20`, `1..100`,
> `1..400`, `1..250`) must match the row counts in `seed.sh`. Change them together, or a child can
> reference an id beyond the parent pool.

## Files

| File | Purpose |
|------|---------|
| `schema.sql` | Postgres DDL — 5 tables, IDENTITY PKs, FK constraints, `CHECK` enums |
| `structures/*.yaml` | One flat record per table, **no `id`**, FK columns as static-range `ref[]` |
| `jobs/db_*.yaml` | One database job per table (fixed seed, truncate + restart identity, Postgres) |
| `seed.sh` | Idempotent reseed: truncates and repopulates every table parent-first |

`structures_path` is omitted from the jobs on purpose: living in a dir ending `jobs`, the CLI
auto-resolves the sibling `../structures/`, so this folder is self-contained and relocatable.

## Prerequisites

- A reachable Postgres (jobs target `localhost:5432/demodb` — edit `jobs/*.yaml` for your own).
- `DB_PASSWORD` exported.
- SeedStream on `PATH` (or `export SEEDSTREAM=/path/to/bin/cli`).
- **JDBC driver dropped into the distribution's `extras/` dir.** Drivers are not bundled — the dist is
  vendor-neutral by design and `extras/*` is prepended to the classpath at startup (see
  `docs/CONTAINER.md`, `docs/PERFORMANCE.md`).

## Reseeding on demand

`schema.sql` runs **once**, when the demo database is first provisioned. `seed.sh` is the repeatable
entrypoint: every job sets `truncate_before_insert: true` and `restart_identity: true`, so each run
empties every table, restarts its IDENTITY sequence at 1, and regenerates the same rows from the
fixed per-job seed — no `DROP`/`CREATE`, no schema drift, no leftover state from a previous demo.

Point a nightly cron at it so every prospect environment starts each business day with a clean,
identical pipeline:

```cron
# /etc/cron.d/seedstream-demo-reseed — reseed the SaaS demo CRM at 02:00 every night
0 2 * * * demo DB_PASSWORD=demopass SEEDSTREAM=/opt/seedstream/bin/seedstream \
  /opt/seedstream/use-cases/saas-demo-environments/seed.sh >> /var/log/seedstream-demo-reseed.log 2>&1
```

## Limits (honest)

- **Fields are independent.** No cross-field correlation — a `deals.amount` is unrelated to its
  account's `size` (an SMB account can land a $250k deal), and `activities.occurred_at` is not
  ordered per contact (a `note` can appear to postdate a later `meeting`). SeedStream generates
  plausible shapes, not a coherent narrative.
- **`ref[]` pairs can repeat.** With random refs, an `activities` row can reference the same
  `(contact_id, deal_id)` pair as another row — there's no `UNIQUE` constraint on that combination.
  A `unique` generator ([#212](https://github.com/mferretti/SeedStream/issues/212)) would let you add
  one.
- **Counts are hand-tuned, not auto-derived.** Ranges are static literals tied to the `seed.sh`
  counts, not computed from parent volumes.
- **Postgres-shaped.** IDENTITY PKs and `TRUNCATE ... CASCADE` (via `truncate_before_insert`). Adapt
  the DDL and job `conf:` for other engines.

## Where this comes from

You don't have to hand-write the structures. `seedstream inspect schema.sql --output structures/`
generates one flat structure per `CREATE TABLE` and emits each FK as `ref[table.column, 1..count]`
already. After inspecting, you delete the emitted `id` lines (the DB assigns them) and switch the
`1..count` ranges to static parent volumes — exactly what ships here. Two inspect enhancements would
remove those manual steps: skip identity PKs on emit
([#215](https://github.com/mferretti/SeedStream/issues/215)) and map `CHECK IN(...)` to `enum[...]`
([#214](https://github.com/mferretti/SeedStream/issues/214)).
