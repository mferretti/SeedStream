# SeedStream

> **Deterministic synthetic test data for Kafka, databases, and files — same seed, byte-for-byte identical output, every run.**

## ⚡ 60-Second Quickstart

No JDK, no Gradle, no clone — only Docker. The multi-arch image [`ghcr.io/mferretti/seedstream`](https://github.com/mferretti/SeedStream/pkgs/container/seedstream) ships on GitHub Container Registry and is pulled automatically on first run. Generate 1,000 deterministic invoices to `./out/`:

```bash
mkdir -p out
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -v "$PWD/out:/work/out" \
  ghcr.io/mferretti/seedstream:latest \
  execute --job config/jobs/quickstart.yaml --count 1000 --seed 42

head -n 1 out/invoices.json
```

The job config and sample structures are baked into the image, so the first run needs nothing but Docker. `-u "$(id -u):$(id -g)"` lets the (non-root) container write the output as you.

**See the determinism for yourself** — re-run with the same `--seed 42` and the hash never changes, across runs, machines, or thread counts:

```bash
sha256sum out/invoices.json
```

Streaming into Kafka or a database instead of a file? See the [container guide](docs/CONTAINER.md). Prefer a local build? Jump to [Other Ways to Run](#other-ways-to-run).

---

[![Build Status](https://github.com/mferretti/SeedStream/actions/workflows/build.yml/badge.svg)](https://github.com/mferretti/SeedStream/actions/workflows/build.yml)
[![Security Scan](https://github.com/mferretti/SeedStream/actions/workflows/security.yml/badge.svg)](https://github.com/mferretti/SeedStream/actions/workflows/security.yml)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/5ddc8a45a98c4ea4b5a8968152634f2f)](https://app.codacy.com/gh/mferretti/SeedStream/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Gradle](https://img.shields.io/badge/Gradle-9.5-brightgreen.svg)](https://gradle.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)
[![codecov](https://codecov.io/gh/mferretti/SeedStream/branch/main/graph/badge.svg)](https://codecov.io/gh/mferretti/SeedStream)

High-performance, seed-based test data generator for enterprise applications. Generate realistic, reproducible data to Kafka, databases, and files using simple YAML configuration.

## Contents

- [60-Second Quickstart](#-60-second-quickstart)
- [Features](#features)
- [How It Compares](#how-it-compares)
- [Proof of Determinism](#proof-of-determinism)
- [Requirements](#requirements)
- [Other Ways to Run](#other-ways-to-run)
- [Schema Inspection](#schema-inspection)
- [Performance](#performance)
- [Architecture](#architecture)
- [Documentation](#documentation)
- [Use Cases](#use-cases)
- [Secret Management](#secret-management)
- [Is This AI Slop?](#is-this-ai-slop)
- [Security](#security)
- [Contributing](#contributing)
- [License](#license)

---

## Features

- 🚀 **High Performance**: 4–252M records/sec for primitives, 108K–1.1M for realistic Datafaker data; ~32–39K rec/sec end-to-end through a real destination
- 🔄 **Reproducible**: Same seed → identical output, byte-for-byte, across machines and thread counts
- 🌍 **Locale-Aware**: locale-specific data via Datafaker (Italian names, US addresses, etc.) — pick a locale with `geolocation`; coverage comes from Datafaker
- 📝 **Multiple Formats**: JSON (NDJSON), CSV (RFC 4180), Protobuf (binary), Avro (OCF + Confluent Schema Registry wire format), CBEFF (biometric envelope)
- 💾 **Multiple Destinations**: File (NIO, gzip), Kafka (SASL/SSL, async/sync), JDBC databases (HikariCP, nested decomposition — integration-tested against Postgres, MySQL, Oracle, and SQL Server)
- 🔗 **Foreign Key References**: `ref[table.field, min..count]` — FK columns that scale automatically with `--count`
- ⚙️ **YAML Configuration**: Declarative structure and job definitions — no code required
- 🔌 **Extensible Type System**: 48+ Datafaker semantic types with runtime registration (`DatafakerRegistry`)
- 🔍 **Schema Inspection**: Bootstrap structure YAML from an existing OpenAPI 3.x spec, SQL DDL, or compiled Protobuf descriptor set — no hand-writing required
- 🔐 **Secret Management**: AES-256-GCM encrypted credentials in YAML; HashiCorp Vault, AWS Secrets Manager, Azure Key Vault, Google Secret Manager backends

---

## How It Compares

People comparison-shop, so here's an honest cut. The wedge is **determinism that survives parallelism** — same seed, byte-for-byte identical output regardless of thread or machine count — combined with streaming straight into infrastructure.

| | **SeedStream** | **Mockaroo** | **Synth** | **raw Datafaker** |
|---|:---:|:---:|:---:|:---:|
| Deterministic across **runs** (seed) | ✅ | ⚠️ | ✅ | ⚠️ DIY |
| Deterministic across **threads/machines** | ✅ | ❌ | ⚠️ | ❌ |
| Stream into **Kafka** | ✅ | ❌ | ❌ | ❌ |
| Stream into **databases** | ✅ | ⚠️ SQL export | ✅ | ❌ |
| **Foreign-key integrity** (`ref[]`) | ✅ | ⚠️ | ✅ | ❌ |
| **Scale / multi-threaded** | ✅ | ⚠️ hosted limits | ✅ | ⚠️ DIY |
| **No-code config** | ✅ YAML | ✅ GUI | ✅ schema | ❌ you write Java |
| **Bootstrap schema from existing source** | ✅ DDL / OpenAPI / Protobuf | ❌ | ⚠️ live DB only | ❌ |
| **Self-hosted / offline** | ✅ | ❌ SaaS | ✅ | ✅ |
| **Locales** | ✅ via Datafaker | ✅ many | ⚠️ limited | ✅ most (built on it) |
| **License** | Apache-2.0 | Proprietary (SaaS) | Apache-2.0 | Apache-2.0 |

**Where the others win — honestly:**

- **Mockaroo** — nothing to install; a polished GUI and a huge built-in type catalog make one-off sample files the fastest path. If you don't need reproducibility-under-parallelism or streaming into infra, it's hard to beat for quick mock data.
- **Synth** — a single Rust binary that connects to a **live database and samples its actual data distributions**. SeedStream also bootstraps schemas — its `inspect` subcommand generates structure YAML from a SQL DDL, an OpenAPI 3.x spec, or a compiled Protobuf descriptor — but it reads the *declared schema*, not a running system's data. Synth's edge is cloning the statistical shape of data you already have in a live DB.
- **raw Datafaker** — SeedStream is *built on* Datafaker. If you're inside a JVM app and want to call a faker in code (no YAML, no process), use Datafaker directly — it has the widest provider/locale breadth. SeedStream adds the determinism model, the YAML layer, FK integrity, scale, and the Kafka/DB/file destinations around it.

> Capability-level comparison as of mid-2026; tools evolve. Spot something out of date? [Open an issue](https://github.com/mferretti/SeedStream/issues) — corrections welcome.

---

## Proof of Determinism

Determinism is abstract until you watch two hashes match. One command generates the same dataset (seed `12345`) at **1, 4, and 8 threads** and compares the SHA-256:

```bash
./scripts/determinism-demo.sh
```

![Determinism demo: identical SHA-256 across 1, 4, and 8 threads](docs/assets/determinism.gif)

```text
==> Generating 5000 records with --seed 12345, three thread counts…
    threads=1  sha256=d87c2c641609dc72488ad9bb37af068f5b315b6890e39bc63d81654ef5fa2c44
    threads=4  sha256=d87c2c641609dc72488ad9bb37af068f5b315b6890e39bc63d81654ef5fa2c44
    threads=8  sha256=d87c2c641609dc72488ad9bb37af068f5b315b6890e39bc63d81654ef5fa2c44

✅ DETERMINISTIC — identical SHA-256 across 1, 4, and 8 threads.
```

The output byte-for-byte does not depend on thread count, core count, or machine — only the seed. This is the property that makes reproducible bug reports, golden-master pipeline tests, and "ship the recipe, not the data" collaboration possible. It's locked in CI by `GenerationEngineTest.shouldProduceIdenticalOrderedOutputRegardlessOfThreadCount`.

---

## Requirements

- **Java 21+** (Amazon Corretto, OpenJDK, or GraalVM)
- **Gradle 9.5+** wrapper included — no system install needed
- **Docker** (optional, for integration tests with Testcontainers)
- **JDBC driver** (optional, for database destination — drop into `extras/`)

---

## Other Ways to Run

Prefer not to use Docker? Run from a release JAR, the distribution zip, or source.

### Option 1 — Fat JAR (no build required)

Download the release JAR and run immediately. You still need the config files, so clone first:

```bash
git clone https://github.com/mferretti/SeedStream.git && cd SeedStream
wget https://github.com/mferretti/SeedStream/releases/latest/download/seedstream-latest.jar
java -jar seedstream-latest.jar execute --job config/jobs/file_address.yaml --count 100
```

### Option 2 — Distribution zip

```bash
wget https://github.com/mferretti/SeedStream/releases/latest/download/cli-latest.zip
unzip cli-latest.zip
# Point to your own job configs or clone the repo for examples
cli-*/bin/datagenerator execute --job /path/to/job.yaml --count 100
```

### Option 3 — Build from source

```bash
git clone https://github.com/mferretti/SeedStream.git && cd SeedStream
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --count 100"
```

### Common examples

```bash
# Generate 10,000 US customers as CSV
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format csv --count 10000"

# Stream 1M events to Kafka with 8 threads
./gradlew :cli:run --args="execute --job config/jobs/kafka_events_env_seed.yaml --count 1000000 --threads 8"

# Reproducible output — same seed, same data every time
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --seed 12345 --count 1000"

# Validate a configuration without running
./gradlew :cli:run --args="validate --job config/jobs/file_invoice.yaml"

# Encrypt a credential for embedding in job YAML
export SEEDSTREAM_ENCRYPTION_KEY=$(openssl rand -hex 32)
echo -n "my-db-password" | ./gradlew :cli:run --args="encrypt"
# Or interactively (value hidden at terminal):
./gradlew :cli:run --args="encrypt"
# Output already includes the AES256GCM: prefix, e.g.:  AES256GCM:BASE64CIPHERTEXT...
# Paste it verbatim into job YAML as: password: "${SECRET:enc:<output>}"
```

### CLI options

| Option | Default | Description |
|--------|---------|-------------|
| `--job` | required | Path to job YAML |
| `--format` | `json` | `json`, `csv`, `protobuf`, `avro`, `avro-registry`, `cbeff` |
| `--count` | `100` | Records to generate |
| `--seed` | from config | Override seed for this run |
| `--threads` | CPU cores | Worker threads |
| `--verbose` | off | Detailed logging |
| `--debug` | off | Enables sampled TRACE logging (see `--trace-sample`) |
| `--trace-sample` | `10` | TRACE sampling rate 1–100 (percentage); only effective with `--debug` |

---

## Schema Inspection

Already have a live database schema or an OpenAPI spec? `inspect` bootstraps SeedStream structure YAML files from it, so you don't need to write them by hand.

```bash
# From a SQL DDL file (auto-detected from .sql extension)
datagenerator inspect schema.sql --output config/structures/

# From an OpenAPI 3.x spec (auto-detected from .yaml / .json extension)
datagenerator inspect api.yaml --output config/structures/

# Overwrite any existing structure files
datagenerator inspect schema.sql --output config/structures/ --force
```

**What you get**: one `{snake_case_name}.yaml` per `CREATE TABLE` or OpenAPI schema object, written to the output directory and immediately usable in a job. For example, given:

```sql
CREATE TABLE customers (
  id       BIGINT,
  email    VARCHAR(100),
  city     VARCHAR(50),
  status   VARCHAR(10),
  balance  DECIMAL(10,2),
  joined   DATE
);
```

the inspector produces `config/structures/customers.yaml`:

```yaml
name: customers
data:
  id:
    datatype: int[1..999999]
  email:
    datatype: "email"  # guessed from column name — verify
  city:
    datatype: "city"  # guessed from column name — verify
  status:
    datatype: char[1..10]
  balance:
    datatype: decimal[0.0..9999.99]
  joined:
    datatype: date[2020-01-01..2030-12-31]
```

**Review comments** appear on fields where the inspector made a guess that a human should confirm:

| comment | what happened | action |
|---------|---------------|--------|
| `# guessed from column name — verify` | A Datafaker semantic type was inferred from the column name. | Keep it, change it, or replace with a `char[min..max]` range. |
| `# unrecognized source type, defaulted — verify` | SQL/OpenAPI type not recognized; fell back to `char[1..50]`. | Adjust the range or type. |

Fields with **no comment** were mapped from explicit schema information (declared SQL types, OpenAPI `format`, `enum`, numeric bounds) and don't need review. The CLI summary reports the total count: `inspect complete: 3 written, 0 skipped, 2 fields flagged for review (commented)`.

**Foreign keys → flat references by default**: each foreign key becomes a scalar `ref[parent_table.column, 1..count]` on the child structure, so every table maps to its own independent, joinable dataset that scales with `--count`. For `customer → invoice → invoice_item` that is three flat structures (`invoice.customer_id: ref[customer.id, 1..count]`, `invoice_item.invoice_id: ref[invoice.id, 1..count]`).

**Opt-in nesting** (`--nest`): the DDL inspector can invert `1:n` / `1:1` foreign keys into embedded documents — the same `customer → invoice → invoice_item` chain then emits a `customer` that carries `invoices: array[object[invoice], 1..10]` and an `invoice` that carries `invoice_items: array[object[invoice_item], 1..10]`:

```bash
datagenerator inspect schema.sql --nest --output config/structures/
# array multiplicity defaults to 1..10; override with --nest-default-count 2..5
```

`--nest` (= `--nest=auto`) keeps cycles, composite FKs, and M:N junction tables flat (with a warning); `--nest=all` errors on a true cycle instead. A `UNIQUE`/PK foreign key nests as `object[child]` (1:1). OpenAPI specs already declare their own nesting (`$ref` → `object[...]`, `array` of `$ref` → `array[object[...], min..max]`), so `--nest` is ignored for OpenAPI input. See [docs/INSPECT-NESTING-PLAN.md](docs/INSPECT-NESTING-PLAN.md) and spec §9.

**After inspection**: review and adjust any commented fields, then create a job YAML pointing at the output directory and run `execute` as normal.

### `inspect` flags

| Flag | Default | Description |
|------|---------|-------------|
| `<input>` | required | Schema file to inspect (`.sql`, `.yaml`, `.yml`, `.json`) |
| `--output` | `config/structures/` | Directory to write structure YAML files |
| `--force` | off | Overwrite existing structure files (default: skip and warn) |
| `--format openapi\|ddl\|protobuf` | auto-detect | Override format detection (by default inferred from extension) |
| `--faker-types <file>` | unset | YAML config of extra Datafaker types; register before inspection so name hints can target them |
| `--best-effort` | off | DDL only: emit the parseable subset and warn on tables that fail to parse, instead of aborting the whole inspection |
| `--nest[=auto\|all\|none]` | `none` | DDL only: invert `1:n`/`1:1` FKs into nested `array[object[child]]`/`object[child]`. `auto` keeps cycles/M:N/shared children flat; `all` errors on a true cycle |
| `--nest-default-count <min..max>` | `1..10` | DDL only: multiplicity for synthesized nested arrays when the schema gives no hint |

### Custom Datafaker types

The built-in name hints cover common fields (`email`, `city`, `first_name`, etc.). For domain-specific column names, register extra Datafaker providers with `--faker-types`:

```bash
datagenerator inspect schema.sql \
  --faker-types config/datafaker-types.example.yaml \
  --output config/structures/
```

The same `--faker-types` file must be passed to `execute` so those types resolve at generation time. See [config/datafaker-types.example.yaml](config/datafaker-types.example.yaml) for the format and [docs/INSPECT-V1-SPEC.md](docs/INSPECT-V1-SPEC.md) for the full type-mapping reference.

---

## Performance

Validated throughput — JMH component benchmarks plus the July 2026 end-to-end suite ([docs/E2E-TEST-RESULTS.md](docs/E2E-TEST-RESULTS.md)):

| Data type | Throughput |
|-----------|-----------|
| Primitive (int, boolean) | 4–252M records/sec |
| Regex types (`regex:` patterns) | 1.2–5.1M records/sec |
| Datafaker (names, emails, etc.) | 108K–1.1M records/sec |
| Real-world (10-field record, E2E) | ~32–39K records/sec |
| File I/O | 600–800 MB/s |

**Scaling**: worker threads parallelize generation and serialization; a single writer thread then drains to the
destination. Speedup therefore depends on how generation-heavy your structure is — a nested `invoice` scales
**2.3× on 8 threads** (file), a flatter `passport` **~1.4×**, and a primitives-only record not at all (it is
already writer-bound at ~530K rec/s). Kafka scales worst (**~1.3×**): record compression happens on the single
writer thread, so `compression: none` is **+66% faster at 4 threads** than `snappy`.

> The E2E table above measures a **whole CLI process**, which carries ~1.5s of fixed JVM + locale startup.
> For 100K-record runs that overhead dominates and hides the scaling. Engine-only throughput is much higher
> (passport ~106K rec/s, invoice→file ~90K rec/s). See [PERFORMANCE.md](docs/PERFORMANCE.md#thread-count).

See [PERFORMANCE.md](docs/PERFORMANCE.md) for full benchmarks, tuning guide, and hardware recommendations.

---

## Architecture

```
cli → destinations → formats → generators → schema → core
cli → inspector → schema → core
              (benchmarks: JMH harness, depends on core + generators)
```

Eight modules — seven in the runtime dependency chain (`inspector` powers the `inspect` subcommand) plus `benchmarks` (JMH micro-benchmarks, excluded from production artifacts). Each layer is pluggable: add a destination by implementing `DestinationAdapter`, a format by implementing `FormatSerializer`, or a new semantic type by registering it with `DatafakerRegistry`.

See [DESIGN.md](docs/DESIGN.md) for architecture decisions, the multi-threading reproducibility model, and extension points.

---

## Documentation

| Document | Contents |
|----------|----------|
| [config/README.md](config/README.md) | Type system reference, job/structure examples, Kafka & database config |
| [docs/INSPECT-V1-SPEC.md](docs/INSPECT-V1-SPEC.md) | `inspect` subcommand: type mapping tables, DDL/OpenAPI rules, review comment taxonomy |
| [docs/DESIGN.md](docs/DESIGN.md) | Architecture, threading model, reproducibility, extensibility |
| [docs/CONTAINER.md](docs/CONTAINER.md) | Running in Docker/Kubernetes/CI: image, `/work` layout, resource sizing, seed-then-test recipes |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | Benchmarks, tuning guide, hardware recommendations |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Common errors, debug mode, FAQ |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | Setup, development workflow, code standards |
| [docs/QUALITY.md](docs/QUALITY.md) | Coverage, SpotBugs, Spotless configuration |
| [CHANGELOG.md](CHANGELOG.md) | Release history and roadmap |

---

## Use Cases

Runnable, self-contained examples mapping SeedStream to a concrete business problem — each folder is
a "scenario README + config" unit you can copy, run in one command, and forward to a colleague.

| Use case | Persona | Status |
|----------|---------|--------|
| [DORA / GDPR resilience testing (ISO 20022 SEPA)](use-cases/dora-gdpr-sepa-payments/) | Regulated finance | **Ready** |
| Dev bootstrapping · CI DB seeding · load testing · SaaS demos | Various | *Planned* |

See [use-cases/](use-cases/) for the full index.

---

## Secret Management

Database passwords, Kafka credentials, and other secrets can be stored securely instead of in plaintext YAML.

### Option 1 — AES-256-GCM inline encryption

```bash
# Generate a key (store it safely — you need it to decrypt)
export SEEDSTREAM_ENCRYPTION_KEY=$(openssl rand -hex 32)

# Encrypt a credential — pipe via stdin (value not visible in ps or shell history)
echo -n "my-db-password" | ./seedstream encrypt
# Or run without argument to be prompted interactively (value hidden at terminal)
./seedstream encrypt
# → AES256GCM:BASE64CIPHERTEXT...
```

Paste the output into your job YAML:

```yaml
conf:
  password: "${SECRET:enc:AES256GCM:BASE64CIPHERTEXT...}"
```

### Option 2 — Environment variable substitution

```yaml
conf:
  password: "${ENV:DB_PASSWORD}"
```

### Option 3 — Cloud secret backends

```yaml
secrets:
  resolver: vault          # env | vault | aws | azure_keyvault | gcp_secretmanager | encrypted_file
  vault_addr: "https://vault.example.com"
  # Vault token is read from the VAULT_TOKEN environment variable
```

Supported backends: HashiCorp Vault (KV v1/v2), AWS Secrets Manager, Azure Key Vault, Google Secret Manager, encrypted file.

See [config/README.md](config/README.md) for full secret configuration reference.

---

## Is This AI Slop?

SeedStream was built with AI assistance — `CLAUDE.md` is in the repo and Claude Code is in the stack, openly. Fair question to ask of any such project. The answer is in the verification, not the prose:

- **~106 test classes** — 90 unit + 16 integration (Testcontainers for real Kafka, and Postgres/MySQL/Oracle/SQL Server over JDBC; 3 tagged `slow`), not smoke tests.
- **70% minimum line coverage**, enforced by a JaCoCo gate — the build fails below it.
- **Static analysis on every build** — SpotBugs (bug patterns) + Spotless (Google Java Style, build fails on drift).
- **OWASP Dependency-Check on every push** (CVSS ≥ 7.0). Every known CVE is listed below with status and an **expiry date** — no silent, permanent suppressions ([Security](#security)).
- **Benchmarked, not guessed** — JMH micro-benchmarks for hot paths plus an end-to-end throughput suite ([Performance](#performance), [benchmarks/](benchmarks/)).
- **CI you can read** — [build](.github/workflows/build.yml), [security](.github/workflows/security.yml), and [release](.github/workflows/release.yml) workflows run on every push/PR.

The determinism guarantee in particular is locked by a regression test that generates the same data across 1, 2, 3, 4, 8, and 16 threads and asserts byte-for-byte identical output. Claims here are testable — `./gradlew build` runs the lot.

---

## Security

SeedStream runs continuous OWASP Dependency-Check scans on every push (CVSS threshold ≥ 7.0).

**Known open issues (as of 2026-07-12):** none exploitable. One transitive fix applied; four
CPE false positives suppressed with a 2026-10-10 expiry.

The 2026-07-12 scan re-flagged the Azure Key Vault dependency chain (transitive via
`azure-security-keyvault-secrets`). Triage against upstream CVE records:

| CVE | Component | Resolution |
|-----|-----------|------------|
| CVE-2026-54428, CVE-2026-54399 | `httpcore5-h2` 5.4 | **Fixed** — forced to `5.4.3` (patched) in `build.gradle.kts` |
| CVE-2026-54428, CVE-2026-54399 | `httpcore` 4.4.16 (classic) | False positive — HTTP/2 issue in 5.x only; 4.x has no HTTP/2. Suppressed |
| CVE-2026-33117 | azure-core / identity / json | False positive — flaw is in keyvault-*keys* local crypto; we use keyvault-*secrets*. Suppressed |
| CVE-2023-36415, CVE-2024-35255 | azure-identity / msal4j | CPE false positives (confirmed 2026-07-07). Suppressed |

No permanent suppressions exist in this project — every entry in
`config/dependency-check-suppressions.xml` carries an `until` expiry that forces CI to re-fail and
trigger a fresh review.

To report a vulnerability, open a [GitHub issue](https://github.com/mferretti/SeedStream/issues) marked **security**.

---

## Example: `support_ticket` Domain

A ready-to-run example of a new domain. Structure: `config/structures/support_ticket.yaml` (uses primitives, `enum[...]`, `date[...]`, and a nested `object[...]`). Job: `config/jobs/file_support_ticket.yaml` (writes JSON to a file).

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_support_ticket.yaml --count 20"
```

Re-running with the same `--seed` produces byte-for-byte identical output.

## Contributing

Contributions welcome — bug reports, new generators, destinations, or formats.

```bash
git clone https://github.com/mferretti/SeedStream.git
cd SeedStream
./gradlew build test
```

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for setup, workflow, and code standards.

---

## License

Copyright 2024-2026 Marco Ferretti

Licensed under the [Apache License 2.0](LICENSE).
