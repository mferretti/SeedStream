# CI Pipeline Database Seeding

Seed a fresh database with **known, deterministic** data before integration tests
run. Same seed → same rows every run → assertions can reference specific generated
values without depending on pre-existing state. (Issue #80.)

## Why

- **No flaky "depends on pre-existing state" failures.** Each run reseeds from clean.
- **Reproducible across branches and machines.** A fixed seed produces byte-for-byte
  identical data (see the determinism guarantee in the top-level `README.md`).
- **Assertable fixtures.** Because the dataset is fixed, tests can assert on concrete
  rows (counts, specific usernames, date ranges).

## How it works

`truncate_before_insert: true` on a `database` job empties each target table with
`TRUNCATE TABLE ... CASCADE` before its first insert, then streams the generated
rows in. Combined with a fixed `seed`, one `execute` gives a clean, identical dataset.

> ⚠️ **Destructive.** TRUNCATE (with CASCADE to FK dependents) wipes the table. Point
> it only at a disposable/CI database. Default is `false` — nothing is truncated unless
> you opt in. PostgreSQL/Oracle only (CASCADE syntax).

## Files in this example

| File | Purpose |
|------|---------|
| `config/structures/ci_user.yaml` | Flat user fixture (DB assigns the PK identity column) |
| `config/jobs/db_ci_seed_users.yaml` | Database job: fixed seed `424242`, `truncate_before_insert: true` |
| `config/sql/ci_seed_users.sql` | One-time DDL for the `ci_users` table |

## Local run

```bash
export DB_PASSWORD=...
psql -U dbuser -d testdb -f config/sql/ci_seed_users.sql   # once — create the table
./seedstream execute --job config/jobs/db_ci_seed_users.yaml --count 100
./gradlew integrationTest                                  # sees the same 100 rows
```

Re-running `execute` truncates and reseeds — row `id`s restart, the data columns are
identical every time.

## In a CI workflow

Illustrative GitHub Actions steps (adapt to your CI). The DB is a disposable service
container, so truncation is safe:

```yaml
jobs:
  integration-tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: dbuser
          POSTGRES_PASSWORD: ${{ secrets.DB_PASSWORD }}
        ports: ["5432:5432"]
        options: >-
          --health-cmd="pg_isready -U dbuser" --health-interval=5s
          --health-timeout=5s --health-retries=10
    env:
      DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
    steps:
      - uses: actions/checkout@v5

      - name: Create fixture schema
        run: psql -h localhost -U dbuser -d testdb -f config/sql/ci_seed_users.sql
        env:
          PGPASSWORD: ${{ secrets.DB_PASSWORD }}

      - name: Seed deterministic data
        run: ./seedstream execute --job config/jobs/db_ci_seed_users.yaml --count 100

      - name: Run integration tests
        run: ./gradlew integrationTest
```

## Asserting against the seeded data

With a fixed seed the dataset is stable, so integration tests can assert on it. Example
shape (adapt to your test framework / query layer):

```java
// 100 rows were seeded, all active flag deterministic, dates within the configured range
assertThat(userRepository.count()).isEqualTo(100);
assertThat(userRepository.findAll())
    .allSatisfy(u -> assertThat(u.getSignupDate())
        .isBetween(LocalDate.parse("2020-01-01"), LocalDate.parse("2024-12-31")));
```

To pin exact values (e.g. `assertThat(result).containsKey("<username>")`), run the seed
once, read the generated value from the DB, then hard-code it in the assertion — it will
not change as long as the seed and structure stay fixed.

## Per-suite isolation

For full isolation, reseed between suites: run `execute` again (it truncates first) before
each suite that needs a pristine table. Because the seed is fixed, every suite starts from
the identical dataset.
