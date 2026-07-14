# Performance Guide

This document provides comprehensive performance benchmarks, tuning guidance, and optimization strategies for SeedStream.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Benchmark Results](#benchmark-results)
3. [Real-World Performance](#real-world-performance)
4. [Performance Tuning](#performance-tuning) — File, Database, Kafka, Format, Complexity
5. [Hardware Requirements](#hardware-requirements)
6. [Running Benchmarks](#running-benchmarks)

---

## Quick Reference

### Expected Throughput

| Workload | Throughput | Notes |
|----------|------------|-------|
| **Primitive types (in-memory)** | 4-252 M records/sec | Boolean fastest (252M), decimal/timestamp slowest (~4.3M) |
| **Realistic data (Datafaker)** | 108 K - 1.1 M records/sec | Company fastest (1.1M), phones slowest (108K) — **7-65× faster since the `FakerCache`** |
| **Regex types (`regex:`)** | 1.2 - 5.1 M records/sec | Cheaper than a Datafaker name field; cost scales with output length |
| **File output (JSON)** | ~32,000-38,000 records/sec | With 64KB buffer + batching (E2E validated, Jul 2026) |
| **File output (CSV)** | ~33,000-39,000 records/sec | Fastest format for flat data (E2E validated, Jul 2026) |
| **File output (Protobuf)** | ~32,000-37,000 records/sec | Binary output is 50-70% smaller than JSON, but *costs more CPU to produce* (E2E validated, Jul 2026) |
| **Kafka output (JSON/Protobuf)** | ~21,000-31,000 records/sec | Network-bound; JSON ~25-31K, Protobuf ~21-26K (E2E validated, Jul 2026) |
| **Database output (PostgreSQL, flat)** | 55,000-84,000 records/sec | BIGSERIAL PK, JDBC batching, local Docker; see DB section for realistic production estimates |
| **Database output (PostgreSQL, nested)** | 1,900-2,900 records/sec | 3 INSERTs per logical record (1 parent + 2 children), `per_batch` strategy |

**⚠️ Benchmark Environment:** All tests run on **localhost** (Kafka and PostgreSQL in Docker containers). Real-world production deployments with network latency will show **30-50% lower throughput** for Kafka destinations and **50-70% lower throughput** for database destinations.

> ### ⚠️ How to read the E2E rows
>
> The file/Kafka/database rows above are measured by `benchmarks/run_e2e_test.sh`, which times the **whole
> CLI process** — including ~1.5–1.7 s of fixed JVM startup, class loading, and Datafaker locale
> initialisation. At the 100K-record job size used, that fixed cost is roughly *half the wall clock*.
>
> They are therefore an honest answer to *"what does one `seedstream execute` run of 100K records deliver?"*
> — and a **misleading** answer to *"what is the engine's throughput?"* or *"does threading help?"*. The
> engine's own reported rate (`Time elapsed` in the CLI output) for the same jobs is 2–3× higher:
>
> | Job (engine-only, **1M records**) | 1 thread | 4 threads | 8 threads |
> |---|---|---|---|
> | passport → file/json | 122K rec/s | 228K rec/s | 258K rec/s |
> | invoice (nested) → file/json | 51K rec/s | 143K rec/s | 185K rec/s |
> | invoice (nested) → kafka/json (snappy) | 46K rec/s | 72K rec/s | 77K rec/s |
> | primitives-only → file/json | 732K rec/s | 1.52M rec/s | 1.54M rec/s |
>
> **Use 1M+ records to benchmark this engine.** At 100–200K, JVM startup *and* JIT warmup both dominate:
> the same passport job measures 1.4× scaling at 200K but 2.1× at 1M. Short runs understate both
> throughput and scaling.

**Rule of Thumb**: **generator choice is rarely your bottleneck; the writer and the destination usually are.**
Since the thread-local `FakerCache`, every generator family runs at 0.1M–252M ops/s, while the *serial* part
of the pipeline (single writer thread → destination) tops out around **1.5M rec/s** for file. Threads pay in
proportion to how much of your per-record cost is generation:

- **Generation-heavy** (nested objects, many Datafaker fields) → **3.6×** on 8 threads
- **Lighter records** (flat, few Datafaker fields) → **~2.1×**
- **Kafka** → **1.7×** with compression, **2.2×** without; see [§4 Thread Count](#4-thread-count)

> The old advice here — *"realistic Datafaker data is 1,000× slower than primitives, plan accordingly"* — was
> measured before the `FakerCache` and is **no longer true**. The gap is now 10–100×.

---

## Benchmark Results

All benchmarks run with **JMH** (Java Microbenchmark Harness) on development hardware. **Results validated
14 July 2026**, using the high-fidelity config (5 warmup / 10 iterations / 2 forks) so the error margins are
≤ 5%. Full tables, error bars, and the delta against March 2026 are in
[BENCHMARK-RESULTS.md](BENCHMARK-RESULTS.md).

### Primitive Generation

**NFR-1**: ≥ 10M ops/s.

| Type | Throughput | Target | Compliance |
|------|------------|--------|------------|
| Boolean | **252M ops/s** | 10M ops/s | ✅ 25× |
| Enum | **142M ops/s** | 10M ops/s | ✅ 14× |
| Integer | **63M ops/s** | 10M ops/s | ✅ 6.3× |
| Date (LocalDate) | **22M ops/s** | 10M ops/s | ✅ 2.2× |
| String (char) | **12.4M ops/s** | 10M ops/s | ✅ 1.2× |
| Timestamp | **4.5M ops/s** | 10M ops/s | ⚠️ 0.45× |
| Decimal (BigDecimal) | **4.3M ops/s** | 10M ops/s | ⚠️ 0.43× |

**`DateGenerator` now clears NFR-1** — 2.4M → 22M ops/s (9.1×). Cause: commit `137caba` (14 Jun 2026), which
caches the parsed min/max bounds per type. Previously *every* `generate()` call re-parsed the same two range
strings with `LocalDate.parse()`. The same commit cached Integer/Char bounds, but those barely moved
(1.1×/1.0×) — `Integer.parseInt` was already cheap, whereas `LocalDate.parse` dominated the date path.

Timestamp and Decimal remain below target (`Instant` / `BigDecimal` construction cost), but are still ~8×
above the fastest measured full-pipeline rate, so this is not a practical constraint.

### Realistic Data Generation (Datafaker)

| Type | Throughput | March 2026 | Change |
|------|------------|-----------:|-------:|
| Company names | **1.10M ops/s** | 154K | **7.1×** |
| Cities | **921K ops/s** | 14K | **64.7×** |
| Person names | **863K ops/s** | 23K | **37.2×** |
| Email addresses | **325K ops/s** | 24K | **13.5×** |
| Addresses | **261K ops/s** | 18K | **14.8×** |
| Phone numbers | **108K ops/s** | 13K | **8.5×** |

**Why the jump?** The thread-local `FakerCache` (`generators/.../semantic/FakerCache.java`, commit
`cf3492d`, 8 Mar 2026). Before it, a fresh `Faker` — and therefore a fresh locale dictionary load — was
constructed **for every field of every record**. The March benchmark run predates the commit. Primitives and
serializers are unchanged over the same period, which is the control confirming the effect is real.

### Regex Types

Config-declarable `regex:` types (`--faker-types`), backed by RgxGen. See
[Regex Types](#regex-types-1) below for guidance and [REGEX-E2E-RESULTS.md](REGEX-E2E-RESULTS.md) for the
pipeline cost.

| Pattern | Throughput |
|---------|-----------:|
| `ORD-\d{8}` (literal + fixed digits) | **5.14M ops/s** |
| `(INV\|CRN\|DBN)-[0-9]{6}` (alternation) | **4.46M ops/s** |
| `SEPA[0-9]{8}[A-Z0-9]{6}` | **2.91M ops/s** |
| `[A-Z0-9]{10,35}` (bounded class) | **1.85M ops/s** |
| IBAN-shaped | **1.66M ops/s** |
| `[a-z]+` (unbounded — capped at 100 reps) | **1.20M ops/s** |
| *Pattern compile (one-off, at load)* | *0.59 – 2.97 µs* |

### Composite Generators

| Type | Throughput | Notes |
|------|------------|-------|
| Small arrays (10 elements) | **6.0M ops/s** | Primitive elements |
| Simple objects (5 fields) | **5.7M ops/s** | Flat structure, primitive fields |
| Large arrays (100 elements) | **738K ops/s** | Primitive elements |

**Key insight**: Object overhead is minimal. Arrays of primitives are fast.

### Serialization

| Format | Throughput (simple) | Throughput (complex) | Throughput (nested) |
|--------|---------------------|----------------------|---------------------|
| **JSON** | **3.14M ops/s** | **1.07M ops/s** | **688K ops/s** |
| **CSV** | **2.58M ops/s** | **960K ops/s** | **240K ops/s** |
| **Protobuf** | **1.48M ops/s** | **569K ops/s** | **307K ops/s** |

**Observations**:
- **Protobuf is now measured, and it is the slowest serializer** — about 2× slower than JSON on simple
  records. Earlier revisions of this doc carried *estimates* of ~2.5M / ~900K / ~550K, which were
  optimistic. Protobuf still produces 50–70% smaller output than JSON; it simply costs more CPU to produce.
  Choose it for wire/storage size, not for speed.
- JSON and CSV are close for simple records; JSON is ~22% faster
- JSON handles nesting far better than CSV (688K vs 240K — 2.9×): CSV has no native nested representation,
  so it double-serializes (object→JSON string→CSV cell)
- Nested structures slow serialization ~3-4× across all formats

### File I/O

| Operation | Throughput | Notes |
|-----------|------------|-------|
| Raw BufferedWriter | **5.4M ops/s** | Baseline (no serialization) |
| FileDestination (optimized) | **962K ops/s** | Serialize + 64KB buffer + 1000-record batch |

**Optimizations applied** (March 2026):
- 64KB buffer size (up from 8KB)
- Batch writes (1000 records per batch)
- Eliminated redundant newLine() calls

**Result**: ⚠️ **NFR-1's 500 MB/s file target is NOT met on the reference hardware, and cannot be — the limit
is CPU, not I/O.** This document previously claimed "600-800 MB/s, exceeding the 500 MB/s target"; that figure
was a *projection* from the optimization plan, never a measurement. Measured 14 Jul 2026, 1M records:

| Path | Throughput | MB/s |
|------|-----------:|-----:|
| Full pipeline, 8 workers, 526-byte records | 610,500 rec/s | **306 MB/s** |
| Single writer thread ceiling (44-byte records) | 1,845,018 rec/s | (~930 MB/s at 526 B) |
| Disk, buffered no-fsync (the path we use) | — | **2,300 MB/s** |
| **NFR-1 target** | — | **500 MB/s** |

**Neither the disk nor the writer thread binds before 500 MB/s does.** Generation + Jackson serialization —
both already parallel, both already streaming (see [Custom & Regex Types](DESIGN.md)) — saturate the CPU
first. Per-record cost is ~5.7 µs single-threaded; 500 MB/s at 526 B/record needs ~996K rec/s, roughly
**1.6× more CPU than a 6-core 15 W mobile Ryzen has**.

> ⚠️ **`benchmarkRawFileWrite` is not a stable reference.** It measured 5.4M ops/s on 13 Jul and 3.7M on
> 14 Jul (**-28%**) on an idle-vs-thermally-loaded machine, with no code change in between. An earlier
> revision of this document quoted it as "1,261 MB/s" to four significant figures. Do not do that. It is a
> rough ceiling indicator, nothing more.

#### NFR-1 status: expected, not verified

We believe NFR-1 is achievable and we cannot prove it, because we do not own hardware that can reach it. That
is a hardware limit, not a design limit, and the numbers above locate it precisely:

- The **disk** sustains 1.0–1.2 GB/s and absorbs 2.3 GB/s buffered — 4.6× the target.
- The **single writer thread** tops out at ~1.85M rec/s ≈ 930 MB/s at 526 B — 1.9× the target.
- **CPU** delivers 306 MB/s on 6 cores. This is the binding constraint.

**Prediction (falsifiable):** at the measured per-core cost, NFR-1 should be met at roughly **10–12 physical
cores** of this class — fewer with faster desktop/server cores — and the architecture should not get in the
way until ~930 MB/s, where the writer thread becomes the next ceiling.

**If you have better hardware, please falsify this.** One command:

```bash
./gradlew :cli:installDist
./cli/build/install/cli/bin/cli execute \
    --job config/jobs/perf_probe_wide_file_json.yaml \
    --count 1000000 --threads $(nproc)
```

Read the `Time elapsed: … (N records/sec)` line. The record is 526 bytes, so
`MB/s = N × 526 / 1048576`. Open an issue with your core count, CPU model and the number — whether it
confirms the prediction or blows it up. Both are useful; the second is more useful.

#### Is 500 MB/s even reachable on this hardware?

Yes, with ~2× headroom. Measured 14 Jul 2026 on the reference laptop (SK Hynix HFS512GDE9X081N NVMe,
ext4 on `/dev/nvme0n1p2`):

| Disk test | Result |
|-----------|-------:|
| `O_DIRECT` sequential write, 1 MB blocks | 1.2 GB/s |
| Sustained 64 GB (SLC-cache exhaustion check) | **1.0–1.2 GB/s, flat — no cliff** |
| Buffered + `fdatasync` | 886 MB/s |
| **Buffered, no fsync — the path `FileDestination` uses** | **2.3 GB/s** |

`FileDestination` writes through a `BufferedWriter` with no per-record fsync, so records land in page cache
and the kernel drains them in the background. The relevant ceiling is therefore the **2.3 GB/s** page-cache
figure, with a 1.1 GB/s sustained background drain. Against that, the full pipeline (302 MB/s) uses 13% of
available write bandwidth and the NFR-1 target (500 MB/s) would use 22%.

Two consequences worth stating plainly:

- **The disk has never been the constraint.** A 100K-record run writes ~24 MB, which never leaves page cache.
  Every file-destination number in this document is CPU-bound, not I/O-bound.
- **Nor is the writer thread, yet.** It sustains ~1.85M rec/s (~930 MB/s at 526 B), well clear of the target.
  It only becomes the ceiling on hardware fast enough to meet NFR-1 in the first place — which is why the
  worker-side chunk coalescing landed (see CHANGELOG): it raises that ceiling for machines we cannot test.

Caveat: this is one drive on one laptop. A CI runner on a throttled cloud volume or spinning disk could
genuinely be disk-bound at 500 MB/s. The claim is "achievable on the reference hardware", not "disk never
matters". Small-block `O_DIRECT` does collapse (132 MB/s at 4K, 452 MB/s at 16K, 1.1 GB/s at 64K), but that
is a single-queue-depth artifact of `dd` — buffering means SeedStream hands the kernel large batches
regardless of record size, so it is not a constraint the generator can hit.

---

## Real-World Performance

### Test Case: 100K Datafaker Customer Records

**Configuration** (`config/structures/customer.yaml`):
```yaml
name: customer
geolocation: usa
data:
  customer_id:
    datatype: uuid
    alias: "id"
  first_name:
    datatype: first_name
  last_name:
    datatype: last_name
  email:
    datatype: email
  phone:
    datatype: phone_number
  billing_address:
    datatype: address
  city:
    datatype: city
  state:
    datatype: state
  postal_code:
    datatype: postal_code
  country:
    datatype: country
```

**Command**:
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format json --count 100000 --threads 4"
```

**Results** (100% Datafaker `customer` structure; figures predate the June 2026 E2E refresh — mixed/passport runs measure ~32–39K, see [E2E-TEST-RESULTS.md](E2E-TEST-RESULTS.md)):
- **Records Generated**: 100,000
- **Worker Threads**: 4
- **Time Elapsed**: ~3 seconds
- **Throughput**: **~25,000-33,000 records/sec**
- **Output File Size**: ~30 MB
- **Data Types**: UUID, names, emails, addresses, phone numbers, cities, states, postal codes (USA locale)

**Sample Output**:
```json
{
  "id": "ce344f82-baf2-4e17-b871-8808047a09c5",
  "first_name": "Valentine",
  "last_name": "Reynolds",
  "email": "sherman.king@gmail.com",
  "phone": "(256) 511-6029",
  "billing_address": "Suite 233 33062 Verlie Corners, East Berryberg, WI 35149",
  "city": "Mabletown",
  "state": "New Jersey",
  "postal_code": "16305",
  "country": "Kazakhstan"
}
```

### Scaling Analysis

#### File / Kafka Destinations

Measured with the **Passport** structure (11 fields, mixed Datafaker + primitives), refreshed by the **June 2026 E2E suite** — file ~32–39K rec/s, Kafka ~21–31K rec/s (see [E2E-TEST-RESULTS.md](E2E-TEST-RESULTS.md)):

| Records | Threads | Time | Throughput | Scaling |
|---------|---------|------|------------|---------|
| 100 | 1 | <0.5s | startup-dominated | Baseline |
| 1,000 | 1 | ~0.03s | ~35,000 rec/s | |
| 10,000 | 1 | ~0.3s | ~35,000 rec/s | Linear |
| 100,000 | 1 | ~3s | ~35,000 rec/s | Linear |
| 100,000 | 4 | ~3s | ~35,000 rec/s | Minimal gain (I/O bound) |
| 100,000 | 8 | ~3s | ~35,000 rec/s | I/O bound |

**Key Observations**:
1. **Single-threaded**: Linear scaling, ~35K rec/s after Faker cache optimization (was 7K before)
2. **Multi-threaded**: Marginal gains — I/O is now the bottleneck, not Datafaker CPU time
3. **100% Datafaker workloads** (e.g., customer): slightly lower (~20-25K rec/s); same threading behaviour

**Conclusion**: 4 threads is a safe default. Beyond that, output I/O (file or network) is the bottleneck regardless of thread count.

#### Database Destination

Database throughput is dominated by network round-trips and commit overhead rather than generation speed. Measured via JMH against a local PostgreSQL 17-alpine Docker instance (**re-run 14 July 2026**):

**Flat Insert (`benchmark_flat`, BIGSERIAL PK, 5 fields):**

| batchSize | `per_batch`    | `auto_commit`  |
|-----------|----------------|----------------|
| 100       | 56,165 ops/s   | 54,549 ops/s   |
| 500       | 75,560 ops/s   | 75,518 ops/s   |
| 1,000     | 80,154 ops/s   | 72,131 ops/s   |
| 5,000     | 83,519 ops/s   | 73,601 ops/s   |

**Nested Insert (`benchmark_order` + `order_items`, 1 parent + 2 children):**

| batchSize | `per_batch`    | `auto_commit`  |
|-----------|----------------|----------------|
| 100       | 1,974 ops/s    | 772 ops/s      |
| 500       | 2,344 ops/s    | 658 ops/s      |
| 1,000     | 1,934 ops/s    | 123 ops/s      |
| 5,000     | 2,895 ops/s    | 126 ops/s      |

**Key Observations**:
1. **Flat throughput is very high** under these micro-benchmark conditions: BIGSERIAL eliminates client-side ID generation, the same pre-built record is reused every call (no Datafaker overhead), and the Docker container uses shared memory sockets — not TCP — so network latency is effectively zero.
2. **Nested `auto_commit` is catastrophic** — and worse than the March figures suggested. At batch 1000–5000 it collapses to ~125 ops/s, up to **24× slower** than `per_batch`. Never use `auto_commit` for nested writes.
3. **Nested `per_batch`** scales modestly with `batchSize` (~1,900→2,900 ops/s): the 3× INSERT fanout is the binding constraint, not commit frequency.
4. **Flat vs Nested overhead factor**: ~29× (83K/2.9K) — far above the theoretical 3× INSERT fanout, confirming that decomposition overhead (FK injection, multi-table prepared-statement cache misses, parent-child dependency ordering) adds significant cost.
5. **Production throughput will be substantially lower**: real workloads with Datafaker generation, TCP connections to a remote DB, and non-trivial record variety typically yield 5,000–15,000 rec/s (flat) and 1,000–3,000 rec/s (nested). Use the JMH numbers to compare configurations relative to each other, not as absolute production targets.

⚠️ These DB benchmarks run at the default JMH config and their error margins are wide (batch=100/`per_batch`
reports 56,165 ops/s **± 119,838**). Trust the *ordering* and the `per_batch`-vs-`auto_commit` conclusion;
treat the absolute values as ±50%.

---

## Performance Tuning

### 1. Generator Selection

**Rule (revised, July 2026): pick the type that expresses your data. Generator choice is no longer a
throughput decision.**

Every generator family now runs far faster than any destination can drain:

| Family | Throughput | vs fastest destination (~39K rec/s) |
|--------|------------|-------------------------------------|
| Primitives | 4M – 252M ops/s | 100× – 6,000× headroom |
| Regex types | 1.2M – 5.1M ops/s | 30× – 130× headroom |
| Datafaker | 108K – 1.1M ops/s | 3× – 28× headroom |

Even the *slowest* generator (phone numbers, 108K ops/s) is ~3× faster than the quickest destination. Picking
primitives over Datafaker to "go faster" buys you nothing end-to-end — it only makes your test data less
realistic. Optimise the destination instead (see §3 and §4).

> This reverses the previous guidance in this document, which was written when Datafaker cost ~13–24K ops/s
> and genuinely *was* the bottleneck. The `FakerCache` (Mar 2026) removed that constraint.

**Where it still matters:** at 100% Datafaker across *many* fields, per-record generation cost can still
add up — a 10-field all-Datafaker record costs roughly 10 × ~1–10 µs. If you are chasing the last 10% on a
file destination, mixing in primitives for fields nobody inspects is still a legitimate micro-optimisation.
It is no longer a 1,000× decision.

### 2. Regex Types

Regex types (`regex:` in a `--faker-types` YAML) cost **less than a Datafaker name field** — 1.2M–5.1M ops/s.
End-to-end, a record with 4 of 10 fields as regex runs **~6% slower** than the same record with `char[]`
in those slots (see [REGEX-E2E-RESULTS.md](REGEX-E2E-RESULTS.md)). That is the whole price of structured,
pattern-conformant IDs.

**What actually costs, in order:**

1. **Output length.** Cost scales with the number of characters generated, not with how clever the pattern
   looks. Alternation is nearly free (4.5M ops/s).
2. **Unbounded quantifiers.** `+`, `*`, `{n,}` are capped by RgxGen at **100 repetitions**, so `[a-z]+` can
   emit a 100-character string — the slowest case measured (1.2M ops/s) and almost certainly not what you
   meant. **Always bound them**: `[a-z]{5,10}`.
3. **`.`** matches any printable ASCII including punctuation. Prefer explicit classes: `[A-Za-z0-9]`.

Pattern compilation (0.59–2.97 µs) happens **once**, at `--faker-types` load — it amortises after a handful
of records. Do not restructure patterns to make them "compile faster".

```yaml
# Good — bounded, explicit
types:
  order_ref: "regex:ORD-\\d{8}"
  msg_id: "regex:[A-Z0-9]{10,35}"

# Avoid — unbounded (100-char values) and `.` (punctuation in your IDs)
types:
  bad_ref: "regex:[a-z]+"
  bad_id: "regex:.{10}"
```

### 3. Mixed Workloads

```yaml
data:
  id: { datatype: int[1..999999] }
  name: { datatype: name }                           # Datafaker - realistic
  email: { datatype: email }                         # Datafaker - realistic
  order_ref: { datatype: order_ref }                 # regex - structured ID
  status: { datatype: enum[ACTIVE,INACTIVE] }
  created_at: { datatype: timestamp[now-365d..now] }
```

### 4. Thread Count

**The pipeline is parallel on the producer side and serial on the writer side.** Worker threads generate
*and* serialize records into bounded queues; **one writer thread** merges them in chunk order and calls the
destination. So threading only speeds up the parallel half, and Amdahl's law sets the ceiling:

```
Worker 0 → generate + serialize → queue[0] ─┐
Worker 1 → generate + serialize → queue[1] ─┼→ Writer (merge, ordered) → Destination
Worker N → generate + serialize → queue[N] ─┘        ^ serial
```

**Measured speedup (engine-only, 1M records, 8 threads vs 1):**

| Workload | 1 thread | 4 threads | 8 threads | Speedup | Why |
|----------|---------:|----------:|----------:|--------:|-----|
| Nested invoice → file | 51K rec/s | 143K rec/s | 185K rec/s | **3.6×** | Generation-heavy — lots of parallel work |
| Passport → file | 122K rec/s | 228K rec/s | 258K rec/s | **2.1×** | Less generation per record |
| Primitives → file | 732K rec/s | 1.52M rec/s | 1.54M rec/s | **2.1×** | Hits the writer ceiling (~1.5M rec/s) |
| Nested invoice → Kafka (snappy) | 46K rec/s | 72K rec/s | 77K rec/s | **1.7×** | See below |

**Rule**: the more expensive your record is to *generate*, the more threads pay.

> **Benchmark with 1M+ records.** At 100–200K, JVM startup and JIT warmup dominate and make scaling look
> far worse than it is — the same passport job measures 1.4× at 200K but 2.1× at 1M, and Kafka measures
> 1.3× at 200K but 1.7× at 1M. Short runs will mislead you (they misled an earlier revision of this doc).

#### Why Kafka scales worst

Kafka scales to only ~1.7× where the same structure to a file scales 3.6×. The reason is not the broker and
not the network — it is that `KafkaProducer.send()` **compresses the record into its batch buffer on the
calling thread**, and that call happens on the single writer thread. Compression is therefore serialized
onto one core, while the workers sit idle.

Measured, nested invoice → Kafka, engine-only, 1M records:

| Compression | 1 thread | 4 threads | 8 threads | Speedup |
|-------------|---------:|----------:|----------:|--------:|
| `snappy` | 46.0K rec/s | 71.6K rec/s | 76.7K rec/s | 1.67× |
| `none` | 47.1K rec/s | **103.5K rec/s** | 103.2K rec/s | **2.19×** |

**Dropping compression is +45% at 4 threads** (103.5K vs 71.6K) *and* it scales better. If your broker link
is local or fast, prefer `compression: none` and let the network carry the bytes. If you are
bandwidth-constrained, keep compression and accept a lower thread ceiling.

> **This is a fixable limitation, not a law.** `KafkaProducer` is thread-safe and explicitly designed to be
> shared across threads, and SeedStream's global record ordering is meaningless for Kafka anyway (records are
> sent with a null key, so the broker round-robins them across partitions). Letting workers call `send()`
> directly — instead of funnelling through the single writer — should recover most of the gap. Not yet done.

**Auto-optimization**: For jobs < 1000 records, engine automatically uses single-threaded mode (faster).

### 5. File I/O Optimization

**Current defaults** (optimized March 2026):
- Buffer size: **64KB** (internal, fixed)
- Batch size: **1000 records** (configurable via `conf.batch_size`)

**Tuning** (via job YAML `conf` block):
```yaml
conf:
  path: output/data.json
  compress: false      # gzip: 70-80% size reduction, 30-40% slower writes
  append: false
  batch_size: 1000     # records per flush (increase for throughput, decrease for memory pressure)
```

**Trade-offs**:
- **Larger batch_size**: Fewer flushes, better throughput, slightly higher peak memory
- **Compression (gzip)**: 70-80% smaller files, 30-40% slower writes
- **Append mode**: Slightly slower due to seek operations

### 6. Database Output Optimization

The three main knobs for database performance are **transaction strategy**, **batch size**, and **structure complexity**.

**Configuration** (via job YAML `conf` block):
```yaml
conf:
  url: jdbc:postgresql://localhost:5432/testdb
  username: ${DB_USER}
  password: ${DB_PASSWORD}
  table: customers              # optional — defaults to structure name
  batch_size: 500                   # records per batch INSERT (default: 100)
  transaction_strategy: per_batch   # per_batch | per_job | auto_commit
```

**Transaction strategies**:

| Strategy | Description | Throughput | Use Case |
|----------|-------------|------------|----------|
| `per_batch` | One transaction per batch (commit after each batch) | **Best** | Default recommendation |
| `per_job` | Single transaction for entire job (one commit at end, all-or-nothing) | Fast, but holds lock for the full run | Small jobs only |
| `auto_commit` | JDBC auto-commit (per-statement commit) | 2-3× slower | Resumable / observable jobs |

**Batch size guidelines**:

| Batch Size | Throughput | Memory | Notes |
|------------|------------|--------|-------|
| 100 (default) | Baseline | Low | Safe starting point |
| 500 | +30-50% | Medium | Recommended for most workloads |
| 1000+ | +50-70% | Higher | Only if DB server can handle large transactions |

**Nested structure considerations**:

Nested structures (e.g., `invoice → line_items`) produce multiple INSERTs per top-level record.
Each child record requires the parent's generated ID, so inserts are inherently sequential within a record tree:

```
1 invoice INSERT → get invoice.id
  N line_item INSERTs (with invoices_id = invoice.id)
    M line_item_attribute INSERTs (with line_items_id = line_item.id)
```

For nested structures, reduce `batch_size` to 100-200 to avoid large transactions with mixed table inserts.

**JDBC driver**: drivers are **not** bundled in the distribution. Drop the driver JAR for your database (PostgreSQL, MySQL, or any JDBC-compliant driver) into the `extras/` directory — the launch scripts prepend `extras/*` to the classpath at startup, so the driver registers via `DriverManager` automatically.

### 7. Format Selection

| Format | Speed | Size | Use Case |
|--------|-------|------|----------|
| **JSON** | Fast (~3.0M ops/s) | Medium | General purpose, nested structures, human-readable |
| **CSV** | Fast (~2.6M ops/s) | Smaller | Flat tabular data, spreadsheet import |
| **Protobuf** | Fast (~2.5M ops/s, est.) | Smallest (50-70% smaller) | High-volume, language-agnostic, binary format |

**Recommendation**: Use CSV for simple flat data, JSON for everything else.

### 8. Data Complexity

**Impact of nesting**:

| Structure | Throughput | Complexity |
|-----------|------------|------------|
| Flat (5 fields, primitives) | 100K+ ops/s | Low |
| Flat (5 fields, Datafaker) | 10-20K ops/s | Medium |
| Nested (2 levels, primitives) | 50K+ ops/s | Medium |
| Nested (2 levels, Datafaker) | 5-10K ops/s | High |
| Nested (3+ levels, Datafaker) | 1-5K ops/s | Very High |

**Recommendation**: Keep nesting to 2 levels max for good performance.

---

## Hardware Requirements

### Minimum Requirements

- **CPU**: 2 cores, 2 GHz
- **RAM**: 2 GB
- **Disk**: 10 MB/s sequential write (HDD)
- **Java**: 21 or higher

**Expected performance**: 1,000-5,000 Datafaker records/sec

### Recommended Configuration

- **CPU**: 8 cores, 3+ GHz (Intel i7, AMD Ryzen 7, Apple M1)
- **RAM**: 8 GB
- **Disk**: 200+ MB/s sequential write (SSD)
- **Java**: 21 with G1GC or ZGC

**Expected performance**:
- File/Kafka: 25,000-33,000 Datafaker records/sec (E2E validated)
- Database (localhost Docker, JMH micro-benchmark): 57,000-85,000 rec/s (flat), 2,500-3,300 rec/s (nested)
- Database (localhost, realistic Datafaker workload): 5,000-15,000 rec/s (flat), 1,000-3,000 rec/s (nested)

### High-Performance Configuration

- **CPU**: 16+ cores, 3.5+ GHz (Intel Xeon, AMD Threadripper, Apple M2 Pro)
- **RAM**: 16+ GB
- **Disk**: 500+ MB/s (NVMe SSD)
- **Java**: 21 with ZGC or Shenandoah GC

**Expected performance**:
- File/Kafka: 33,000-50,000 Datafaker records/sec (disk/network I/O bound)
- Database: throughput limited by DB server capacity and network, not SeedStream

### Memory Considerations

**Per-record memory usage**: ~100-120 bytes

| Records | Memory Usage | Notes |
|---------|--------------|-------|
| 1,000 | ~100 KB | Negligible |
| 10,000 | ~1 MB | Negligible |
| 100,000 | ~10 MB | Low |
| 1,000,000 | ~100 MB | Moderate |
| 10,000,000 | ~1 GB | Consider batching |

**Garbage collection**: < 2% overhead for all tested workloads (validated with JFR profiling).

For detailed memory profiling methodology, see [MEMORY-PROFILING.md](MEMORY-PROFILING.md).

---

## Running Benchmarks

### Quick Start

```bash
# Run all benchmarks (takes 10-15 minutes)
./benchmarks/run_benchmarks.sh
```

This generates `BENCHMARK-RESULTS.md` with formatted results.

### Manual Execution

```bash
# Run benchmarks
./gradlew :benchmarks:jmh

# Format results
python3 benchmarks/format_results.py > BENCHMARK-RESULTS.md

# View results
cat BENCHMARK-RESULTS.md
```

### Running a Subset

Use `-PjmhSuite`:

```bash
./gradlew :benchmarks:jmh -PjmhSuite=generators   # primitives, datafaker, composite, serializer, destination
./gradlew :benchmarks:jmh -PjmhSuite=regex        # regex types + Datafaker regexify comparison
./gradlew :benchmarks:jmh -PjmhSuite=kafka        # needs Kafka on localhost:9092
./gradlew :benchmarks:jmh -PjmhSuite=database     # needs PostgreSQL on localhost:5432
```

⚠️ **`-Pjmh.includes` and `-Pjmh.excludes` do not work.** With `me.champeau.jmh` 0.7.3 they are silently
ignored and the *entire* suite runs regardless — which, if you were expecting a filtered run, wastes hours
and re-measures things you didn't intend to touch.

Use `-PjmhSuite=database|kafka|generators|regex` for a named group, `-PjmhInclude=<regex>` for anything
finer (a single method), or `-PjmhExclude=<regex>` to skip a family. All three are applied inside
`benchmarks/build.gradle.kts` and do take effect.

### Benchmark Configuration

Defaults live in the `jmh { }` block of `benchmarks/build.gradle.kts` (2 warmup / 3 iterations / 1 fork).
**Don't edit them** — every historical number in [BENCHMARK-RESULTS.md](BENCHMARK-RESULTS.md) was measured
with those values, so changing them silently invalidates comparison against past runs.

For tighter error bars, use the high-fidelity flag instead (5 warmup / 10 iterations / 2 forks):

```bash
./gradlew :benchmarks:jmh -PjmhSuite=generators -PjmhFidelity=high
```

High-fidelity results are **not** comparable with default-config results — label them when publishing.

⚠️ Each run **overwrites** `benchmarks/build/reports/jmh/results.json`. Archive it before the next run
(see `benchmarks/results-2026-07-14/` for the layout).

For more details, see [benchmarks/README.md](../benchmarks/README.md).

---

## Troubleshooting Performance Issues

### Issue: Slower than expected throughput

**Diagnostics**:
1. Check data complexity: Are you using 100% Datafaker? That's expected to be slow.
2. Check thread count: `--verbose` flag shows worker activity.
3. Check disk I/O: Use `iostat` to monitor disk usage.

**Solutions**:
- Mix primitives with Datafaker (use primitives where realism doesn't matter)
- Reduce thread count if I/O bound
- Use SSD instead of HDD
- Disable compression (`compress: false`)

### Issue: High memory usage

**Diagnostics**:
1. Check batch size: Are you generating millions of records?
2. Monitor heap usage: Use `-Xmx` to limit heap.

**Solutions**:
- Process in batches (generate 100K at a time, not 10M)
- Reduce worker thread count
- Increase heap: `export JAVA_OPTS="-Xmx4g"`

### Issue: OOM errors

**Cause**: Usually unbounded queue in multi-threading engine.

**Solution**:
- Reduce thread count
- Reduce batch size in destination config
- Increase heap size

### Issue: Slow database inserts

**Diagnostics**:
1. Check transaction strategy: `auto_commit` is 2-3× slower than `per_batch`.
2. Check batch size: default (100) is conservative — try 500.
3. Check network: `localhost` vs remote DB makes a large throughput difference.
4. Check nested depth: 3-level nested structures produce many INSERTs per record.

**Solutions**:
- Switch to `transaction_strategy: per_batch`
- Increase `batch_size` to 500-1000
- For nested structures, target a simpler schema or reduce array cardinality

### Issue: Database connection refused / timeout

**Cause**: Database unreachable, or incorrect connection URL / credentials.

**Diagnostics**:
1. Confirm database is reachable: `psql -h host -U user -d db`
2. Check connection URL format: `jdbc:postgresql://host:5432/db`
3. Verify credentials are correctly set (env var substitution: `${DB_PASSWORD}`)

**Solution**:
- Verify `url`, `username`, `password` in job YAML (use `${ENV_VAR}` for secrets)
- Confirm the database user has INSERT privileges on the target tables

For more troubleshooting, see [README.md](../README.md#troubleshooting) or open a [GitHub Issue](https://github.com/mferretti/SeedStream/issues).

---

## Performance Roadmap

**Completed** (figures refreshed from the 14 July 2026 re-run — the March numbers below were stale by up to 65×):
- ✅ Primitive generators: 4–252M ops/s
- ✅ Datafaker integration: 108K–1.1M ops/s (was recorded as 13–154K; the thread-local `FakerCache` landed after that run)
- ✅ Regex types (`regex:` via RgxGen): 1.2–5.1M ops/s
- ✅ File I/O optimization: 25K-50K records/sec per format
- ✅ Kafka destination: 25K-33K records/sec (JSON/CSV/Protobuf)
- ✅ Protobuf serialization: 307K–1.5M ops/s, 50-70% smaller output — **the slowest format, ~2× behind JSON**, not the "~2.5M ops/s" previously estimated here
- ✅ E2E benchmarks: 54 tests across 2 destinations × 3 formats × 3 threads × 3 memory configs
- ✅ Database E2E benchmarking: invoice nested structure (invoices → issuer, recipient, line_items)
- ✅ Database JMH component benchmarks: flat (57K–85K ops/s) and nested (2.5K–3.3K ops/s), 16-configuration matrix (4 batch sizes × 2 transaction strategies)
- ✅ Serializer JMH component benchmarks: JSON, CSV, Protobuf × simple/complex/nested (`SerializerBenchmark`)

**Planned**:
- 📋 Distributed generation (external orchestrator assigning non-overlapping seeds and record ranges across multiple instances)
- 📋 GPU acceleration for primitives (experimental)

---

## Lessons Learned

Kept because this project has now paid for each of these twice.

1. **Verify how the library is actually used before blaming its performance.** The original analysis blamed
   Datafaker's YAML parsing. The real cause was *our* code defeating Datafaker's internal caching by
   constructing a new `Faker` per field. A thread-local cache produced a **7–65×** improvement with a trivial
   change — no library was at fault.

2. **Profile before optimising; a hot method is not automatically a bug.** JFR showed 98% of CPU inside
   Datafaker. That was normal operation, not a defect. The bug was the *call pattern* around it.

3. **Component benchmarks do not predict end-to-end throughput** — and worse, they can measure a code path
   the product never takes. `DestinationBenchmark` measures `FileDestination.write()` → the `streamWriter`
   path, but the CLI takes the *serialized-write* path for JSON. Every "223 MB/s" claim derived from it was
   describing dead code.

4. **Benchmark this engine with 1M+ records.** At 100–200K, fixed JVM + locale startup (~1.5 s) and JIT
   warmup dominate, understating throughput and *hiding thread scaling entirely*. Conclusions drawn at 100K
   have been overturned at 1M more than once.

5. **A number that swings run-to-run is not a number.** `benchmarkRawFileWrite` moved **-28%** between two
   runs with no code change, on thermals alone. It had been quoted in the docs to four significant figures.

6. **Never write a measurement you have not taken.** The "600–800 MB/s" file-I/O figure lived in the README,
   DESIGN.md and PERFORMANCE.md for months. It was an *Expected Result* from an optimisation plan that was
   never run. It then caused a real fix (Jackson streaming) to be deferred as "unnecessary — target already
   met", and later caused that same fix to be *re-proposed* by someone who trusted the doc instead of reading
   the code, by which time it had already shipped. Two wasted efforts from one unmeasured number.

---

**For architectural details on the multi-threading engine and reproducibility guarantees, see [DESIGN.md](DESIGN.md).**
