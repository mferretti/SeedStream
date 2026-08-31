# Performance and load testing

**Persona:** a performance engineer who needs to load-test **their own** Kafka cluster or Postgres
database — throughput, index behavior, query latency — before a release or a capacity review.
**Outcome:** one script drives millions of realistic, cardinality-varied analytics events into Kafka
or Postgres so you can measure produce/insert throughput and, for the DB path, how your indexes
perform under a realistic access pattern — **no hand-written `INSERT` loops, no synthetic
all-identical rows that make every index scan look artificially fast.**

## The scenario

Before a release, or before sizing a cluster, you need to know how your Kafka topic or Postgres
table behaves under real volume: what's the sustained produce/insert rate, does a query against
`event_type` or `occurred_at` stay fast once the table has 50M rows, does an index degrade the way
you expect. The old answer is a throwaway script with a `for` loop and `random.choice()` — slow to
write, and the data it produces is usually too uniform (or too random) to exercise indexes the way
production data does. SeedStream generates that data from a versioned YAML spec instead, at whatever
volume you need, with deliberately mixed cardinality (a 6-value `event_type` enum next to a
1,000,000-value `user_id` range) so the indexes in `schema.sql` behave like they would in production.

```bash
export DB_PASSWORD=loadpass
COUNT=50000000 THREADS=8 DEST=db bash loadtest.sh
```

## Distinct from the internal `benchmarks/`

This use case and the repo's `benchmarks/` module answer different questions — don't confuse them:

- **`use-cases/performance-load-testing/` (this folder)** — load-test **your** system. You point it
  at your own Kafka broker or Postgres instance and measure how *that system* behaves under
  SeedStream-generated volume. SeedStream here is just the data generator.
- **[`benchmarks/`](../../benchmarks/) and [`docs/PERFORMANCE.md`](../../docs/PERFORMANCE.md)** —
  benchmark **SeedStream itself**: JMH component benchmarks and E2E suites that measure the
  generator's and serializers' own throughput (records/sec, ops/sec) against local Docker
  containers, to answer "how fast is the engine."

If you're trying to answer "how fast can SeedStream generate data," read those. If you're trying to
answer "how does my Kafka/Postgres hold up," this is the right folder.

## Files

| File | Purpose |
|------|---------|
| `structures/event.yaml` | One analytics/telemetry event — mixed cardinality (6-value enum next to a 1M-value id range), no `id` field |
| `jobs/kafka_events.yaml` | Kafka job tuned for throughput (`batch_size`, `linger_ms`, `compression`) |
| `jobs/db_events.yaml` | Postgres job tuned for repeatable load (`truncate_before_insert` + `restart_identity`) |
| `schema.sql` | Postgres DDL — one `events` table, three secondary indexes (`user_id`, `event_type`, `occurred_at`) |
| `loadtest.sh` | Runs the job for the chosen destination, times it, prints records/sec |

`structures_path` is omitted from both jobs on purpose: living in a dir ending `jobs`, the CLI
auto-resolves the sibling `../structures/`, so this folder is self-contained and relocatable.

## How to scale

`loadtest.sh` reads three env vars:

| Var | Default | Meaning |
|---|---|---|
| `COUNT` | `1000000` | Records to generate |
| `THREADS` | `$(nproc)` | Worker threads (`--threads`) |
| `DEST` | `kafka` | `kafka` or `db` |

```bash
COUNT=50000000 THREADS=8 DEST=kafka bash loadtest.sh
```

Beyond `--count` and `--threads`, the knobs that actually move throughput live in the job YAML:

- **Kafka** (`jobs/kafka_events.yaml`): `batch_size`, `linger_ms`, `compression`. Dropping
  `compression: none` can be a large win on a fast/local broker link — see
  [`docs/PERFORMANCE.md` §4](../../docs/PERFORMANCE.md#4-thread-count) ("Why Kafka scales worst").
- **Database** (`jobs/db_events.yaml`): `batch_size`, `transaction_strategy`. `per_batch` is the
  right default; `auto_commit` is measured 2-3× slower.

**On threads:** [`docs/PERFORMANCE.md`](../../docs/PERFORMANCE.md) measures 4 threads as a safe
default — beyond that, the destination's I/O (network to the broker, round-trips to Postgres) is
the bottleneck, not generation. Benchmark with 1M+ records; short runs understate both throughput
and thread scaling.

## Measuring throughput

`loadtest.sh` times the run with `date +%s.%N` around the `seedstream execute` call and prints a
summary:

```
==================================================
 dest      : db
 count     : 1000000
 threads   : 8
 elapsed   : 14.203s
 rec/sec   : 70407
==================================================
```

This is wall-clock throughput of the whole CLI process (JVM startup included), not the engine-only
figure — see `docs/PERFORMANCE.md`'s "How to read the E2E rows" note if you need to separate the two.

## Prerequisites

- A reachable Kafka broker at `localhost:9092` (for `DEST=kafka`) and/or Postgres at
  `localhost:5432/loadtestdb` (for `DEST=db`) — edit `jobs/*.yaml` for your own hosts.
- `DB_PASSWORD` exported (for `DEST=db`).
- SeedStream on `PATH`, or `export SEEDSTREAM=/path/to/bin/seedstream`.
- **JDBC driver dropped into the distribution's `extras/` dir** (for `DEST=db`) — drivers are not
  bundled; see `docs/CONTAINER.md` / `docs/PERFORMANCE.md`.
- For `DEST=db`, apply `schema.sql` once before the first run.

## Limits (honest)

- **Fields are independent.** No per-record correlation — an `event_type` of `purchase` doesn't bias
  `amount` upward, `device` doesn't correlate with `country`. SeedStream generates plausible shapes,
  not a coherent user journey.
- **The fixed seed makes the *data* reproducible, not the *timings*.** Re-running with the same seed
  regenerates byte-identical records, but elapsed time and records/sec depend on your hardware,
  broker, disk, and network — they are not part of the reproducibility guarantee.
- **Single denormalized table.** No joins, no foreign keys — this measures raw produce/insert
  throughput and single-table index behavior, not a multi-table workload.
- **You're measuring produce/insert throughput, not consumption.** A Kafka consumer is not included;
  if you need to measure consumer-side lag or fan-out, that's a separate harness.
- **Not an APM.** This tells you rows/sec and lets you point real query tooling (`EXPLAIN ANALYZE`,
  your broker's own metrics) at the loaded data — it doesn't collect query plans or latency
  percentiles itself.
