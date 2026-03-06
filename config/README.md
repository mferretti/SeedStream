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

**Output**: `cli/output/addresses.json`  
**Features**: Italian locale, field aliases, char/int ranges

---

### 2. Generate 10,000 USA Customers (Datafaker Integration)

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format csv --count 10000"
```

**Output**: `cli/output/customers.csv`  
**Features**: Realistic names, emails, addresses, phone numbers (USA locale)

---

### 3. Generate 500 Italian Invoices (Complex Nested Structures)

```bash
./gradlew :cli:run --args="execute --job config/jobs/file_invoice.yaml --format json --count 500"
```

**Output**: `cli/output/invoices.json`  
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
- **Types**: `uuid`, `timestamp[now-30d..now]`, `enum`, `ip_address`, `int[200..599]`
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
  path: cli/output/customers
  compress: false
  append: false
```

**Usage**:
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --format json --count 5000"
```

**Output**: `cli/output/customers.json`

---

#### Compressed File Output (gzip)
**File**: `file_order.yaml`
```yaml
source: order.yaml
type: file
seed:
  type: embedded
  value: 54321
conf:
  path: output/orders
  compress: true    # Enable gzip compression
  append: false
```

**Usage**:
```bash
./gradlew :cli:run --args="execute --job config/jobs/file_order.yaml --format json --count 10000"
```

**Output**: `output/orders.json.gz` (compressed)

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
  security_protocol: SASL_SSL
  sasl_mechanism: PLAIN
  sasl_username: ${KAFKA_USER}
  sasl_password: ${KAFKA_PASSWORD}
```

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

**API Response Example**:
```json
{
  "seed": 123456789
}
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

---

## Reproducibility

Same seed → identical output (bit-for-bit):

```bash
# First run
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --seed 12345 --count 1000"
sha256sum cli/output/customers.json

# Second run (identical output)
rm cli/output/customers.json
./gradlew :cli:run --args="execute --job config/jobs/file_customer.yaml --seed 12345 --count 1000"
sha256sum cli/output/customers.json  # Same hash!
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
