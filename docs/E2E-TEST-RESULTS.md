# End-to-End Test Results

**Date:** June 14, 2026
**Record Count:** 100,000 per test
**Test Matrix:** file (3 formats) + kafka (2 formats) + database = 54 executed, 9 skipped
**Timing:** millisecond-resolution wall clock (`throughput = records × 1000 / duration_ms`)

> **Re-run after the June 2026 engine work** (worker-side parallel serialization,
> `FieldRecord` flyweight, queue chunk-batching — see [CHANGELOG](../CHANGELOG.md)).
> Numbers supersede the March 2026 results, which used a coarse **whole-second**
> timer that quantized throughput to 100000/{2,3,4}s (= 50000/33333/25000) and
> systematically inflated peaks. Compare bands, not exact figures.

**⚠️ Local testing environment.** Single machine; Kafka + PostgreSQL in Docker on
`localhost` (no network latency); file destination on local SSD; loopback only.
Production adds WAN/LAN latency, bandwidth limits, and broker/storage overhead —
expect Kafka 30–50% slower and absolute numbers to differ by hardware.

## Structures under test

| Destination | Structure | Shape |
|--|--|--|
| file (json/csv/protobuf) | `passport` | flat, 11 fields, ~200 B/record |
| kafka (json/protobuf) | `invoice` | nested: issuer, recipient, line_items[] |
| database | `invoice` | nested → 4 relational tables (`invoices`, `issuer`, `recipient`, `line_items`) |

File tests use the flat `passport` structure so the three formats are
apples-to-apples. Kafka and database use the heavier nested `invoice` structure.
`kafka/csv` is intentionally skipped — a nested record does not have a meaningful
flat CSV form.

## Summary (rec/s)

| Destination / Format | min | mean | peak | n |
|--|--|--|--|--|
| file / csv | 31,989 | 36,028 | 38,372 | 9 |
| file / protobuf | 32,404 | 35,411 | 37,864 | 9 |
| file / json | 32,351 | 34,447 | 39,666 | 9 |
| kafka / json | 24,746 | 28,036 | 31,605 | 9 |
| kafka / protobuf | 21,353 | 24,080 | 25,654 | 9 |
| database (invoice, nested) | 530 | 605 | 676 | 9 |

**45 SUCCESS, 0 FAILED, 9 skipped** (kafka/csv, by design).

## File destination — `passport` (rec/s, heap used MB, GC %)

| Format | 1 thread | 4 threads | 8 threads |
|--|--|--|--|
| json (256 MB) | 34,530 / 57 / 1.93% | 39,666 / 57 / 1.98% | 37,411 / 57 / 1.72% |
| json (512 MB) | 33,658 / 30 / 0.74% | 33,003 / 31 / 0.96% | 32,435 / 31 / 0.94% |
| json (1024 MB) | 34,164 / 76 / 1.67% | 32,808 / 75 / 1.97% | 32,351 / 78 / 1.71% |
| csv (256 MB) | 37,664 / 56 / 2.30% | 38,372 / 54 / 1.92% | 33,211 / 57 / 1.96% |
| csv (512 MB) | 36,284 / 31 / 1.05% | 37,965 / 31 / 0.91% | 37,147 / 32 / 1.23% |
| csv (1024 MB) | 36,589 / 76 / 1.98% | 35,038 / 76 / 2.00% | 31,989 / 78 / 1.86% |
| protobuf (256 MB) | 34,013 / 41 / 2.48% | 37,864 / 42 / 2.20% | 36,859 / 41 / 2.25% |
| protobuf (512 MB) | 33,189 / 29 / 0.93% | 36,496 / 28 / 1.35% | 35,448 / 29 / 1.21% |
| protobuf (1024 MB) | 32,404 / 73 / 1.85% | 37,243 / 73 / 1.90% | 35,186 / 73 / 2.15% |

Flat passport is **overhead/IO-bound at a ~35–40k ceiling** — generation and
serialization are cheap, so the formats converge and thread count barely moves
throughput. Heap stays low (28–78 MB) thanks to the `FieldRecord` flyweight and
streaming writes.

## Kafka destination — `invoice` (rec/s, heap used MB, GC %)

| Format | 1 thread | 4 threads | 8 threads |
|--|--|--|--|
| json (256 MB) | 25,933 / 19 / 1.87% | 31,605 / 60 / 2.34% | 29,052 / 64 / 1.92% |
| json (512 MB) | 26,184 / 19 / 1.13% | 28,977 / 58 / 1.48% | 29,779 / 64 / 1.61% |
| json (1024 MB) | 24,746 / 18 / 0.72% | 27,639 / 57 / 1.35% | 28,409 / 63 / 1.53% |
| protobuf (256 MB) | 22,925 / 20 / 2.25% | 25,654 / 66 / 2.39% | 24,943 / 66 / 2.47% |
| protobuf (512 MB) | 22,857 / 20 / 1.58% | 24,666 / 57 / 1.87% | 24,096 / 62 / 1.78% |
| protobuf (1024 MB) | 21,353 / 19 / 1.02% | 25,342 / 57 / 1.44% | 24,888 / 60 / 4.95% |

Kafka uses the **worker-side serialized pipeline** (each message is an
independently-encoded payload), and the nested invoice payload makes
serialization heavy enough that throughput **scales with threads** (1→4: ~25k →
~31k json).

## Database destination — `invoice` nested → 4 tables (rec/s, duration, heap)

| Threads | 256 MB | 512 MB | 1024 MB |
|--|--|--|--|
| 1 | 652 / 153 s / 19 MB | 626 / 160 s / 18 MB | 585 / 171 s / 18 MB |
| 4 | 632 / 158 s / 21 MB | 559 / 179 s / 20 MB | 676 / 148 s / 20 MB |
| 8 | 538 / 186 s / 23 MB | 530 / 189 s / 24 MB | 647 / 155 s / 27 MB |

### Why database throughput looks ~50× lower — record folding

The `rec/s` figure counts **logical invoice records**, but one invoice is not one
write. The nested `invoice` is **folded (decomposed) into its parent/child rows**
and written across four tables:

- 1 row → `invoices` (parent)
- 1 row → `issuer` (`object[company]`)
- 1 row → `recipient` (`object[company]`)
- **1–20 rows → `line_items`** (`array[object[line_item], 1..20]`, mean ≈ 10.5),
  each carrying an injected `invoices_id` foreign key

So **each logical record is ~13.5 physical rows** (1 + 1 + 1 + ~10.5). A
100,000-invoice run therefore writes roughly:

| Table | Rows |
|--|--|
| invoices | 100,000 |
| issuer | 100,000 |
| recipient | 100,000 |
| line_items | ~1,050,000 |
| **Total** | **~1,350,000 rows** |

Restating the throughput in **physical rows**: ~676 invoices/s × ~13.5 ≈
**~9,100 rows/s** inserted. That is the apples-to-apples comparison — file/Kafka
serialize **one** blob per record, while the database performs ~13.5 keyed
multi-table `INSERT`s (`per_batch` strategy) plus FK injection per record. The gap
is the relational fan-out, not a generation slowdown.

More worker threads do **not** help — the bottleneck is the single JDBC
connection / DB write path, not record generation. Heap stays tiny (18–27 MB).

## Running the full suite

```bash
./gradlew :cli:installDist
./benchmarks/run_e2e_test.sh --record-count 100000
```

Prerequisites for the non-file destinations:

- **Kafka** — the runner does **not** start it; bring up a container named
  `kafka-benchmark` on `localhost:9092` (see `benchmarks/run_kafka_benchmark.sh`
  / `benchmarks/KAFKA-BENCHMARK-GUIDE.md`). Absent → kafka tests skipped.
- **PostgreSQL** — the runner auto-starts a `postgres-benchmark` container.
- **JDBC driver** — the CLI does **not** bundle JDBC drivers (by design). Drop the
  PostgreSQL driver JAR into the distribution's `extras/` directory
  (`cli/build/install/cli/extras/`), which is on the classpath at startup;
  otherwise database tests fail with `No suitable driver`.

Outputs: `benchmarks/e2e_results.csv` (raw) and
`benchmarks/E2E-TEST-RESULTS.md` (generated report).
