-- CI pipeline seeding schema (issue #80) — a small order-service fixture.
--
-- Run ONCE per database (or whenever the schema changes). The per-run reseed is handled by
-- SeedStream itself: every job sets `truncate_before_insert: true` + `restart_identity: true`,
-- so each `seed.sh` run empties the tables and restarts the IDENTITY sequences at 1.
--
-- Surrogate PKs are DB-assigned IDENTITY columns: Postgres fills a dense 1..N sequence, so
-- SeedStream does NOT generate `id` (the structures omit it). Child jobs reference that dense
-- pool with a static `ref[parent.id, 1..<parent_count>]`, and every FK resolves — on the first
-- run and on every reseed, because RESTART IDENTITY brings the sequence back to 1.
--
-- Usage (once): PGPASSWORD=... psql -h localhost -U ci_user -d ci_testdb -f schema.sql

CREATE TABLE IF NOT EXISTS customers (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email       VARCHAR(255) NOT NULL,
  full_name   VARCHAR(255) NOT NULL,
  signup_date DATE         NOT NULL,
  active      BOOLEAN      NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id  BIGINT        NOT NULL REFERENCES customers(id),
  status       VARCHAR(16)   NOT NULL CHECK (status IN ('pending','paid','shipped','cancelled')),
  total_amount NUMERIC(10,2) NOT NULL,
  placed_at    TIMESTAMP     NOT NULL
);

-- `total_amount` above is generated independently of the line items below — SeedStream has no
-- cross-record arithmetic. Assert on ranges and counts, not on order totals summing to items.
CREATE TABLE IF NOT EXISTS order_items (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id     BIGINT        NOT NULL REFERENCES orders(id),
  product_name VARCHAR(120)  NOT NULL,
  quantity     INT           NOT NULL CHECK (quantity BETWEEN 1 AND 5),
  unit_price   NUMERIC(10,2) NOT NULL
);
