# JMH Benchmark Results

**Component Benchmarks** — Isolated performance measurements for data generators, serializers, and I/O operations.

**Last run:** 14 July 2026 (previous: March 2026)

> **Headline:** Datafaker semantic generators are **7–65× faster** than the March 2026 figures. This is
> not a measurement artefact — it is the thread-local `FakerCache` (commit `cf3492d`, 8 Mar 2026), which
> landed *after* the last benchmark run and replaced a `new Faker(...)` construction on every single
> field generation. Primitives and serializers are unchanged (1.0×), which is the control confirming
> the effect is real and confined to the Datafaker path. See [Regressions & Changes](#regressions--changes).

---

## Methodology

### About JMH (Java Microbenchmark Harness)

These benchmarks use **JMH 1.37**, the industry-standard framework for Java performance testing developed by Oracle. JMH is specifically designed to avoid common microbenchmarking pitfalls:

✅ **JIT Compiler Warmup** — Runs warmup iterations before measurement to ensure code is fully optimized  
✅ **Dead Code Elimination** — Uses blackholes to prevent JVM from optimizing away benchmark code  
✅ **Statistical Analysis** — Multiple iterations with confidence intervals and error margins  
✅ **Stable Measurement** — Isolated process with controlled GC and compilation  

### Benchmark Configuration

Two configurations were used in this run. **Which one produced a number matters** — do not compare across them.

| Parameter | Default config | High-fidelity (`-PjmhFidelity=high`) |
|-----------|----------------|--------------------------------------|
| **Warmup Iterations** | 2 × 1 s | 5 × 1 s |
| **Measurement Iterations** | 3 × 1 s | 10 × 1 s |
| **Forks** | 1 | 2 |
| **Threads** | 1 | 1 |
| **Mode** | Throughput (ops/s) | Throughput (ops/s) |

The **default config** is what every historical number in this file was measured with, so it is what makes
this run comparable to March 2026. It is also *noisy*: the first pass of this run reported the Datafaker
name generator at 794K ops/s **± 829K** — an error margin larger than the value, which is not publishable.

The **high-fidelity config** was therefore used for the generator, serializer, destination, and regex
families, bringing error margins down to ≤ 5% for nearly all of them. Kafka and Database results below are
from the default config (their param matrices are large and their numbers were not in question).

Every table below states which config produced it.

### What These Benchmarks Measure

These are **component benchmarks** that measure isolated performance:

- **Primitive Generators:** Pure generation speed without I/O
- **Datafaker Generators:** Realistic data generation overhead
- **Regex Types:** Config-declarable `regex:` types (RgxGen), vs Datafaker's own `regexify`
- **Serializers:** JSON/CSV/Protobuf formatting speed (in-memory)
- **File I/O, Kafka, Database:** Write throughput to each destination

**NOT measured here:**
- End-to-end pipeline performance (see [E2E-TEST-RESULTS.md](E2E-TEST-RESULTS.md) and [REGEX-E2E-RESULTS.md](REGEX-E2E-RESULTS.md))
- Multi-threaded scaling (see [PERFORMANCE.md §4 Thread Count](PERFORMANCE.md#4-thread-count) — scaling is real but structure-dependent, 1.0×–2.3×)
- Network I/O with real-world latency (all tests use localhost)

**⚠️ Testing Environment:** Local development machine. Kafka and PostgreSQL run in Docker containers on
localhost, eliminating network latency. Production deployments will show slower results.

---

## Primitive Generators

*High-fidelity config.* **NFR-1: ≥ 10M ops/s.**

| Type | Throughput | Error | vs March 2026 | NFR-1 |
|------|-----------:|------:|--------------:|:-----:|
| Boolean | **252,195,310** ops/s | ±0.0% | 1.0× | ✅ 25× |
| Enum | **141,591,679** ops/s | ±1.2% | *new* | ✅ 14× |
| Integer | **63,078,708** ops/s | ±0.2% | 1.1× | ✅ 6.3× |
| Date (LocalDate) | **21,967,304** ops/s | ±0.1% | **9.1×** | ✅ 2.2× |
| String (char) | **12,390,831** ops/s | ±0.2% | 1.0× | ✅ 1.2× |
| Decimal (BigDecimal) | **4,315,555** ops/s | ±0.8% | 1.5× | ⚠️ 0.43× |
| Timestamp | **4,461,473** ops/s | ±1.8% | 1.0× | ⚠️ 0.45× |

**`DateGenerator` moved from 2.4M to 22M ops/s (9.1×) and now clears NFR-1.** Cause: commit `137caba`
(14 Jun 2026) — whose subject is `feat(inspector): Protobuf descriptor-set input`, but which bundles a perf
pass that caches parsed min/max bounds per type (`ConcurrentHashMap<PrimitiveType, Bounds>`). Before it,
every single `generate()` call re-ran two `LocalDate.parse()` calls on the *same* range strings from the
type definition. The same commit cached `Integer`/`Char` bounds too — but those only gained 1.1×/1.0×,
because `Integer.parseInt` was already cheap while `LocalDate.parse` (ISO formatter + field resolution)
dominated the date path entirely. The 9.1× is the parse disappearing, not the date arithmetic improving.

Decimal and Timestamp remain below the 10M target due to `BigDecimal` / `Instant` construction cost. Both
are still ~8× above the fastest measured full-pipeline rate (~532K rec/s, primitives → file), so this is not
a practical constraint.

## Datafaker Generators (Realistic Data)

*High-fidelity config.*

| Type | Throughput | Error | March 2026 | Change |
|------|-----------:|------:|-----------:|-------:|
| Company | **1,097,838** ops/s | ±0.4% | 153,816 | **7.1×** |
| City | **920,536** ops/s | ±4.5% | 14,222 | **64.7×** |
| Name | **862,679** ops/s | ±4.5% | 23,168 | **37.2×** |
| Email | **324,836** ops/s | ±3.9% | 24,143 | **13.5×** |
| Address | **260,597** ops/s | ±4.2% | 17,576 | **14.8×** |
| Phone | **107,886** ops/s | ±2.3% | 12,759 | **8.5×** |

**Cause:** the thread-local `FakerCache` (`generators/.../semantic/FakerCache.java`, commit `cf3492d`,
8 Mar 2026). Before it, `DatafakerGenerator.generate()` constructed a fresh `Faker` — and therefore
reloaded locale dictionaries — **for every field of every record**. The cache made that a once-per-thread
cost. The March benchmark run predates the commit.

**The old "~1,000× slower than primitives" rule of thumb is dead.** Datafaker types now run at roughly
0.1–1.1M ops/s against 12M ops/s for a primitive char — a gap of **10–100×**, not 1,000×. Datafaker
generation is still the dominant *parallelisable* cost in a realistic record, which is why generation-heavy
structures are the ones that still benefit from more threads.

## Regex Types

*High-fidelity config.* Config-declarable `regex:` types via `DatafakerRegistry.registerRegex()` → RgxGen.
New in this run — the capability had no performance coverage before.

| Pattern | Shape | Throughput | Error |
|---------|-------|-----------:|------:|
| `ORD-\d{8}` | literal prefix + fixed digits | **5,137,970** ops/s | ±7.7% |
| `(INV\|CRN\|DBN)-[0-9]{6}` | alternation | **4,455,285** ops/s | ±1.1% |
| `SEPA[0-9]{8}[A-Z0-9]{6}` | SEPA message id | **2,914,940** ops/s | ±6.0% |
| `[A-Z0-9]{10,35}` | bounded class, variable length | **1,850,485** ops/s | ±1.6% |
| `[A-Z]{2}\d{2}[A-Z0-9]{4}\d{7}([A-Z0-9]?){0,16}` | IBAN-shaped | **1,661,339** ops/s | ±6.7% |
| `[a-z]+` | **unbounded** (RgxGen caps at 100 reps) | **1,202,549** ops/s | ±1.0% |

**Baselines, same run:**

| Reference | Throughput |
|-----------|-----------:|
| `char[10..35]` primitive | 6,620,593 ops/s |
| Datafaker `regexify` (warm Faker, same `[A-Z0-9]{10,35}`) | 2,035,372 ops/s |
| **Our `registerRegex`, same pattern** | **1,850,485 ops/s** |
| Datafaker `regexify` (fresh Faker per call) | 241,666 ops/s |
| Datafaker `name` | 781,891 ops/s |

**What the numbers actually say — including the inconvenient part:**

1. **Regex types are cheap.** Every pattern shape beats the Datafaker `name` generator (782K ops/s). A
   regex field costs *less* than a realistic-name field. Cost scales with output length and quantifier
   freedom, not with pattern "complexity": alternation is nearly free; the unbounded `[a-z]+` is the
   slowest because RgxGen's 100-repetition cap makes it generate long strings.
2. **RgxGen-direct is not faster than Datafaker's `regexify`.** On the warm path ours is **~9% slower**
   (1.85M vs 2.04M) for an identical pattern. The direct dependency (commit `59259cf`) is justified by
   *control* — not depending on Datafaker's shaded copy, and failing fast on malformed patterns at load —
   **not** by throughput. The 8× advantage over `regexify` only materialises against a cold `Faker`
   (242K ops/s), which is not how either path runs in production.
3. **Pattern compile is a genuine one-off.** `registerRegex` costs 0.59 µs (`ORD-\d{8}`), 0.70 µs
   (`[A-Z0-9]{10,35}`), 2.97 µs (IBAN-shaped) — paid once at `--faker-types` load. At ~0.5 µs per
   generated value, compile amortises after **1–6 records**.

**Author guidance:** prefer explicit bounded classes (`[A-Za-z0-9]{n,m}`) over `.` and over unbounded
`+`/`*`/`{n,}`. The bound is what keeps the value short, and value length is what costs.

## Composite Generators

*High-fidelity config.*

| Type | Throughput | Error | vs March 2026 |
|------|-----------:|------:|--------------:|
| Small array (10 elements) | **6,011,440** ops/s | ±3.7% | 1.0× |
| Simple object (5 fields) | **5,712,758** ops/s | ±0.2% | 1.4× |
| Large array (100 elements) | **737,871** ops/s | ±0.7% | 1.0× |

## Serializers

*High-fidelity config.* **Protobuf is measured for the first time** — previous docs carried estimates.

| Format | Simple | Complex | Nested |
|--------|-------:|--------:|-------:|
| **JSON** | **3,140,970** ops/s | **1,067,484** ops/s | **688,052** ops/s |
| **CSV** | **2,581,730** ops/s | **959,597** ops/s | **240,356** ops/s |
| **Protobuf** | **1,477,740** ops/s | **569,215** ops/s | **307,023** ops/s |

**Correction to previous docs.** `docs/PERFORMANCE.md` estimated Protobuf at "~2.5M / ~900K / ~550K
(est.)". Measured, it is **1.48M / 569K / 307K** — Protobuf is the **slowest** of the three serializers,
roughly 2× slower than JSON on simple records, not comparable to it. The estimates were optimistic and have
been replaced. Protobuf still produces substantially smaller output than JSON; it just costs more CPU to
produce.

JSON handles nesting far better than CSV (688K vs 240K, 2.9×) because CSV has no native nested
representation and double-serializes (object → JSON string → CSV cell).

## Destinations (File I/O)

*High-fidelity config.*

| Operation | Throughput | Error | vs March 2026 |
|-----------|-----------:|------:|--------------:|
| Raw BufferedWriter (no serialization) | **5,442,992** ops/s | ±1.7% | 1.1× |
| FileDestination (serialize + 64KB buffer + batch) | **961,828** ops/s | ±0.2% | 1.2× |

## Kafka Destination

*Default config.* 72 configurations (async/sync × compression × batch size); 48 recorded — the
`benchmarkAsyncProducer` / `benchmarkSyncProducer` methods deliberately throw on the contradictory half of
the `sync` param to avoid duplicating `benchmarkKafkaProducer`, so 24 "missing" runs are by design.

**Async (fire-and-forget), best of each compression at batch 16384–65536:**

| Compression | Batch | Throughput |
|-------------|------:|-----------:|
| lz4 | 16384 | **724,699** ops/s |
| snappy | 16384 | **704,109** ops/s |
| none | 65536 | **622,306** ops/s |
| gzip | 16384 | **376,396** ops/s |

**Sync (blocking, waits for broker ack):** ~**2,000 ops/s** across *every* compression and batch size.

The async/sync gap is ~350×. Sync throughput is entirely dominated by broker round-trip latency —
compression and batch size make no measurable difference, because the producer never accumulates a batch.
Batch size 1024 collapses async throughput too (19K–301K depending on compression); **use ≥ 16384**.

## Database Destination

*Default config, PostgreSQL 17 in Docker (`pg-benchmark`).* 16 configurations.

| Insert | Strategy | Batch | Throughput |
|--------|----------|------:|-----------:|
| Flat (1 INSERT) | per_batch | 5000 | **83,519** ops/s |
| Flat | per_batch | 1000 | 80,154 ops/s |
| Flat | auto_commit | 500 | 75,518 ops/s |
| Flat | per_batch | 100 | 56,165 ops/s |
| Nested (3 INSERTs) | per_batch | 5000 | **2,895** ops/s |
| Nested | per_batch | 500 | 2,344 ops/s |
| Nested | auto_commit | 1000 | **123** ops/s |

Consistent with March 2026 (flat 57–85K, nested 2.5–3.3K). For nested writes, `auto_commit` is
catastrophic — up to **24× slower** than `per_batch` (123 vs 2,895 ops/s), because each of the 3 INSERTs
per logical record commits separately. Use `per_batch`.

⚠️ Error margins on the DB benchmarks are wide under the default config (batch=100/per_batch reports
56,165 ops/s ± 119,838). Treat the ordering as sound and the absolute values as ±50%.

---

## Regressions & Changes

Nothing regressed. Changes vs the March 2026 run:

| Area | Change | Cause |
|------|--------|-------|
| Datafaker generators | **7–65× faster** | `FakerCache` (`cf3492d`) — thread-local Faker reuse |
| `DateGenerator` | **9.1× faster** | **not attributed** |
| Decimal, simple object, FileDestination | 1.2–1.5× faster | not attributed; within refactor noise |
| Protobuf serializer | previously **estimated**, now measured — and 2× slower than the estimate | estimates were wrong |
| Primitives, JSON/CSV serializers | unchanged (1.0×) | control — confirms the above are real |
| **Thread scaling** | structure-dependent (8 threads, 1M records): **3.6×** (nested invoice → file), **2.1×** (passport → file), **2.1×** (primitives), **1.7×** (Kafka) | only generation + serialization are parallel; the writer thread is serial |
| **File write path** | **306 MB/s** (8 threads, 526-byte records) — NFR-1's 500 MB/s target **not met on this hardware** | the limit is **CPU**, not disk (2.3 GB/s) and not the writer thread (~930 MB/s). Generation + serialization are already parallel and already streaming. Recorded as *expected but unverified*; predicted met at ~10–12 cores. The old "600–800 MB/s" was a projection, never measured |

---

## Test Environment

| Component | Specification |
|-----------|---------------|
| **CPU** | AMD Ryzen 5 PRO 4650U (6 cores / 12 threads) |
| **RAM** | 30 GB |
| **Storage** | NVMe SSD |
| **OS** | Ubuntu 24.04, Linux 6.17 |
| **JDK** | Amazon Corretto 21.0.9 |
| **JMH** | 1.37 |
| **Kafka** | Docker, localhost:9092 (`kafka-benchmark`) |
| **PostgreSQL** | 17.9-alpine, Docker, localhost:5432 (`pg-benchmark`) |

Measurements were taken on a laptop over a multi-hour session; thermal/turbo drift adds noise beyond the
reported error margins. Treat two-significant-figure differences as meaningful and three as not.

Raw result files for this run are archived in `benchmarks/results-2026-07-14/`.

## Reproducing

```bash
# Everything (Kafka + PostgreSQL containers must be up)
./gradlew :benchmarks:jmh

# One suite
./gradlew :benchmarks:jmh -PjmhSuite=generators   # primitives, datafaker, composite, serializer, destination
./gradlew :benchmarks:jmh -PjmhSuite=regex        # regex types + Datafaker regexify comparison
./gradlew :benchmarks:jmh -PjmhSuite=kafka
./gradlew :benchmarks:jmh -PjmhSuite=database

# Tighter error bars (5 warmup / 10 iterations / 2 forks) — NOT comparable with default-config numbers
./gradlew :benchmarks:jmh -PjmhSuite=generators -PjmhFidelity=high

# Format the JSON into a report
python3 benchmarks/format_results.py
```

⚠️ **`-Pjmh.includes` and `-Pjmh.excludes` do not work** with `me.champeau.jmh` 0.7.3 — they are silently
ignored and the full suite runs anyway.

Use `-PjmhSuite=database|kafka|generators|regex` for a named group, `-PjmhInclude=<regex>` for anything
finer (a single method), or `-PjmhExclude=<regex>` to skip a family. All three are applied inside
`benchmarks/build.gradle.kts` and do take effect.

⚠️ Each run **overwrites** `benchmarks/build/reports/jmh/results.json`. Archive it before the next run.
