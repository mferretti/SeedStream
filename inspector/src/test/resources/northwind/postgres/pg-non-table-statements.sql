-- SeedStream test fixture (Apache-2.0, written for this project): Statements that are not modellable tables (#443) — that real pg_dump
-- output does not contain but hand-written / tool-exported PostgreSQL scripts do.

-- Header comment: the CREATE TABLE statements below are hand-written.
SET client_encoding = 'UTF8';

\connect northwind

CREATE TABLE base (
    id integer NOT NULL PRIMARY KEY
);

CREATE FUNCTION make_tmp() RETURNS void AS $$
BEGIN
    CREATE TABLE tmp_scratch (y integer);
END
$$ LANGUAGE plpgsql;

CREATE TABLE ledgers_copy AS SELECT * FROM ledgers;

CREATE TABLE events (
    event_id integer NOT NULL,
    happened_on date NOT NULL
) PARTITION BY RANGE (happened_on);

CREATE TABLE events_2026 PARTITION OF events FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
