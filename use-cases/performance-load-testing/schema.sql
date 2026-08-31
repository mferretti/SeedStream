-- Performance / load-testing schema (issue #81) — one denormalized analytics-event table.
-- Run once: PGPASSWORD=... psql -h localhost -U loaduser -d loadtestdb -f schema.sql
-- SeedStream reseeds via db_events.yaml's truncate_before_insert + restart_identity — it does not
-- recreate the schema, so re-running this file is only needed if you want a clean slate.

DROP TABLE IF EXISTS events CASCADE;

CREATE TABLE events (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  event_id     VARCHAR(36)   NOT NULL,
  user_id      BIGINT        NOT NULL,
  session_id   VARCHAR(36)   NOT NULL,
  event_type   VARCHAR(16)   NOT NULL CHECK (event_type IN
                 ('page_view','click','add_to_cart','purchase','signup','logout')),
  device       VARCHAR(16)   NOT NULL CHECK (device IN ('desktop','mobile','tablet')),
  country      VARCHAR(64)   NOT NULL,
  ip           VARCHAR(45)   NOT NULL,
  url          VARCHAR(512)  NOT NULL,
  amount       NUMERIC(8,2)  NOT NULL,
  occurred_at  TIMESTAMP     NOT NULL
);

-- Three secondary indexes so you can observe realistic index behavior at load — a filter on
-- event_type, a lookup by user_id, and a range scan on occurred_at are the three access patterns
-- an analytics/telemetry table is actually queried by.
CREATE INDEX idx_events_user_id     ON events (user_id);
CREATE INDEX idx_events_event_type  ON events (event_type);
CREATE INDEX idx_events_occurred_at ON events (occurred_at);
