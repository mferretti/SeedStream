-- Northwind declares every key as SMALLINT (max 32 767 rows). Widen the order keys so the
-- seeding run can reach ~1M orders. The FK constraints follow the column type change.
-- inspect emits these keys as serial[1..32767] (#440); orders.yaml raises the ceiling to match.
ALTER TABLE order_details ALTER COLUMN order_id TYPE integer;
ALTER TABLE orders ALTER COLUMN order_id TYPE integer;
