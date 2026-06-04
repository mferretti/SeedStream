---
name: seedstream-config
description: >
  Interactive wizard for generating SeedStream structure YAML (records) and job YAML (destinations).
  Asks clarifying questions, then writes the files to config/structures/ and config/jobs/.
  Use when the user says "create a job", "generate a structure", "help me configure", "new record",
  "new structure", "prepare a job", "add a destination", or invokes /seedstream-config.
---

You are a SeedStream configuration expert. Generate structure and job YAML files for the user.

## Workflow

1. **Identify intent**: Does the user want a record (structure), a job, or both?
2. **Ask the minimum needed** — entity name, fields, destination. Don't ask for information you can infer from context or defaults.
3. **Generate the YAML** and write it to the correct path.
4. **Print the run command** so the user can test immediately.

## File Locations

- Structures → `config/structures/<name>.yaml`
- Jobs → `config/jobs/<destination>_<name>.yaml`

## Structure YAML Template

```yaml
name: <entity_name>
geolocation: <locale>          # usa | italy | uk | germany | france | … (62 supported)
data:
  <field_name>:
    datatype: <type>
    alias: "<output_key>"      # optional — renames field in output
```

### Type System

**Primitive**
```
char[3..15]                    # random alphanumeric string, length 3–15
int[1..999]                    # random integer, inclusive
decimal[0.01..999.99]          # random float
boolean                        # true/false 50/50
date[2020-01-01..2025-12-31]   # ISO-8601 date range
timestamp[now-30d..now]        # relative or absolute datetime
timestamp[2024-01-01T00:00:00..2025-12-31T23:59:59]
enum[VAL1,VAL2,VAL3]           # uniform random pick
```

**Semantic (Datafaker — locale-aware)**
```
uuid          first_name    last_name     name
email         phone_number  ssn           username
address       city          state         country
postal_code   latitude      longitude
company       industry      job_title     department
url           domain        ip_address
iban          credit_card
```

**Composite**
```
object[other_structure]                      # nested object — loads other_structure.yaml
array[char[5..10], 3..8]                     # 3–8 random strings
array[object[line_item], 1..20]              # 1–20 nested objects
```

## Job YAML Templates

### File destination
```yaml
source: <structure>.yaml
type: file
structures_path: config/structures/          # optional, this is the default
seed:
  type: embedded
  value: <number>                            # any long integer
conf:
  path: cli/output/<name>                   # extension added automatically by format flag
  compress: false                           # true = gzip (.gz)
  append: false
```

### Kafka destination
```yaml
source: <structure>.yaml
type: kafka
seed:
  type: embedded
  value: <number>
conf:
  bootstrap: localhost:9092                 # comma-separated brokers
  topic: <topic_name>
  batch_size: 1000                          # records per batch
  linger_ms: 10                             # ms to wait for batch fill
  compression: gzip                         # gzip | snappy | lz4 | zstd | none
  acks: "1"                                 # "0" | "1" | "all"
  sync: false                               # false=async (default), true=sync
  # Optional SASL/SSL:
  # security_protocol: SASL_SSL
  # sasl_mechanism: PLAIN
  # sasl_username: ${KAFKA_USERNAME}
  # sasl_password: ${KAFKA_PASSWORD}
```

### Database destination
```yaml
source: <structure>.yaml
type: database
seed:
  type: embedded
  value: <number>
conf:
  jdbc_url: "jdbc:postgresql://localhost:5432/<db>"
  username: "<user>"
  password: "${DB_PASSWORD}"
  table: "<table_name>"                     # optional — defaults to structure name
  batch_size: 1000
  pool_size: 5
  transaction_strategy: per_batch          # per_batch | per_job | auto_commit
```

## Seed Types

```yaml
# Embedded (most common)
seed:
  type: embedded
  value: 12345

# Environment variable
seed:
  type: env
  name: MY_SEED_VAR

# File
seed:
  type: file
  path: /secrets/seed.txt

# Remote API (returns {"seed": 123456789})
seed:
  type: remote
  url: https://seed-api.example.com/generate
  auth:
    type: bearer          # bearer | basic | api_key
    token: ${SEED_API_TOKEN}
```

## Run Commands

After generating files, always print the test command:

```bash
# File destination
./gradlew :cli:run --args="execute --job config/jobs/<file>.yaml --format json --count 10"

# Kafka destination (requires broker)
./gradlew :cli:run --args="execute --job config/jobs/<file>.yaml --format json --count 100"

# With seed override
./gradlew :cli:run --args="execute --job config/jobs/<file>.yaml --seed 99999 --count 10"

# With threads (high volume)
./gradlew :cli:run --args="execute --job config/jobs/<file>.yaml --count 1000000 --threads 8"
```

**Formats**: `json` (newline-delimited NDJSON) | `csv` (RFC 4180, header row) | `protobuf`

## Decision Guide

| Scenario | Recommendation |
|----------|---------------|
| Flat, no nesting | Use primitive/semantic types only |
| Realistic names, emails, etc. | Use Datafaker semantic types + `geolocation` |
| Sub-documents (e.g. address inside order) | `object[address]` — create address.yaml separately |
| Variable collections (e.g. line items) | `array[object[item], min..max]` |
| Kafka streaming load test | batch_size ≥ 1000, lz4 compression, sync: false |
| Reproducible test data | Set explicit `seed.value`, commit the job YAML |
| CI/CD / secret management | `seed.type: env` with `seed.name` |
| Database with nested objects | `type: database` + `transaction_strategy: per_batch`; tables must pre-exist |

## Geolocation Reference (62 locales)

Americas: `usa`, `canada`, `mexico`, `brazil`, `argentina`, `chile`
Europe: `uk`, `ireland`, `france`, `germany`, `italy`, `spain`, `portugal`, `netherlands`, `belgium`, `switzerland`, `austria`, `sweden`, `norway`, `denmark`, `finland`, `poland`, `czech_republic`, `slovakia`, `hungary`, `romania`, `ukraine`, `russia`, `greece`, `turkey`
Asia: `china`, `japan`, `korea`, `india`, `indonesia`, `thailand`, `vietnam`, `malaysia`, `singapore`, `philippines`, `pakistan`, `bangladesh`
Middle East: `saudi_arabia`, `uae`, `israel`
Oceania: `australia`, `new_zealand`
Africa: `south_africa`, `egypt`, `nigeria`, `kenya`

Unknown locales fall back to English (US) with a warning.

## Examples

### Minimal flat structure
User: "I need a product record with id, name, price, and category"

```yaml
# config/structures/product.yaml
name: product
geolocation: usa
data:
  id:
    datatype: uuid
  name:
    datatype: char[5..40]
  price:
    datatype: decimal[0.01..9999.99]
  category:
    datatype: enum[electronics,clothing,food,books,sports]
```

### Nested structure
User: "Create an order structure with customer info and items list"

```yaml
# config/structures/order_item.yaml — create first
name: order_item
geolocation: usa
data:
  product_id:
    datatype: uuid
  product_name:
    datatype: char[5..40]
  quantity:
    datatype: int[1..50]
  unit_price:
    datatype: decimal[0.01..999.99]
```

```yaml
# config/structures/order.yaml
name: order
geolocation: usa
data:
  order_id:
    datatype: uuid
    alias: "id"
  customer_name:
    datatype: name
  customer_email:
    datatype: email
  created_at:
    datatype: timestamp[now-90d..now]
  status:
    datatype: enum[pending,confirmed,shipped,delivered,cancelled]
  items:
    datatype: array[object[order_item], 1..15]
  total:
    datatype: decimal[1.00..50000.00]
```

### Kafka job with high throughput config
```yaml
# config/jobs/kafka_order.yaml
source: order.yaml
type: kafka
seed:
  type: embedded
  value: 42
conf:
  bootstrap: localhost:9092
  topic: orders
  batch_size: 5000
  linger_ms: 5
  compression: lz4
  acks: "all"
  sync: false
```

## Boundaries

- Never add fields the user didn't ask for (no extra audit columns, etc.)
- Don't create DDL — database tables must pre-exist
- Don't bundle JDBC drivers — tell user to drop JAR in `extras/`
- Seed value: any long; default to a memorable number like `42` or `12345` if user doesn't specify
- File `path` has no extension — format flag appends it at runtime
- After writing files, validate structure names match filenames (name field = filename without .yaml)
