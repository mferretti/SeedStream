# SeedStream Configuration Examples

This directory contains example data structures and job configurations demonstrating various features and use cases of SeedStream.

---

## Directory Structure

```
config/
├── structures/     # Data structure definitions
│   ├── Simple examples (primitives)
│   ├── Datafaker examples (realistic data)
│   └── Complex examples (nested objects, arrays)
└── jobs/           # Job configurations
    ├── File destination examples
    ├── Kafka destination examples
    └── Various seed type examples
```

---

## Quick Start Examples

### 1. Generate 1000 Italian Addresses (Simple Primitives)

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --format json --count 1000"
```

**Output**: `build/run-output/addresses.json`  
**Features**: Italian locale, field aliases, char/int ranges

---

### 2. Generate 10,000 USA Customers (Datafaker Integration)

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format csv --count 10000"
```

**Output**: `build/run-output/customers.csv`  
**Features**: Realistic names, emails, addresses, phone numbers (USA locale)

---

### 3. Generate 500 Italian Invoices (Complex Nested Structures)

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_invoice.yaml --format json --count 500"
```

**Output**: `build/run-output/invoices.json`  
**Features**: Nested company objects, arrays of line items (1-20 per invoice), Italian locale

---

### 4. Generate 100,000 Event Logs with Multi-Threading

```bash
./gradlew :cli:run --args="execute --job config/jobs/kafka_events_env_seed.yaml --format json --count 100000 --threads 8"
```

**Requirements**: Set environment variable `EVENT_SEED`  
**Output**: Kafka topic `application_events`  
**Features**: High-volume log generation, timestamp ranges, LZ4 compression

---

## Data Structure Examples

### Simple Primitives

#### `address.yaml` - Italian Addresses
- **Locale**: Italy (`geolocation: italy`)
- **Fields**: name, surname, street, city, postal code, province
- **Types**: `char[min..max]`, `int[min..max]`
- **Features**: Field aliases for Italian terminology (`nome`, `cognome`, `via`, `citta`, `cap`)
- **Use Case**: Testing address validation, localization

#### `passport.yaml` - USA Passports
- **Locale**: USA (`geolocation: usa`)
- **Fields**: passport number, names, dates, nationality, issuing authority
- **Types**: `char`, `date[start..end]`, `enum[M,F,X]`, Datafaker types
- **Features**: Date ranges, enum for gender inclusivity
- **Use Case**: Identity management, government data simulation

---

### Datafaker Integration (Realistic Data)

#### `customer.yaml` - USA Customers
- **Locale**: USA
- **Fields**: UUID, first/last name, email, phone, address, city, state, zip, country
- **Types**: `uuid`, `first_name`, `last_name`, `email`, `phone_number`, `address`, `city`, `state`, `postal_code`, `country`
- **Features**: All Datafaker semantic types, realistic data
- **Use Case**: E-commerce, CRM testing, customer data pipelines

#### `user.yaml` - User Accounts
- **Locale**: USA
- **Fields**: UUID, username, email, names, DOB, phone, timestamps, boolean, role enum, profile URL
- **Types**: `uuid`, `email`, `date`, `timestamp`, `boolean`, `enum`, `url`
- **Features**: Authentication data, user lifecycle timestamps
- **Use Case**: User management systems, authentication testing, analytics

#### `event_log.yaml` - Application Events
- **Locale**: USA
- **Fields**: Event ID, timestamp, type, user/session IDs, IP, user agent, URL, status, response time, country
- **Types**: `uuid`, `timestamp[now-30d..now]`, `enum`, `ipv4`, `int[200..599]`
- **Features**: Recent timestamp ranges (last 30 days), HTTP status codes, performance metrics
- **Use Case**: Log aggregation testing, analytics, monitoring systems

---

### Complex Nested Structures

#### `invoice.yaml` + `company.yaml` + `line_item.yaml` - Italian Invoices
- **Locale**: Italy
- **Nested Objects**: `issuer` and `recipient` are `object[company]`
- **Arrays**: `line_items` are `array[object[line_item], 1..20]` (variable length)
- **Fields**: Invoice number, dates, issuer/recipient companies, line items, totals with VAT
- **Features**: Multi-level nesting, Italian VAT rates (4%, 10%, 22%), business terminology
- **Use Case**: Accounting systems, invoice generation, B2B data

#### `order.yaml` + `order_item.yaml` - E-Commerce Orders
- **Locale**: USA
- **Nested Objects**: `order_item` products
- **Arrays**: `items` are `array[object[order_item], 1..15]`
- **Fields**: Order details, customer info, items, pricing, payment, shipping, tracking
- **Features**: Order lifecycle, multiple payment methods, product discounts
- **Use Case**: E-commerce platforms, order management, fulfillment systems

---

## Job Configuration Examples

### File Destinations

#### Basic File Output (JSON)
**File**: `file_customer.yaml`
```yaml
source: customer.yaml
type: file
seed:
  type: embedded
  value: 98765
conf:
  path: build/run-output/customers
  compress: false
  append: false
```

**Usage**:
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format json --count 5000"
```

**Output**: `build/run-output/customers.json`

---

#### Compressed File Output (gzip)

Set `compress: true` in the `conf` section to write gzip-compressed output. The `.gz` extension is appended automatically — no need to include it in the path.

Supported with **JSON** and **CSV** formats (Avro uses its own internal Deflate codec when `compress: true`).

**File**: `file_customer_gzip.yaml`
```yaml
source: customer.yaml
type: file
seed:
  type: embedded
  value: 98765
conf:
  path: build/run-output/customers
  compress: true              # Enable gzip compression (.gz added automatically)
  compress_mode: stream       # Optional: stream (default) or per_chunk
  append: false
```

**`compress_mode` (optional, default `stream`):**
- `stream` — single gzip stream on the writer thread; default behavior, unchanged from earlier versions.
- `per_chunk` — each chunk gzipped independently on workers, then concatenated as multi-member gzip; requires `compress: true` and an NDJSON-style format (JSON/NDJSON only — CSV/Avro not supported). Decompressed output is identical to `stream` mode; compressed `.gz` bytes differ. Parallel workers remove compression from the writer-thread bottleneck, at a cost of slightly lower compression ratio due to smaller per-member dictionaries.

**Usage**:
```bash
# stream mode (default)
./gradlew :cli:run --args="execute --job config/jobs/file_customer_gzip.yaml --format json --count 1000"
# per_chunk mode (parallel gzip)
./gradlew :cli:run --args="execute --job config/jobs/file_customer_gzip_parallel.yaml --format json --count 1000"
```

**Output**: `build/run-output/customers.json.gz` (valid gzip, decompresses to newline-delimited JSON — identical content in either mode)

See `config/jobs/file_order.yaml` for a gzip example with nested structures.

```bash
# Verify gzip output is valid and round-trips:
gzip -t build/run-output/customers.json.gz
zcat build/run-output/customers.json.gz | head -3
```

---

### Kafka Destinations

#### Basic Kafka (Localhost)
**File**: `kafka_address.yaml`
```yaml
source: address.yaml
type: kafka
seed:
  type: embedded
  value: 12345
conf:
  bootstrap: localhost:9092
  topic: addresses
  batch_size: 1000
  linger_ms: 10
  compression: gzip
  acks: "1"
  sync: false
```

**Usage**:
```bash
./gradlew :cli:run --args="execute --job config/jobs/kafka_address.yaml --format json --count 50000"
```

**Requirements**: Kafka broker running on `localhost:9092`

---

#### Kafka with SASL/SSL Authentication
**File**: `kafka_address_sasl.yaml`
```yaml
source: address.yaml
type: kafka
seed:
  type: embedded
  value: 99999
conf:
  bootstrap: kafka.example.com:9093
  topic: secure_addresses
  batch_size: 2000
  linger_ms: 5
  compression: snappy
  acks: "all"
  sync: false
  security_protocol: SASL_SSL       # required with username/password
  sasl_mechanism: SCRAM-SHA-512     # PLAIN | SCRAM-SHA-256 | SCRAM-SHA-512 (default PLAIN)
  username: ${KAFKA_USER}
  password: ${KAFKA_PASSWORD}
```

The `username`/`password` keys are the supported way to supply SASL credentials: SeedStream builds the `sasl.jaas.config` login-module string for the chosen `sasl_mechanism`, and both values honour `${VAR}` / `${SECRET:path}` substitution.

> **Do not put `${...}` inside a raw `sasl_jaas_config` block.** Substitution is whole-string only, so a placeholder embedded in a multi-line JAAS value is sent to the broker literally and never resolved — use the `username`/`password` keys instead. If you do supply an explicit `sasl_jaas_config`, it takes precedence and `username`/`password` are ignored (with a warning). `username`/`password` require `security_protocol` to be set (e.g. `SASL_SSL`); only `PLAIN` / `SCRAM-SHA-256` / `SCRAM-SHA-512` can be synthesized — for `GSSAPI` / `OAUTHBEARER` supply `sasl_jaas_config` yourself.

**Usage**:
```bash
export KAFKA_USER=myuser
export KAFKA_PASSWORD=mypassword
./gradlew :cli:run --args="execute --job config/jobs/kafka_address_sasl.yaml --count 100000"
```

**Features**: Enterprise security, high throughput (batch_size=2000), all acks for durability

---

#### High-Volume Event Streaming
**File**: `kafka_events_env_seed.yaml`
```yaml
source: event_log.yaml
type: kafka
seed:
  type: env
  name: EVENT_SEED
conf:
  bootstrap: localhost:9092
  topic: application_events
  batch_size: 5000    # High batch size for throughput
  linger_ms: 5        # Low latency
  compression: lz4    # Fast compression
  acks: "all"         # Durability
  sync: false         # Async for speed
```

**Usage**:
```bash
export EVENT_SEED=12345
./gradlew :cli:run --args="execute --job config/jobs/kafka_events_env_seed.yaml --count 1000000 --threads 10"
```

**Performance**: Optimized for high throughput (5K batch, LZ4 compression, async)

---

## Seed Configuration Examples

Seeds ensure reproducible data generation. Same seed = identical output.

### 1. Embedded Seed (Simple)
```yaml
seed:
  type: embedded
  value: 12345
```

**Most common**. Seed value directly in configuration.

---

### 2. File-Based Seed
**File**: `file_user_seed_from_file.yaml`
```yaml
seed:
  type: file
  path: /secrets/user-seed.txt
```

**Usage**:
```bash
echo "87654321" > /secrets/user-seed.txt
./gradlew :cli:run --args="execute --job config/jobs/file_user_seed_from_file.yaml --count 1000"
```

**Use Case**: Shared seed across team, seed rotation, external seed management

---

### 3. Environment Variable Seed
**File**: `kafka_events_env_seed.yaml`
```yaml
seed:
  type: env
  name: EVENT_SEED
```

**Usage**:
```bash
export EVENT_SEED=555555
./gradlew :cli:run --args="execute --job config/jobs/kafka_events_env_seed.yaml --count 50000"
```

**Use Case**: CI/CD pipelines, containerized environments, secret management

---

### 4. Remote API Seed
**File**: `file_address_remote_seed.yaml`
```yaml
seed:
  type: remote
  url: https://seed-api.example.com/generate
  auth:
    type: bearer
    token: ${SEED_API_TOKEN}
```

**Usage**:
```bash
export SEED_API_TOKEN=your-api-token
./gradlew :cli:run --args="execute --job config/jobs/file_address_remote_seed.yaml --count 5000"
```

**Use Case**: Centralized seed service, audit logging, dynamic seed rotation

**API Response Example**: the endpoint must return the seed as a bare integer in the response body (`text/plain`), **not** a JSON envelope. Surrounding whitespace is trimmed.
```
123456789
```

**Supported auth types**: `bearer`, `basic`, `api_key`

---

### 5. CLI Seed Override
Override any configured seed at runtime:

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --seed 99999 --count 1000"
```

**Use Case**: Quick testing, ad-hoc data generation

---

## Feature Demonstrations

### Locale-Specific Data

**Italian Example** (`address.yaml`):
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --count 100"
```

**Output**:
```json
{"nome":"Giovanni","cognome":"Rossi","via":"Via Roma","n.":42,"citta":"Milano","cap":"20100","provincia":"MI"}
```

**USA Example** (`customer.yaml`):
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --count 100"
```

**Output**:
```json
{"id":"ce344f82-baf2-4e17-b871-8808047a09c5","first_name":"Valentine","last_name":"Reynolds","email":"sherman.king@gmail.com",...}
```

---

### Variable-Length Arrays

**Invoice Line Items** (1-20 items per invoice):
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_invoice.yaml --count 10"
```

**Sample** (invoice with 5 line items):
```json
{
  "numero_fattura": 123456,
  "righe": [
    {"description":"Product A","quantity":2,"unit_price":19.99,"vat_rate":0.22},
    {"description":"Product B","quantity":1,"unit_price":49.99,"vat_rate":0.22},
    ...
  ]
}
```

---

### Multi-Threading Performance

Generate 10 million records with 12 worker threads:

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --count 10000000 --threads 12"
```

**Expected Throughput**: 5,000-10,000 records/sec (Datafaker types)  
**Memory**: ~100-120 bytes/record  
**Duration**: ~15-20 minutes

---

## Format Examples

### JSON (Newline-Delimited)
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format json --count 100"
```

**Output** (`customers.json`):
```json
{"id":"uuid1","first_name":"John",...}
{"id":"uuid2","first_name":"Jane",...}
```

**Features**: One JSON object per line, no array wrapper, streaming-friendly

---

### CSV (RFC 4180 Compliant)
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format csv --count 100"
```

**Output** (`customers.csv`):
```csv
"id","first_name","last_name","email",...
"uuid1","John","Doe","john@example.com",...
"uuid2","Jane","Smith","jane@example.com",...
```

**Features**: Header row, always-quoted fields, nested objects as JSON strings

**Formula-injection neutralization (CWE-1236)**: any **string** cell (header or value) whose first character is a spreadsheet formula trigger — `=`, `+`, `-`, `@`, TAB, or CR — is prefixed with a single quote (`'`) so Excel/LibreOffice/Sheets treat it as literal text rather than executing it. Typed numbers, dates and nested-JSON cells are not affected. This is always on and not currently configurable, so a downstream **non-spreadsheet** parser may see a leading `'` on such values (e.g. `-5` written as `'-5`) and should strip it if needed.

---

### Avro (OCF Container)
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format avro --count 100"
```

**Output** (`customers.avro`): Avro OCF binary file with embedded schema. Schema is derived automatically from the job structure — no `.avsc` file required.

---

### Avro + Confluent Schema Registry
```yaml
# In job YAML conf section:
conf:
  schema_registry_url: "http://localhost:8081"
  schema_registry_auth: "bearer"              # bearer | basic | (omit for none)
  schema_registry_token: "${SR_TOKEN}"
  # schema_registry_subject: "my-subject"     # optional; defaults to <topic>-value
```

```bash
./gradlew :cli:run --args="execute --job config/jobs/kafka_customer.yaml --format avro-registry --count 100"
```

**Output**: Confluent wire format — magic byte (0x00) + 4-byte schema ID + Avro binary payload. Schema is registered on first write and cached for subsequent records.

---

### Protobuf (Binary)
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format protobuf --count 100"
```

**Output** (`customers.protobuf`): base64-encoded protobuf, **one record per line** (NDJSON-friendly text, not raw/length-prefixed binary). Each serialized message is dynamically schema-generated, then base64-encoded; decode a line with `Base64.getDecoder().decode(line)` before parsing.

---

### CBEFF (Biometric Envelope)
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_fingerprint_fmr.yaml --format cbeff --count 10"
```

**Output** (`fingerprints.cbeff`): CBEFF-like JSON envelope wrapping biometric field data per ISO/IEC 19785. (The payload is JSON, but the file extension is `.cbeff`, not `.cbeff.json`.)

The BDB header format identifiers are configurable via the job `conf`:

| Key | Meaning | Default |
|-----|---------|---------|
| `cbeff_format_owner` | CBEFF format owner identifier written to the BDB header | `ISO/IEC-JTC1-SC37` |
| `cbeff_format_type` | CBEFF format type identifier written to the BDB header | `biometric-json` |

```yaml
conf:
  path: build/run-output/fingerprints
  cbeff_format_owner: "ACME-BIO"
  cbeff_format_type: "vendor-fmr"
```

---

## Reproducibility

Same seed → identical output (bit-for-bit):

```bash
# First run
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --seed 12345 --count 1000"
sha256sum build/run-output/customers.json

# Second run (identical output)
rm build/run-output/customers.json
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --seed 12345 --count 1000"
sha256sum build/run-output/customers.json  # Same hash!
```

**Guaranteed**: Even across JVM restarts, different machines, different thread counts.

---

## Real-World Use Cases

### 1. E-Commerce Load Testing
```bash
# Generate 1 million orders with 10 worker threads
./gradlew :cli:run --args="execute --job config/jobs/file_order.yaml --count 1000000 --threads 10"
```

**Scenario**: Test order processing pipeline, checkout performance, inventory updates

---

### 2. User Analytics Pipeline
```bash
# Stream 10M events to Kafka for analytics
export EVENT_SEED=54321
./gradlew :cli:run --args="execute --job config/jobs/kafka_events_env_seed.yaml --count 10000000 --threads 12"
```

**Scenario**: Test log ingestion, real-time analytics, dashboards, alerting

---

### 3. CRM Data Migration
```bash
# Generate customer data in CSV for import
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format csv --count 500000"
```

**Scenario**: Populate staging environment, test data migration scripts, user acceptance testing

---

### 4. Invoice Processing System
```bash
# Generate Italian invoices for accounting system
./gradlew :cli:run --args="execute --job config/jobs/file_invoice.yaml --count 50000"
```

**Scenario**: Test invoice parsing, VAT calculation, accounting rules, compliance

---

---

## Type System Reference

All types are specified in the `datatype` field of a structure definition.

### Primitive Types

```yaml
name:        { datatype: char[3..15] }        # Random string, 3–15 chars (a-zA-Z)
age:         { datatype: int[18..65] }         # Random integer, inclusive range
price:       { datatype: decimal[0.01..999.99] }
is_active:   { datatype: boolean }             # true or false (50/50)
birth_date:  { datatype: date[1960-01-01..2005-12-31] }   # ISO-8601
created_at:  { datatype: timestamp[now-365d..now] }        # relative or absolute
status:      { datatype: enum[PENDING,ACTIVE,CANCELLED] }  # uniform pick
```

### Semantic Types (Datafaker)

Generate realistic, locale-aware data. All types respect the `geolocation` field.

**Person & Identity**
```yaml
uuid:         { datatype: uuid }          # "ce344f82-baf2-4e17-b871-8808047a09c5"
name:         { datatype: name }          # "John Smith"
first_name:   { datatype: first_name }
last_name:    { datatype: last_name }
email:        { datatype: email }
phone_number: { datatype: phone_number }
ssn:          { datatype: ssn }           # US Social Security Number
```

**Location**
```yaml
address:     { datatype: address }
city:        { datatype: city }
state:       { datatype: state }
country:     { datatype: country }
postal_code: { datatype: postal_code }
latitude:    { datatype: latitude }       # alias: lat
longitude:   { datatype: longitude }      # aliases: lon, lng
```

**Business**
```yaml
company:    { datatype: company }
occupation: { datatype: occupation }
department: { datatype: department }
```

**Internet**
```yaml
url:        { datatype: url }
domain:     { datatype: domain }
ipv4:       { datatype: ipv4 }
ipv6:       { datatype: ipv6 }
username:   { datatype: username }
```

**Finance**
```yaml
iban:        { datatype: iban }           # locale-aware IBAN: geolocation country (italy → IT…)
random_iban: { datatype: random_iban }    # IBAN from any country worldwide (alias: random_locale_iban)
sepa_iban:   { datatype: sepa_iban }      # IBAN from a random SEPA-zone country
bic:         { datatype: bic }            # ISO 9362 bank identifier (alias: swift)
credit_card: { datatype: credit_card }
```

`iban` honours `geolocation` (like names/addresses). `random_iban` and `sepa_iban` are
locale-independent — use them for foreign or SEPA-wide counterparty accounts respectively.

**52 canonical types** with 33 aliases. The full inventory lives in [docs/DATAFAKER-COVERAGE.md](../docs/DATAFAKER-COVERAGE.md). Add more without code via `--faker-types <file>`:
- **Method chains** — any no-arg Datafaker chain, e.g. `beer_style: beer.style` → `faker.beer().style()`.
- **Regex patterns** — a `regex:`-prefixed value generates strings matching a regex. The pattern is
  checked once when the file loads (a broken pattern stops the run with a message naming the field),
  and output is reproducible under the seed. Common patterns:

  | You want… | Write | Example output |
  |---|---|---|
  | 10–35 upper-case letters/digits | `regex:[A-Z0-9]{10,35}` | `R7ZK9444J9` |
  | Fixed prefix + 8 digits | `regex:ORD-\d{8}` | `ORD-40021785` |
  | 3 letters, dash, 4 digits | `regex:[A-Z]{3}-\d{4}` | `ABC-8130` |
  | Lower-case hex, 12 chars | `regex:[0-9a-f]{12}` | `a1b2c3d4e5f6` |

  **Avoid these — they don't do what you'd expect:**
  - **`.`** produces *any* printable character, including spaces and punctuation (`@`, `/`, `~`…).
    For "any letter or digit" write `[A-Za-z0-9]`, not `.`.
  - **Open-ended repeats** (`+`, `*`, or `{5,}` with no upper bound) stop at 100 characters. Always
    give an upper bound, e.g. `{5,20}`.
  - **Look-ahead/look-behind** (`(?=…)`) and **`\p{…}`** categories are not fully supported — stick
    to plain character classes and bounded counts.

  (Patterns are generated by [RgxGen](https://github.com/curious-odd-man/RgxGen).)

Only other parameterized providers (ranges, `options`, bounded dates) or non-String formatting still
need a Java lambda via `DatafakerRegistry.register`.
See [generators module](../generators/) for the complete list.

### Composite Types

```yaml
# Nested object — references another structure file
billing_address: { datatype: object[address] }

# Variable-length array
tags:       { datatype: "array[char[5..10], 3..8]" }    # 3–8 strings
line_items: { datatype: "array[object[line_item], 1..20]" }  # 1–20 nested objects
```

Referenced structure files are loaded from `structures_path`. Resolution order:
1. If the job sets `structures_path`, that value is used as-is (absolute, or relative to the current working directory).
2. Otherwise, if the job file lives in a directory named `jobs/`, structures are loaded from its sibling `structures/` directory — i.e. `<parent-of-jobs>/structures/`. This is why the shipped `config/jobs/*.yaml` resolve against `config/structures/` with no explicit key.
3. Otherwise, it falls back to the literal path `config/structures/` (relative to the working directory).

### Foreign Key References

Sample a random ID from a declared pool — no DB round-trips, no memory overhead.

```yaml
# Static pool: customer_id is always in [1..50]
customer_id: { datatype: ref[customer.id, 1..50] }

# Dynamic pool: max resolves to the job's --count at runtime
# Run parent and child jobs with the same --count to maintain FK consistency
customer_id: { datatype: ref[customer.id, 1..count] }
order_id:    { datatype: ref[order.id, 1..count] }
```

**`count` keyword**: `ref[s.f, 1..count]` — the `count` placeholder expands to the value of `--count` passed to the CLI (or configured in the job). Use it so FK ranges scale automatically without editing YAML when the count changes.

**Limitation**: `count` refers to the *current* job's count. For a parent→child FK chain, run both jobs with the same `--count`, or use a static range derived from the parent job's count.

**Parent reference (`ref[parent.<field>]`)**: copies the value of `<field>` from the **immediately enclosing parent record** (as opposed to `ref[<struct>.<field>, a..b]`, which samples a random id from *another* structure's pool). No range spec — it propagates the parent's actual generated value, guaranteeing referential integrity in nested trees.

- Valid **only** inside a nested `object[...]` or `array[object[...], ...]` field; used at the top level it fails fast (no parent record on the stack).
- The referenced field must be declared on the parent **before** the nested field that references it, so it is already present when the child is generated.
- `<field>` is a plain field name (lower-case / underscores); no range is accepted.

```yaml
# book.yaml — chapters carry their parent book's id
title:    { datatype: title }
chapters:
  datatype: "array[object[chapter], 1..10]"
# chapter.yaml
book_id:  { datatype: ref[parent.id] }   # copies the enclosing book's id
name:     { datatype: title }
```

### Field Aliases

```yaml
name: address
geolocation: italy
data:
  name:        { datatype: char[3..15], alias: "nome" }
  city:        { datatype: char[3..40], alias: "citta" }
  postal_code: { datatype: int[10000..99999], alias: "cap" }
```

Output: `{"nome": "Mario", "citta": "Milano", "cap": "20100"}`

### Geolocation & Locales

Set `geolocation` at the structure level:

```yaml
name: customer
geolocation: usa   # drives Datafaker locale for all semantic types
```

**Recognized `geolocation` names** (each maps to a Datafaker locale; underscores, spaces and hyphens are interchangeable, e.g. `saudi_arabia` = `saudi arabia` = `saudi-arabia`. Locale data itself is provided by Datafaker):
- **Americas**: `usa`, `canada`, `mexico`, `brazil`, `argentina`, `chile`, `colombia`, `peru`
- **Europe**: `uk`, `ireland`, `france`, `germany`, `italy`, `spain`, `portugal`, `netherlands`, `belgium`, `switzerland`, `austria`, `sweden`, `norway`, `denmark`, `finland`, `poland`, `czech_republic`, `slovakia`, `hungary`, `romania`, `ukraine`, `russia`, `greece`, `turkey`
- **Asia**: `china`, `taiwan`, `japan`, `korea`, `india`, `indonesia`, `thailand`, `vietnam`, `malaysia`, `singapore`, `philippines`, `pakistan`
- **Middle East**: `saudi_arabia`, `uae`, `israel`
- **Oceania**: `australia`, `new_zealand`
- **Africa**: `south_africa`, `egypt`, `nigeria`

An unset (or blank) `geolocation` defaults to US English. A non-blank value that is **not** in this list is **rejected with an error** (`GeneratorException`) rather than silently falling back to US English — a typo or an unsupported locale fails the job instead of quietly producing US data.

---

## Kafka Destination Reference

**Status**: ✅ Fully implemented (44 integration tests).

```yaml
source: address.yaml
type: kafka
seed:
  type: embedded
  value: 12345
conf:
  bootstrap: localhost:9092        # broker(s), comma-separated
  topic: addresses
  batch_size: 16384                # producer batch.size in BYTES, Kafka semantics (default: 16384)
  linger_ms: 10                    # wait time for batching (default: 10)
  compression: gzip                # gzip | snappy | lz4 | zstd | none
  acks: "all"                      # "0" | "1" | "all"
  sync: false                      # false=async (default), true=sync
  max_retries: 3                   # send retries in sync mode (default: 3)
  retry_delay_ms: 1000             # initial backoff between retries, ms; doubles each retry (default: 1000)
  # SASL/SSL authentication (optional):
  sasl_mechanism: PLAIN            # PLAIN | SCRAM-SHA-256 | SCRAM-SHA-512
  security_protocol: SASL_SSL      # PLAINTEXT | SSL | SASL_PLAINTEXT | SASL_SSL
  username: ${KAFKA_USERNAME}
  password: ${KAFKA_PASSWORD}
  # TLS keystore/truststore (optional; passwords support ${VAR} / ${SECRET:path}):
  ssl_truststore_location: /etc/kafka/truststore.jks
  ssl_truststore_password: ${KAFKA_TRUSTSTORE_PASSWORD}
  ssl_keystore_location: /etc/kafka/keystore.jks     # only for mTLS client auth
  ssl_keystore_password: ${KAFKA_KEYSTORE_PASSWORD}
```

- **`batch_size`** is the Kafka producer `batch.size` in **bytes** (default `16384` = 16 KB), *not* a record count. Do not confuse it with the database destination's `batch_size`, which is a record count.
- **`max_retries` / `retry_delay_ms`** apply in **sync** mode (`sync: true`): up to `max_retries` resends (default 3) with an exponential backoff starting at `retry_delay_ms` (default 1000 ms, doubling each attempt).
- **`username` / `password`** supply SASL credentials: SeedStream synthesizes the `sasl.jaas.config` login module for the chosen `sasl_mechanism` (`PLAIN` / `SCRAM-SHA-256` / `SCRAM-SHA-512`; default `PLAIN`). Both require `security_protocol` to be set and honour `${VAR}` / `${SECRET:path}` substitution. An explicit `sasl_jaas_config` overrides them (with a warning); do **not** embed `${...}` inside a raw `sasl_jaas_config` block — it is not interpolated. `GSSAPI` / `OAUTHBEARER` need an explicit `sasl_jaas_config`.
- **`ssl_*`** keys configure the TLS truststore (server verification) and keystore (mTLS client auth). Passwords accept `${VAR}` env and `${SECRET:path}` substitution. See `config/jobs/kafka_address_sasl.yaml` for a worked SASL_SSL example.

Features: async/sync modes, gzip/snappy/lz4/zstd compression, SASL/SSL auth, idempotent producer (`acks=all`), configurable batching, sync-mode retry backoff.

---

## Database Destination Reference

**Status**: ✅ Fully implemented — Stage 1 (flat tables) and Stage 2 (nested auto-decomposition).

```yaml
source: passport.yaml
type: database
seed:
  type: embedded
  value: 42
conf:
  jdbc_url: "jdbc:postgresql://localhost:5432/testdb"
  username: "dbuser"
  password: "${DB_PASSWORD}"   # env var, ${SECRET:enc:AES256GCM:...}, or cloud backend
  table: "passports"               # optional — defaults to structure name
  batch_size: 1000                 # records per batch/transaction (default: 1000)
  pool_size: 5                     # HikariCP pool size (default: 5)
  transaction_strategy: per_batch  # per_batch | per_job | auto_commit
  max_retries: 3                   # connection attempts during open() (default: 3; 1 disables retries)
  retry_delay_ms: 1000             # initial backoff between connection retries, ms; doubles each retry (default: 1000)
  inject_parent_fk: true           # auto-inject {parent_table}_id FK into child records (default: true)
  truncate_before_insert: false    # ⚠️ DESTRUCTIVE — see "CI seeding" below
  restart_identity: false          # also reset IDENTITY/SERIAL sequences (PostgreSQL)
```

- **`max_retries` / `retry_delay_ms`** here govern **connection** attempts during `open()` (exponential backoff from `retry_delay_ms`, doubling). Set `max_retries: 1` to disable retries.
- **`inject_parent_fk`** (default `true`): the nested-record decomposer injects a `{parent_table}_id` FK column into each child record. Set to `false` when child structures already populate the FK themselves via `ref[parent.<field>]`, to avoid a redundant second column.

**CI seeding (`truncate_before_insert`)**: Set to `true` to empty each target table with `TRUNCATE TABLE ... CASCADE` before its first insert. Combined with a fixed `seed`, one `execute` gives a clean, deterministic dataset per run — no external teardown script. Defaults to `false`. Notes:
- ⚠️ **Destructive** — wipes the table (and, via `CASCADE`, its FK dependents). Use only against a disposable/CI database.
- Tables must already exist (no DDL). CASCADE clears nested child tables in one shot, so FK graphs reseed cleanly.
- PostgreSQL/Oracle only — `CASCADE` is not valid MySQL/SQL Server `TRUNCATE` syntax.

**Dense ids across reseeds (`restart_identity`)**: Plain `TRUNCATE` leaves sequences at their high-water mark, so a reseeded table numbers its rows `N+1..2N`. Set `restart_identity: true` to emit `TRUNCATE TABLE ... RESTART IDENTITY CASCADE` instead, restarting identity/serial columns at 1 on every run. Required whenever child structures reference a parent pool with a static `ref[parent.id, 1..N]` — without it the second run's FK inserts fail. Defaults to `false`. Notes:
- PostgreSQL only — `RESTART IDENTITY` is not valid Oracle `TRUNCATE` syntax.
- Requires `truncate_before_insert: true`; the job is rejected at startup otherwise rather than silently ignoring the key.
- Worked example: [`use-cases/ci-pipeline-seeding/`](../use-cases/ci-pipeline-seeding/README.md).

**Nested structures (Stage 2)**: When a structure contains `object[X]` or `array[object[X]]` fields, SeedStream automatically decomposes the tree into multi-table INSERTs. The parent is inserted first; each child gets a `{parent_table}_id` FK column injected automatically.

```yaml
# invoice.yaml has: issuer: object[company], line_items: array[object[line_item], 1..20]
# Each child table is named after the parent FIELD KEY (not the object type), and gets a
# {parent_table}_id FK. SeedStream inserts into: invoices → issuer → line_items
# (a field `recipient: object[company]` would likewise insert into a `recipient` table)
source: invoice.yaml
type: database
conf:
  jdbc_url: "jdbc:postgresql://localhost:5432/testdb"
  table: "invoices"
  transaction_strategy: per_batch
```

**JDBC drivers**: Not bundled. Drop the driver JAR into the `extras/` directory — it is automatically added to the classpath at startup.

Constraints:
- ⚠️ Tables must pre-exist — no DDL generation
- ⚠️ Parent `id` field required for FK injection in nested structures

---

## Secret Management Reference

Secrets in job YAML are resolved before execution. Three mechanisms are supported.

### 1. Environment Variable Substitution

```yaml
conf:
  password: "${DB_PASSWORD}"
  token: "${KAFKA_SASL_TOKEN}"
```

### 2. AES-256-GCM Inline Encryption

Generate a key and encrypt credentials with the `encrypt` CLI command:

```bash
export SEEDSTREAM_ENCRYPTION_KEY=$(openssl rand -hex 32)
# Pipe via stdin — value not visible in ps or shell history:
echo -n "my-db-password" | ./seedstream encrypt
# Or run without argument to be prompted interactively (value hidden at terminal):
./seedstream encrypt
# → AES256GCM:BASE64ENCODED...
```

Reference in YAML:

```yaml
conf:
  password: "${SECRET:enc:AES256GCM:BASE64ENCODED...}"
```

The key is loaded at runtime from `SEEDSTREAM_ENCRYPTION_KEY` (default) or `--key-file /path/to/key.hex`.

### 3. Cloud Secret Backends

Add a `secrets` block to the job YAML to pull secrets from a remote store:

```yaml
secrets:
  resolver: vault                       # env | vault | aws | azure_keyvault | gcp_secretmanager | encrypted_file
  vault_addr: "https://vault.example.com"
  # Vault token is read from the VAULT_TOKEN environment variable

conf:
  password: "${SECRET:secret/db#password}"   # Vault path + optional #field
```

**HashiCorp Vault** (KV v1 and v2 auto-detected):
```yaml
secrets:
  resolver: vault
  vault_addr: "https://vault.example.com"
  vault_namespace: "admin"   # optional (Vault Enterprise / HCP)
  # Vault token is read from the VAULT_TOKEN environment variable
```

**AWS Secrets Manager**:
```yaml
secrets:
  resolver: aws
  aws_region: "us-east-1"
  # Uses default AWS credential chain (env vars, instance profile, ~/.aws/credentials)
```

**Azure Key Vault**:
```yaml
secrets:
  resolver: azure_keyvault
  vault_uri: "https://my-vault.vault.azure.net"
  # Uses DefaultAzureCredential (env vars, managed identity, CLI login)
```

**Google Secret Manager**:
```yaml
secrets:
  resolver: gcp_secretmanager
  gcp_project_id: "my-gcp-project"
  # Uses Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS, metadata server, gcloud login)
```

**Encrypted file** (AES-256-GCM): decrypts inline `${SECRET:enc:AES256GCM:...}` ciphertext using a key loaded from an env var or file. No separate secrets file — the ciphertext lives in the job YAML.
```yaml
secrets:
  resolver: encrypted_file
  key_env: "SEEDSTREAM_ENCRYPTION_KEY"   # default; or use key_file: /path/to/key.hex
```

> **Key-file permissions:** when using `key_file` (or `encrypt --key-file`), the key file must be owner-only — SeedStream fails fast if it is group- or world-readable. Restrict it with `chmod 600 /path/to/key.hex`.

---

## Troubleshooting

### Issue: "Structure file not found"
**Solution**: Check `source` path in job YAML. Structures are loaded from `config/structures/` by default.

### Issue: Kafka connection refused
**Solution**: Verify Kafka broker is running: `docker ps | grep kafka` or `telnet localhost 9092`

### Issue: "No GeneratorContext active"
**Solution**: Bug fixed in TASK-020. Update to latest version.

### Issue: Different output with same seed
**Solution**: Verify identical:
- Job configuration
- Structure definition
- SeedStream version
- Record count

---

## Contributing

To add new examples:

1. Create structure YAML in `config/structures/`
2. Create job YAML in `config/jobs/`
3. Test: `./gradlew :cli:run --args="execute --job config/jobs/your_job.yaml --count 10"`
4. Update this README with example usage
5. Submit PR

---

## License

All examples are licensed under Apache License 2.0. Feel free to use, modify, and distribute for your projects.
