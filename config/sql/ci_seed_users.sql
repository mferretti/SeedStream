-- CI integration-test user table (issue #80).
-- One-time setup: creates the table SeedStream seeds via db_ci_seed_users.yaml.
-- The job's `truncate_before_insert: true` empties this table on every run, so
-- this script only needs to run once (or whenever the schema changes).
--
-- Usage: psql -U dbuser -d testdb -f ci_seed_users.sql

CREATE TABLE IF NOT EXISTS ci_users (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  first_name  VARCHAR(255) NOT NULL,
  last_name   VARCHAR(255) NOT NULL,
  email       VARCHAR(255) NOT NULL,
  username    VARCHAR(255) NOT NULL,
  active      BOOLEAN      NOT NULL,
  signup_date DATE         NOT NULL
);
