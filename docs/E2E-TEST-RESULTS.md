# End-to-End Test Results

**Date:** June 11, 2026
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
| file / csv | 28,612 | 33,135 | 35,549 | 9 |
| file / protobuf | 29,231 | 32,132 | 34,199 | 9 |
| file / json | 28,240 | 31,023 | 32,530 | 9 |
| kafka / json | 21,621 | 26,239 | 28,571 | 9 |
| kafka / protobuf | 20,725 | 23,320 | 25,575 | 9 |
| database (invoice, nested) | 522 | 570 | 655 | 9 |

**54 SUCCESS, 0 FAILED, 9 skipped** (kafka/csv, by design).

## File destination — `passport` (rec/s, heap used MB, GC %)

| Format | 1 thread | 4 threads | 8 threads |
|--|--|--|--|
| json (256 MB) | 32,258 / 55 / 2.06% | 28,240 / 76 / 1.89% | 32,530 / 77 / 2.05% |
| json (512 MB) | 30,358 / 30 / 0.94% | 31,046 / 31 / 0.99% | 32,071 / 31 / 1.03% |
| json (1024 MB) | 29,967 / 75 / 1.59% | 32,216 / 39 / 1.16% | 30,525 / 36 / 0.92% |
| csv (256 MB) | 32,819 / 56 / 2.30% | 34,746 / 76 / 2.95% | 35,423 / 79 / 2.73% |
| csv (512 MB) | 31,515 / 30 / 1.10% | 33,886 / 31 / 1.05% | 35,549 / 32 / 1.21% |
| csv (1024 MB) | 32,583 / 76 / 2.15% | 33,090 / 78 / 1.75% | 28,612 / 36 / 1.06% |
| protobuf (256 MB) | 30,826 / 40 / 2.22% | 34,025 / 74 / 2.59% | 32,905 / 75 / 2.07% |
| protobuf (512 MB) | 30,656 / 28 / 1.13% | 33,255 / 28 / 1.30% | 34,199 / 28 / 1.50% |
| protobuf (1024 MB) | 29,231 / 72 / 1.84% | 33,244 / 43 / 1.56% | 30,854 / 78 / 2.25% |

Flat passport is **overhead/IO-bound at a ~33k ceiling** — generation and
serialization are cheap, so the formats converge and thread count barely moves
throughput. Heap stays low (28–79 MB) thanks to the `FieldRecord` flyweight and
streaming writes.

## Kafka destination — `invoice` (rec/s, heap used MB, GC %)

| Format | 1 thread | 4 threads | 8 threads |
|--|--|--|--|
| json (256 MB) | 21,621 / 44 / 1.84% | 27,932 / 64 / 2.29% | 28,571 / 69 / 2.17% |
| json (512 MB) | 22,941 / 25 / 1.15% | 28,473 / 55 / 1.71% | 27,716 / 61 / 1.64% |
| json (1024 MB) | 23,485 / 18 / 0.73% | 27,502 / 56 / 1.43% | 27,917 / 62 / 1.31% |
| protobuf (256 MB) | 21,953 / 18 / 2.28% | 25,568 / 63 / 2.58% | 23,551 / 64 / 2.31% |
| protobuf (512 MB) | 21,519 / 19 / 1.68% | 24,881 / 55 / 2.02% | 23,923 / 60 / 1.87% |
| protobuf (1024 MB) | 20,725 / 18 / 1.14% | 25,575 / 56 / 1.43% | 22,192 / 58 / 1.40% |

Kafka uses the **worker-side serialized pipeline** (each message is an
independently-encoded payload), and the nested invoice payload makes
serialization heavy enough that throughput **scales with threads** (1→4: ~22k →
~28k json).

## Database destination — `invoice` nested → 4 tables (rec/s, duration, heap)

| Threads | 256 MB | 512 MB | 1024 MB |
|--|--|--|--|
| 1 | 620 / 161 s / 18 MB | 522 / 191 s / 18 MB | 619 / 161 s / 18 MB |
| 4 | 606 / 164 s / 20 MB | 522 / 191 s / 18 MB | 524 / 190 s / 21 MB |
| 8 | 655 / 152 s / 18 MB | 539 / 185 s / 18 MB | 528 / 189 s / 25 MB |

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

Restating the throughput in **physical rows**: ~655 invoices/s × ~13.5 ≈
**~8,800 rows/s** inserted. That is the apples-to-apples comparison — file/Kafka
serialize **one** blob per record, while the database performs ~13.5 keyed
multi-table `INSERT`s (`per_batch` strategy) plus FK injection per record. The gap
is the relational fan-out, not a generation slowdown.

More worker threads do **not** help — the bottleneck is the single JDBC
connection / DB write path, not record generation. Heap stays tiny (18–25 MB).

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
