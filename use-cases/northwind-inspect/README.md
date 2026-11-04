# Northwind at scale: from a famous schema to millions of rows

**Persona:** anyone evaluating SeedStream who wants to see it on a schema they already know.
**Outcome:** the classic Microsoft **Northwind** database (PostgreSQL port), bootstrapped with
`inspect` and seeded to **3.7 million rows** (1M orders, 2.6M order lines) into PostgreSQL with
every primary-key and foreign-key constraint active, or written to JSON/CSV files. The output is
byte-for-byte identical on every run.

This use case is also an honest test of the inspector. It was built by feeding the unmodified
Northwind schema to `inspect` and loading the result, then fixing whatever broke. Every hand edit
needed to get a working load exposed a gap, and every gap became a GitHub issue. The
[edit list](#3-hand-edits-32-of-92-fields) and the [issue map](#how-the-edits-produced-the-issues)
below show which edit came from which issue. When those issues are fixed, the edits can go.

## Quick start

```bash
cd use-cases/northwind-inspect

# 1. A disposable PostgreSQL (any 13+ works)
docker run -d --name northwind-pg -p 127.0.0.1:5432:5432 \
  -e POSTGRES_USER=northwind -e POSTGRES_PASSWORD=northwind -e POSTGRES_DB=northwind \
  postgres:17-alpine

# 2. The original Northwind schema, plus one widening so orders can pass 32 767 rows
psql -h localhost -U northwind -d northwind -f schema.sql
psql -h localhost -U northwind -d northwind -f scale.sql

# 3. Seed 3.7M rows, parents first, then look at them
export DB_PASSWORD=northwind
./seed.sh db
psql -h localhost -U northwind -d northwind -f verify.sql

# No database? Same data as files under ./output/
./seed.sh file                              # JSON
SEEDSTREAM_ARGS="--format csv" ./seed.sh file   # CSV
```

Prerequisites: SeedStream on `PATH` (or `export SEEDSTREAM=/path/to/bin/seedstream`) and, for the
database target, the PostgreSQL JDBC driver dropped into the distribution's `extras/` directory.
Drivers aren't bundled; see [`docs/CONTAINER.md`](../../docs/CONTAINER.md).

## What you get

| Table | Rows | Notes |
|---|---:|---|
| `categories`, `region`, `shippers`, `suppliers`, `us_states` | 8 / 4 / 6 / 29 / 51 | lookup tables, original Northwind sizes |
| `employees` | 9 | self-referencing `reports_to` |
| `territories`, `employee_territories` | 53 / 49 | M:N employee ↔ territory, composite PK |
| `products` | 77 | FKs to suppliers and categories |
| `customer_demographics`, `customer_customer_demo` | 10 / 20 000 | M:N, composite PK |
| `customers` | 90 000 | text key `varchar(5)` |
| `orders` | 1 000 000 | FKs to customers, employees, shippers |
| `order_details` | 2 600 000 | composite PK `(order_id, product_id)`, about 2.6 lines per order, as in the original |

Measured on a laptop (Ryzen 5 PRO 4650U, 32 GB, PostgreSQL 17 in Docker on the same machine):

| Target | Wall time (14 jobs, one JVM each) | Notes |
|---|---:|---|
| PostgreSQL, constraints on | **254 s** | orders 96 s, order_details 138 s; `per_batch` commits of 5 000 rows |
| JSON files | **21 s** | 550 MB; most of the time is JVM start-up across 14 jobs |
| CSV files | **22 s** | |

`verify.sql` prints the row counts, confirms that the 13 FK constraints were enforced throughout
the load, checks for orphans on the largest edges (0), and runs two classic Northwind reports:
top customers by revenue and sales by category.

## How it was built

### 1. Get the schema

[`schema.sql`](schema.sql) is `northwind.sql` from
[pthom/northwind_psql](https://github.com/pthom/northwind_psql) with the `INSERT` rows removed and
nothing else changed. To get it yourself:

```bash
curl -sSLO https://raw.githubusercontent.com/pthom/northwind_psql/master/northwind.sql
```

### 2. Inspect it

The file has the usual `pg_dump` layout, and three inspector gaps get in the way. The first
failure you'd hit:

```text
$ seedstream inspect northwind.sql -o structures/
inspect failed: Failed to parse 13 CREATE TABLE statement(s); no output written ...
```

[`schema-inspect.sql`](schema-inspect.sql) is the same schema after three mechanical rewrites.
Each one disappears when its issue lands:

| Rewrite | Why | Issue |
|---|---|---|
| Collapse runs of empty lines (`cat -s`) | two empty lines end a statement early, which breaks every `pg_dump` file | [#436](https://github.com/mferretti/SeedStream/issues/436) |
| Move `ALTER TABLE ONLY … ADD CONSTRAINT` PK/FK clauses into the table bodies | constraints declared after the table are ignored: without this, no `serial`, no `ref[]`, and no warning | [#376](https://github.com/mferretti/SeedStream/issues/376) |
| `REFERENCES customers` → `REFERENCES customers (customer_id)` | an FK without a column list crashes `inspect` | [#437](https://github.com/mferretti/SeedStream/issues/437) |

```bash
seedstream inspect schema-inspect.sql -o structures/
# inspect complete: 14 written, 0 skipped, 29 fields flagged for review (commented)
```

Out of the box, from 92 columns, the inspector produced:

- **8 `serial` primary keys** and **13 foreign keys** as `ref[parent.key, …]`, including the
  self-reference `employees.reports_to`.
- **3 composite keys** (`order_details`, `employee_territories`, `customer_customer_demo`) as
  `ref[…, unique=pk]` pairs, collision-free with no extra configuration.
- **24 semantic types** from column names (`last_name`, `postal_code`, `phone_number`,
  `product_name`, …). Ranges are capped to column capacity (`smallint` → `int[1..32767]`).
- **Review comments** on every guess, and **warnings** on the 3 text primary keys it couldn't
  enforce.

### 3. Hand edits: 32 of 92 fields

Every edit in [`structures/`](structures/) carries a comment naming its issue: `# hand edit (#NNN)`
is required to load the data, and `# realism (optional)` only makes it look more like Northwind.

| Edits | Fields | What and why | Issue |
|---:|---|---|---|
| 12 | every FK `ref[]` | `1..count` → the parent's real row count (`1..8` categories, `1..90000` customers, …); `count` is the *child* job's `--count`, so different table sizes need static ranges kept in sync with `seed.sh` | [#442](https://github.com/mferretti/SeedStream/issues/442) |
| 14 | `country`, `city`, `address`, `title`, `first_name` | name-hinted types overflow `varchar(n)` (`country` reaches 51 chars against `varchar(15)`); replaced with Northwind-style enums or a shorter type | [#440](https://github.com/mferretti/SeedStream/issues/440) |
| 3 | `customer_id`, `territory_id`, `customer_type_id` | text PKs inspected as random `char[1..5]` collide and can't be referenced; set to `serial` (numeric strings, so `varchar(5)` caps customers at 99 999) | [#441](https://github.com/mferretti/SeedStream/issues/441) |
| 2 | `categories.picture`, `employees.photo` | `bytea` inspected as `char`, which PostgreSQL rejects; removed (nullable, stored as `NULL`) | [#439](https://github.com/mferretti/SeedStream/issues/439) |
| 1 | `employees.reports_to` | a self-FK row may reference an id not inserted yet; `1..1` makes everyone report to employee 1 | [#213](https://github.com/mferretti/SeedStream/issues/213) |
| 16 | prices, discount, dates, names | *optional realism*: `discount` 0–25 %, the 8 real category names, `full_name` contacts, … | — |

<details>
<summary><b>Full edit list</b>: every field changed after <code>inspect</code>, before and after</summary>

**Required (32).** Without these, the load fails or the FKs don't resolve.

| Issue | Table | Field | Inspected | Edited to |
|---|---|---|---|---|
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `customer_customer_demo` | `customer_id` | `ref[customers.customer_id, 1..count, unique=pk]` | `ref[customers.customer_id, 1..90000, unique=pk]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `customer_customer_demo` | `customer_type_id` | `ref[customer_demographics.customer_type_id, 1..count, unique=pk]` | `ref[customer_demographics.customer_type_id, 1..10, unique=pk]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `employee_territories` | `employee_id` | `ref[employees.employee_id, 1..count, unique=pk]` | `ref[employees.employee_id, 1..9, unique=pk]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `employee_territories` | `territory_id` | `ref[territories.territory_id, 1..count, unique=pk]` | `ref[territories.territory_id, 1..53, unique=pk]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `order_details` | `order_id` | `ref[orders.order_id, 1..count, unique=pk]` | `ref[orders.order_id, 1..1000000, unique=pk]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `order_details` | `product_id` | `ref[products.product_id, 1..count, unique=pk]` | `ref[products.product_id, 1..77, unique=pk]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `orders` | `customer_id` | `ref[customers.customer_id, 1..count]` | `ref[customers.customer_id, 1..90000]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `orders` | `employee_id` | `ref[employees.employee_id, 1..count]` | `ref[employees.employee_id, 1..9]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `orders` | `ship_via` | `ref[shippers.shipper_id, 1..count]` | `ref[shippers.shipper_id, 1..6]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `products` | `category_id` | `ref[categories.category_id, 1..count]` | `ref[categories.category_id, 1..8]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `products` | `supplier_id` | `ref[suppliers.supplier_id, 1..count]` | `ref[suppliers.supplier_id, 1..29]` |
| [#442](https://github.com/mferretti/SeedStream/issues/442) | `territories` | `region_id` | `ref[region.region_id, 1..count]` | `ref[region.region_id, 1..4]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `customers` | `address` | `address` | `street_name` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `customers` | `city` | `city` | `enum[Aachen,Barcelona,… (29 values)]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `customers` | `country` | `country` | `enum[Argentina,Austria,… (21 values)]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `employees` | `address` | `address` | `street_name` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `employees` | `city` | `city` | `enum[Aachen,Barcelona,… (29 values)]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `employees` | `country` | `country` | `enum[Argentina,Austria,… (21 values)]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `employees` | `first_name` | `first_name` | `enum[Nancy,Andrew,… (9 values)]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `employees` | `title` | `title` | `enum[Sales Representative,Sales Manager,… (4 values)]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `orders` | `ship_address` | `address` | `street_name` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `orders` | `ship_city` | `city` | `enum[Aachen,Barcelona,… (29 values)]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `orders` | `ship_country` | `country` | `enum[Argentina,Austria,… (21 values)]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `suppliers` | `address` | `address` | `street_name` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `suppliers` | `city` | `city` | `enum[Aachen,Barcelona,… (29 values)]` |
| [#440](https://github.com/mferretti/SeedStream/issues/440) | `suppliers` | `country` | `country` | `enum[Argentina,Austria,… (21 values)]` |
| [#441](https://github.com/mferretti/SeedStream/issues/441) | `customer_demographics` | `customer_type_id` | `char[1..5]` | `serial` |
| [#441](https://github.com/mferretti/SeedStream/issues/441) | `customers` | `customer_id` | `char[1..5]` | `serial` |
| [#441](https://github.com/mferretti/SeedStream/issues/441) | `territories` | `territory_id` | `char[1..20]` | `serial` |
| [#439](https://github.com/mferretti/SeedStream/issues/439) | `categories` | `picture` | `char[1..50]` | `*removed*` |
| [#439](https://github.com/mferretti/SeedStream/issues/439) | `employees` | `photo` | `char[1..50]` | `*removed*` |
| [#213](https://github.com/mferretti/SeedStream/issues/213) | `employees` | `reports_to` | `ref[employees.employee_id, 1..count]` | `ref[employees.employee_id, 1..1]` |


**Optional realism (16).** The data loads without these; they make it read like Northwind.

| Table | Field | Inspected | Edited to |
|---|---|---|---|
| `categories` | `category_name` | `char[1..15]` | `enum[Beverages,Condiments,… (8 values)]` |
| `customers` | `contact_name` | `char[1..30]` | `full_name` |
| `employees` | `birth_date` | `date[2020-01-01..2030-12-31]` | `date[1950-01-01..1990-12-31]` |
| `employees` | `hire_date` | `date[2020-01-01..2030-12-31]` | `date[1992-01-01..2024-12-31]` |
| `order_details` | `unit_price` | `decimal[0.0..9999.99]` | `decimal[2.00..265.00]` |
| `order_details` | `quantity` | `int[1..32767]` | `int[1..120]` |
| `order_details` | `discount` | `decimal[0.0..9999.99]` | `decimal[0.00..0.25]` |
| `orders` | `order_date` | `date[2020-01-01..2030-12-31]` | `date[1996-07-01..2026-06-30]` |
| `orders` | `required_date` | `date[2020-01-01..2030-12-31]` | `date[1996-07-01..2026-06-30]` |
| `orders` | `shipped_date` | `date[2020-01-01..2030-12-31]` | `date[1996-07-01..2026-06-30]` |
| `orders` | `freight` | `decimal[0.0..9999.99]` | `decimal[0.02..1007.64]` |
| `orders` | `ship_name` | `char[1..40]` | `full_name` |
| `products` | `unit_price` | `decimal[0.0..9999.99]` | `decimal[2.00..265.00]` |
| `products` | `discontinued` | `int[1..999999]` | `int[0..1]` |
| `shippers` | `company_name` | `char[1..40]` | `enum[Speedy Express,United Package,… (6 values)]` |
| `suppliers` | `contact_name` | `char[1..30]` | `full_name` |

</details>

One change goes into the database rather than the YAML: Northwind uses `smallint` for every key,
which caps `orders` at 32 767 rows. [`scale.sql`](scale.sql) widens `order_id` to `integer`. Nothing
warns you in advance: without it the load fails at row 32 768, after the earlier batches have been
committed ([#440](https://github.com/mferretti/SeedStream/issues/440)).

### 4. Jobs and seeding

`jobs/` holds one database job and one file job per table, all with seed `1996`. The database jobs
truncate their table first. [`seed.sh`](seed.sh) runs them parents first with the counts above.
Those counts must match the static `ref[]` ranges in `structures/`
([#442](https://github.com/mferretti/SeedStream/issues/442)).

The jobs rely on the sibling `../structures/` auto-resolution, so call them with an **absolute**
`--job` path, as `seed.sh` does. A relative `jobs/x.yaml` falls back to `config/structures`
([#438](https://github.com/mferretti/SeedStream/issues/438)).

## How the edits produced the issues

The method: run the inspected output, and for each failure find the root cause, check whether an
issue already covers it, and open one if not. Then work around it, either with a DDL rewrite or a
hand edit tagged with that issue. The workaround is what you see in this folder; the issue is what
will remove it.

| Where it broke | Symptom | Root cause | Workaround here | Issue |
|---|---|---|---|---|
| `inspect` | `Failed to parse 13 CREATE TABLE statement(s)` | 2+ consecutive empty lines end a statement in JSQLParser | `cat -s` in `schema-inspect.sql` | [#436](https://github.com/mferretti/SeedStream/issues/436) (new) |
| `inspect` | no `serial`, no `ref[]`, no warning | `ALTER TABLE … ADD CONSTRAINT` not applied | constraints moved into the table bodies | [#376](https://github.com/mferretti/SeedStream/issues/376) (existing) |
| `inspect` | `NullPointerException … getReferencedColumnNames()` | `REFERENCES t` without a column list not resolved to the PK | columns spelled out | [#437](https://github.com/mferretti/SeedStream/issues/437) (new) |
| `inspect` | provenance header made a `SET` count as a failed table | quick check matches `CREATE TABLE` inside comments | header reworded | [#443](https://github.com/mferretti/SeedStream/issues/443) (new) |
| `execute` | `data structure file not found: config/structures/…` | sibling `../structures` not inferred for a relative job path | absolute `--job` in `seed.sh` | [#438](https://github.com/mferretti/SeedStream/issues/438) (new) |
| load | `column "picture" is of type bytea …` | no binary type; `bytea` falls back to `char` | 2 fields removed | [#439](https://github.com/mferretti/SeedStream/issues/439) (new) |
| load | `value too long for type character varying(15)` | name-hinted types ignore `varchar(n)` | 14 fields edited | [#440](https://github.com/mferretti/SeedStream/issues/440) (new) |
| load at scale | `smallint out of range` after 32 000 committed rows | `serial` drops the column's integer capacity | `scale.sql` widens `order_id` | [#440](https://github.com/mferretti/SeedStream/issues/440) (new) |
| load | duplicate key on `customer_demographics`; FK miss on `orders.customer_id` | text PKs inspected as random `char` | 3 fields → `serial` | [#441](https://github.com/mferretti/SeedStream/issues/441) (new) |
| load | `Key (category_id)=(43) is not present in table "categories"` | `1..count` uses the child's `--count` | 12 ranges set to parent counts | [#442](https://github.com/mferretti/SeedStream/issues/442) (new) |
| load | self-FK can reference a row not inserted yet | no ordered or self-referencing generation | `reports_to` → `1..1` | [#213](https://github.com/mferretti/SeedStream/issues/213) (existing) |

None of the new issues depends on another to be fixed, so none is marked as blocked. They cross-link
where they touch: #437 with #376, #441 with #440, #442 with #400.

## Limits

- **Fields are independent.** `shipped_date` can precede `order_date`, a customer's city doesn't
  match their country, and `orders.freight` ignores the order's lines. The data has plausible
  shapes, not a coherent business history.
- **Lookup names are sampled, not enumerated.** `categories.category_name` draws from the 8 real
  names at random, so an 8-row table can repeat one and miss another.
- **Some text stays random.** `company_name` keeps the inspected `char[1..40]`, because the
  `company` type can exceed `varchar(40)` ([#440](https://github.com/mferretti/SeedStream/issues/440)).
- **Customer ids are numeric strings** (`"562"`, not `"ALFKI"`), see
  [#441](https://github.com/mferretti/SeedStream/issues/441).
- **One table per job.** 14 JVM start-ups dominate the file-target time; a whole-schema job is
  discussed in [#442](https://github.com/mferretti/SeedStream/issues/442) and
  [#400](https://github.com/mferretti/SeedStream/issues/400).

## Files

| File | Purpose |
|---|---|
| `schema.sql` | Northwind DDL, unmodified (`INSERT` rows removed); load this into PostgreSQL |
| `scale.sql` | Widens `orders.order_id` / `order_details.order_id` to `integer` for the 1M-row run |
| `schema-inspect.sql` | `schema.sql` after the three rewrites above; feed this to `inspect` |
| `structures/*.yaml` | Inspector output plus the commented hand edits |
| `jobs/db_*.yaml`, `jobs/file_*.yaml` | One PostgreSQL job and one file job per table |
| `seed.sh` | `./seed.sh db` or `./seed.sh file`: all 14 tables, parents first, with timings |
| `verify.sql` | Row counts, FK check, orphan check, two Northwind reports |
| `LICENSE-northwind.txt` | Microsoft Public License (Ms-PL) covering the Northwind schema |

## Attribution

The Northwind schema is © Microsoft, distributed under the Microsoft Public License (Ms-PL). The
PostgreSQL port is by [pthom/northwind_psql](https://github.com/pthom/northwind_psql). The license
text ships as [`LICENSE-northwind.txt`](LICENSE-northwind.txt). SeedStream ships only the schema,
not the original data.
