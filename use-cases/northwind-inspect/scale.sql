-- Northwind declares every key as SMALLINT (max 32 767 rows). Widen the order keys so the
-- seeding run can reach ~1M orders. The FK constraints follow the column type change.
-- Inspector gap: the ceiling is not reported at inspect time (#440).
ALTER TABLE order_details ALTER COLUMN order_id TYPE integer;
ALTER TABLE orders ALTER COLUMN order_id TYPE integer;
