-- SaaS demo environment schema (issue #82) — a small but convincing CRM: sales reps sell into
-- accounts, work contacts at those accounts, chase deals, and log activities against them.
-- Surrogate PKs are DB-assigned IDENTITY columns: Postgres fills a dense 1..N sequence, so
-- SeedStream does NOT generate `id` (the structures omit it). Child jobs then reference that
-- dense pool with a static `ref[parent.id, 1..<parent_count>]`, and every FK resolves.
--
-- Run once — reseeding is handled by seed.sh (TRUNCATE ... CASCADE + IDENTITY restart per job),
-- not by re-running this file. Usage (once): PGPASSWORD=... psql -h localhost -U demouser -d demodb -f schema.sql

DROP TABLE IF EXISTS activities, deals, contacts, accounts, users CASCADE;

CREATE TABLE users (
  id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  full_name VARCHAR(255) NOT NULL,
  email     VARCHAR(255) NOT NULL,
  title     VARCHAR(100) NOT NULL,
  region    VARCHAR(10)  NOT NULL CHECK (region IN ('NA','EMEA','APAC','LATAM'))
);

CREATE TABLE accounts (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name       VARCHAR(255) NOT NULL,
  website    VARCHAR(255) NOT NULL,
  industry   VARCHAR(20)  NOT NULL CHECK (industry IN
               ('SaaS','Fintech','Healthcare','Retail','Manufacturing','Education','Government')),
  size       VARCHAR(20)  NOT NULL CHECK (size IN ('SMB','MidMarket','Enterprise')),
  created_at DATE         NOT NULL
);

CREATE TABLE contacts (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  account_id BIGINT       NOT NULL REFERENCES accounts(id),
  full_name  VARCHAR(255) NOT NULL,
  email      VARCHAR(255) NOT NULL,
  title      VARCHAR(100) NOT NULL,
  phone      VARCHAR(40)  NOT NULL
);

CREATE TABLE deals (
  id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  account_id BIGINT        NOT NULL REFERENCES accounts(id),
  owner_id   BIGINT        NOT NULL REFERENCES users(id),
  name       VARCHAR(255)  NOT NULL,
  stage      VARCHAR(20)   NOT NULL CHECK (stage IN
               ('lead','qualified','proposal','negotiation','closed_won','closed_lost')),
  amount     NUMERIC(12,2) NOT NULL,
  close_date DATE          NOT NULL
);

CREATE TABLE activities (
  id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  contact_id  BIGINT       NOT NULL REFERENCES contacts(id),
  deal_id     BIGINT       NOT NULL REFERENCES deals(id),
  type        VARCHAR(20)  NOT NULL CHECK (type IN ('call','email','meeting','note')),
  subject     VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP    NOT NULL
);
