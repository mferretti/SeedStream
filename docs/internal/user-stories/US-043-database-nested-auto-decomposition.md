# US-043: Generate Data into Related Tables from Nested Structures

**Status:** Not Started
**Priority:** P1
**Phase:** Phase 8 (Database Destinations Stage 2)
**Task:** TASK-043-database-stage2-nested-decomposition.md

---

## User Story

**As a** QA engineer setting up integration test data for an e-commerce application,
**I want to** define a single `order.yaml` structure with embedded `line_items`,
**so that** I can populate both the `orders` and `line_items` database tables in one job run without writing two separate configs or post-processing scripts.

---

## Background

Our application uses a standard relational schema:

```sql
CREATE TABLE orders (
  id INT,
  customer_id INT,
  order_date DATE
);

CREATE TABLE line_items (
  id INT,
  order_id INT,   -- FK → orders.id
  product_name VARCHAR(255),
  quantity INT,
  price DECIMAL(10,2)
);
```

Today (Stage 1), defining `line_items: array[object[line_item], 1..5]` in `order.yaml` causes the database destination to fail with a clear error: *"Stage 1 does not support arrays"*. The workaround is to flatten or split jobs, which is cumbersome for integration testing.

---

## Acceptance Criteria

1. **Single YAML definition**: A job with `type: database` and a structure containing nested `object[X]` or `array[object[X], min..max]` fields executes without error.

2. **Parent table populated**: Scalar fields from the top-level structure are inserted into the table named by `conf.table` (or the structure name).

3. **Child table populated automatically**: Each nested object / array element is inserted into the table named after the nested structure (`line_item` → `line_items` table? No — table name matches the YAML structure name exactly: `line_item`).
   > **Note:** Table name = structure name. Tester must create tables with matching names.

4. **FK injected correctly**: Each child row has `{parent_structure_name}_id` set to the parent's `id` field value. Example: `order_id = 42301` on all `line_item` rows belonging to order 42301.

5. **Correct counts**: Running the job with `--count 100` and `line_items: array[object[line_item], 1..5]` results in:
   - Exactly 100 rows in `orders`
   - Between 100 and 500 rows in `line_items` (random, seed-controlled)

6. **Multi-level nesting works**: A structure with `order → line_item → attribute` (3 levels) correctly inserts into all three tables with correct FK chains.

7. **Existing destinations unaffected**: File, Kafka, and flat-database jobs produce identical output to before Stage 2.

8. **Transaction safety**: With `transaction_strategy: per_batch`, if a child insert fails, the parent insert for that batch is also rolled back.

---

## Example Configuration

```yaml
# config/structures/order.yaml
name: order
data:
  id: int[1..999999]
  customer_id: int[1..9999]
  order_date: date[2024-01-01..2024-12-31]
  status: enum[PENDING,CONFIRMED,SHIPPED,DELIVERED]
  line_items: array[object[line_item], 1..5]
```

```yaml
# config/structures/line_item.yaml
name: line_item
data:
  id: int[1..9999999]
  product_name: product_name
  quantity: int[1..10]
  price: decimal[0.99..999.99]
```

```yaml
# config/jobs/db_order.yaml
source: order.yaml
type: database
seed:
  type: embedded
  value: 42
conf:
  jdbc_url: "jdbc:postgresql://localhost:5432/testdb"
  username: "testuser"
  password: "testpass"
  table: "order"
  batch_size: 500
  pool_size: 5
  transaction_strategy: per_batch
```

**Run:**
```bash
./gradlew :cli:run --args="execute --job config/jobs/db_order.yaml --count 1000"
```

**Expected output:**
```
Generated 1000 records → orders table (1000 rows)
Generated ~2500 records → line_item table (~2500 rows, avg 2.5 per order)
```

---

## Out of Scope (Stage 2)

- `ref[]` cross-record references (requires TASK-012 Reference Generator)
- Composite primary keys
- Non-standard FK column names (convention `{parent}_id` only)
- DB auto-increment IDs (SeedStream generates all IDs)
- Multiple root tables in one job
- UPSERT / conflict handling

---

## Definition of Done

- [ ] TASK-043 implementation checklist complete
- [ ] All unit tests passing (`./gradlew :destinations:test`)
- [ ] All integration tests passing (`./gradlew :destinations:integrationTest`)
- [ ] E2E test with order + line_items in `run_e2e_test.sh`
- [ ] `DATABASE-DESTINATION-PLANNING.md` Stage 2 section up to date
- [ ] `TASK-CATALOG.md` TASK-043 marked complete

---

**Created:** March 9, 2026
