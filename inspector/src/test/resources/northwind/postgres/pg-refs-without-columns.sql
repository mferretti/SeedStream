-- SeedStream test fixture (Apache-2.0, written for this project): Foreign keys without a referenced column list (#437) — that real pg_dump
-- output does not contain but hand-written / tool-exported PostgreSQL scripts do.

CREATE TABLE accounts (
    account_id integer NOT NULL,
    CONSTRAINT accounts_pkey PRIMARY KEY (account_id)
);

CREATE TABLE ledgers (
    ledger_id integer NOT NULL,
    account_id integer,
    owner_account integer REFERENCES accounts,
    CONSTRAINT ledgers_pkey PRIMARY KEY (ledger_id),
    CONSTRAINT ledgers_account_fk FOREIGN KEY (account_id) REFERENCES accounts
);

CREATE TABLE pairs (
    a integer NOT NULL,
    b integer NOT NULL,
    CONSTRAINT pairs_pkey PRIMARY KEY (a, b)
);

CREATE TABLE pair_refs (
    id integer PRIMARY KEY,
    a integer,
    b integer,
    CONSTRAINT pair_refs_fk FOREIGN KEY (a, b) REFERENCES pairs
);

