# Northwind DDL fixtures

Real-world schema exports used as regression inputs for `inspect`. Data rows (`INSERT`) are
removed; everything else is kept byte-for-byte as exported, because layout quirks (empty-line
runs, `GO`, `ALTER TABLE` placement, quoting) are exactly what these fixtures exercise.

Only permissively licensed sources are used.

| File | Source | License |
|---|---|---|
| `sqlserver/instnwnd-schema.sql` | Microsoft, [sql-server-samples](https://github.com/microsoft/sql-server-samples) `samples/databases/northwind-pubs/instnwnd.sql` (INSERT and `SET IDENTITY_INSERT` statements removed) | MIT — `sqlserver/LICENSE-MIT.txt` |
| `postgres/northwind-pg-source.sql` | SeedStream's own PostgreSQL conversion of the Microsoft script above | MIT (Microsoft) — `postgres/LICENSE-MIT-microsoft.txt` |
| `postgres/northwind-pg-dump.sql` | `pg_dump --schema-only --no-owner --no-privileges` (PostgreSQL 17.9) of `northwind-pg-source.sql`, unmodified | MIT (Microsoft) — `postgres/LICENSE-MIT-microsoft.txt` |
| `postgres/pg-refs-without-columns.sql`, `postgres/pg-non-table-statements.sql` | Written for this project: shapes real `pg_dump` output does not contain | Apache-2.0 (project license) |
| `mysql/northwind-mysql-schema.sql` | [gitkeerthi/northwind-mysql](https://github.com/gitkeerthi/northwind-mysql) `northwind-mysql.sql` (INSERT statements removed) | Apache-2.0 (project license) |
| `oracle/northwind-oracle-schema.sql` | [dshifflet/NorthwindOracle_DDL](https://github.com/dshifflet/NorthwindOracle_DDL) `northwind_export.sql` (INSERT statements removed) | MIT — `oracle/LICENSE-MIT.txt` |

Removing data must not change how a file inspects: each stripped fixture produces the same
`inspect` log and structures as its original (checked when the fixtures were added).
