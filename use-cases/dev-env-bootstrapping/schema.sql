-- Developer-environment bootstrap schema (issue #79) — a small but real task-tracker app.
-- Surrogate PKs are DB-assigned IDENTITY columns: Postgres fills a dense 1..N sequence, so
-- SeedStream does NOT generate `id` (the structures omit it). Child jobs then reference that
-- dense pool with a static `ref[parent.id, 1..<parent_count>]`, and every FK resolves.
--
-- Run order matters (parents before children); bootstrap.sh handles it.
-- Usage (once): PGPASSWORD=... psql -h localhost -U devuser -d devdb -f schema.sql

DROP TABLE IF EXISTS task_labels, comments, tasks, projects, labels, users CASCADE;

CREATE TABLE users (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email      VARCHAR(255) NOT NULL,
  full_name  VARCHAR(255) NOT NULL,
  created_at TIMESTAMP    NOT NULL
);

CREATE TABLE labels (
  id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(40) NOT NULL
);

CREATE TABLE projects (
  id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name     VARCHAR(120) NOT NULL,
  status   VARCHAR(16)  NOT NULL CHECK (status IN ('active','archived','draft')),
  owner_id BIGINT       NOT NULL REFERENCES users(id)
);

CREATE TABLE tasks (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  title          VARCHAR(200) NOT NULL,
  status         VARCHAR(16)  NOT NULL CHECK (status IN ('todo','in_progress','done','blocked')),
  priority       VARCHAR(8)   NOT NULL CHECK (priority IN ('low','med','high')),
  project_id     BIGINT       NOT NULL REFERENCES projects(id),
  assignee_id    BIGINT       NOT NULL REFERENCES users(id),
  -- Self-referencing subtasks: column exists, but SeedStream leaves it NULL (see README "Limits").
  parent_task_id BIGINT       REFERENCES tasks(id)
);

CREATE TABLE comments (
  id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  body      VARCHAR(500) NOT NULL,
  task_id   BIGINT       NOT NULL REFERENCES tasks(id),
  author_id BIGINT       NOT NULL REFERENCES users(id)
);

-- Join table (M:N task↔label). Surrogate PK, no UNIQUE(task_id,label_id): with random refs a
-- (task,label) pair can repeat. Add the UNIQUE constraint once a `unique` generator lands — see README.
CREATE TABLE task_labels (
  id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  task_id  BIGINT NOT NULL REFERENCES tasks(id),
  label_id BIGINT NOT NULL REFERENCES labels(id)
);
