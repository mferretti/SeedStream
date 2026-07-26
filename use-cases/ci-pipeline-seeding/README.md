# CI pipeline database seeding

**Persona:** a platform / DevOps engineer who owns the integration-test stage of a build pipeline.
**Outcome:** one script puts a **known, byte-for-byte identical** dataset into a disposable database
before every test run — so assertions can name concrete rows, and "the test only fails on CI"
stops being a category of bug.

## The scenario

Integration tests that share a long-lived database drift: one suite leaves rows behind, the next
asserts `count() == 12` and gets 14. The usual patches — ordering tests, `@DirtiesContext`, a
hand-maintained `data.sql` — decay as the schema grows.

SeedStream replaces them with a versioned fixture. Each job pins a seed (`424242`) and opts into
destructive reseeding, so the pipeline step is just:

```bash
export DB_PASSWORD=...
./seed.sh                 # truncate + reseed all three tables (1,700 rows, one JVM per table)
./gradlew integrationTest # sees the same 1,700 rows it saw last build
```

Re-running `seed.sh` is idempotent: same rows, same ids, same order. Nothing needs tearing down
first, and suites that need isolation just call it again between them.

## The dataset

A small order service — enough to exercise foreign keys, enums, dates and money:

| Table | Rows | Notes |
|---|---|---|
| `customers` | 100 | `email`, `full_name`, `signup_date`, `active` |
| `orders` | 400 | `customer_id → customers.id`, status enum, `NUMERIC(10,2)` total, timestamp |
| `order_items` | 1200 | `order_id → orders.id`, product name, quantity `1..5`, unit price |

`orders.total_amount` is generated independently of its line items — SeedStream does no
cross-record arithmetic. Assert on ranges and counts, not on totals summing to items.

## Why the ids stay dense — `restart_identity`

Every PK is `GENERATED ALWAYS AS IDENTITY`, so Postgres owns it and the structures omit `id`
entirely. Children reference the parent pool with a **static** range, e.g.
`orders.customer_id = ref[customers.id, 1..100]`. That only works while the parents really are
numbered `1..100`.

Plain `TRUNCATE` does **not** reset a sequence. Reseed a table with `truncate_before_insert` alone
and the second run numbers customers `101..200` — the `orders` insert then fails wholesale with a
foreign-key violation, and the CLI exits `1`. So each job here sets both:

```yaml
truncate_before_insert: true   # ⚠️ DESTRUCTIVE — empties the table (CASCADE)
restart_identity: true         # → TRUNCATE ... RESTART IDENTITY CASCADE
```

`restart_identity` brings the sequence back to 1 on every run, which is what makes the static
`ref[...]` ranges hold forever and the fingerprints below stable. It is PostgreSQL-only
(`RESTART IDENTITY` is not valid Oracle syntax) and has no effect unless `truncate_before_insert`
is on — SeedStream rejects the combination at startup rather than silently ignoring it.

> ⚠️ **These jobs wipe tables.** Point them at a disposable/CI database only. Both flags default to
> `false`; nothing is truncated unless you opt in.

## Files

| File | Purpose |
|---|---|
| `schema.sql` | DDL for the three tables. Run **once** per database (`CREATE TABLE IF NOT EXISTS`) |
| `structures/*.yaml` | Field definitions — no `id`, the DB assigns it |
| `jobs/db_*.yaml` | Database jobs: fixed seed, truncate + restart identity |
| `seed.sh` | Seeds all three tables parent-first with the counts the `ref[]` ranges assume |
| `verify.sql` | Emits `<table> <row_count> <md5>` per table — the determinism fingerprint |
| `expected-fingerprint.txt` | The committed fingerprint CI diffs against |

## Run it locally

Needs a reachable Postgres (jobs point at `localhost:5432/ci_testdb`, user `ci_user` — edit
`jobs/*.yaml` for your target) and the PostgreSQL JDBC driver in the distribution's `extras/`
directory (drivers are not bundled — see [`docs/CONTAINER.md`](../../docs/CONTAINER.md)).

```bash
docker run --rm -d --name ci-pg -p 5432:5432 \
  -e POSTGRES_DB=ci_testdb -e POSTGRES_USER=ci_user -e POSTGRES_PASSWORD=seedstream_ci \
  postgres:16-alpine

export DB_PASSWORD=seedstream_ci
export PGPASSWORD=$DB_PASSWORD

psql -h localhost -U ci_user -d ci_testdb -f schema.sql     # once
./seed.sh                                                   # every run
psql -h localhost -U ci_user -d ci_testdb -q -X -f verify.sql | grep -v '^$'
```

Expected output — and identical after any number of reruns:

```
customers 100 b642a5c040d4881e4201958198ad6e9c
orders 400 5c0d36ddda6e1c066f6c751935471c66
order_items 1200 570394dba09cae0f47439df4f7a5e370
```

## In your pipeline

[`.github/workflows/use-case-ci-seeding.yml`](../../.github/workflows/use-case-ci-seeding.yml) runs
exactly this against a Postgres service container on every PR that touches the use case or the
database destination. It seeds twice and asserts run 1 == run 2 == `expected-fingerprint.txt`, so
the example is verified rather than illustrative. Copy the job and swap the test step for yours:

```yaml
      - name: Seed deterministic data
        run: ./use-cases/ci-pipeline-seeding/seed.sh
        env:
          DB_PASSWORD: ${{ secrets.DB_PASSWORD }}

      - name: Integration tests
        run: ./gradlew integrationTest
```

## Asserting against the fixture

Because ids are dense and values are seed-pinned, tests can assert on concrete rows:

```java
assertThat(customerRepository.count()).isEqualTo(100);
assertThat(customerRepository.findById(1L))
    .get()
    .extracting(Customer::getEmail)
    .isEqualTo("coy.raynor@gmail.com");   // run seed.sh once, read the value, pin it
assertThat(orderRepository.findAll())
    .allSatisfy(o -> assertThat(o.getTotalAmount())
        .isBetween(new BigDecimal("5.00"), new BigDecimal("999.99")));
```

## Limits

- **PostgreSQL only.** `TRUNCATE ... RESTART IDENTITY CASCADE` is Postgres syntax; Oracle supports
  `CASCADE` but not `RESTART IDENTITY`, MySQL/SQL Server neither.
- **`ref[]` ranges are static.** The counts in `seed.sh` and the ranges in `structures/*.yaml` are
  two halves of one contract — change one, change the other, then regenerate the fingerprint.
- **The fingerprint is version-bound.** A Datafaker or generator change moves the generated values;
  that is intentional and flagged in the [CHANGELOG](../../CHANGELOG.md). Regenerate
  `expected-fingerprint.txt` with `verify.sql` when it happens.
- **No uniqueness guarantee.** `ref[]` samples uniformly, so a customer may get many orders or none.
  Assert on aggregates, not on "every customer has an order".
- **Timestamps are stored in UTC**, independent of the JVM's time zone — this fixture is what caught
  the bug where they weren't (see the CHANGELOG). `orders.placed_at` therefore fingerprints
  identically on a developer laptop and a CI runner.
