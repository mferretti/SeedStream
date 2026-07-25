-- Determinism fingerprint for the seeded fixture (issue #80).
--
-- Emits one line per table: "<table> <row_count> <md5>", where the md5 covers every column of
-- every row (including the IDENTITY id) in id order. Two runs of seed.sh must produce identical
-- output; that is what the CI workflow asserts, against both the previous run and the committed
-- expected-fingerprint.txt.
--
-- Session settings below pin the textual rendering of dates/timestamps so the hash depends only
-- on the data, not on the client's locale or timezone.
--
-- Usage: PGPASSWORD=... psql -h localhost -U ci_user -d ci_testdb -q -X -f verify.sql
-- (-q suppresses command tags, so stdout is exactly the three fingerprint lines.)

\pset format unaligned
\pset tuples_only on
\pset footer off

SET TimeZone = 'UTC';
SET DateStyle = 'ISO, MDY';

SELECT 'customers ' || count(*) || ' ' || coalesce(md5(string_agg(t::text, E'\n' ORDER BY t.id)), '-')
FROM customers t;

SELECT 'orders ' || count(*) || ' ' || coalesce(md5(string_agg(t::text, E'\n' ORDER BY t.id)), '-')
FROM orders t;

SELECT 'order_items ' || count(*) || ' ' || coalesce(md5(string_agg(t::text, E'\n' ORDER BY t.id)), '-')
FROM order_items t;
