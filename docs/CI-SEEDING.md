# CI Pipeline Database Seeding

This guide has moved into the runnable use case:
**[`use-cases/ci-pipeline-seeding/`](../use-cases/ci-pipeline-seeding/README.md)** (issue #80).

It covers the same ground — deterministic reseeding of a disposable database before integration
tests — but as a self-contained package you can copy and run: schema, structures, jobs, a seed
script, and a fingerprint check. The GitHub Actions workflow
[`use-case-ci-seeding.yml`](../.github/workflows/use-case-ci-seeding.yml) executes it against a
real PostgreSQL service container on every relevant PR.

Config reference for the two flags involved:

| Key | Effect |
|---|---|
| `truncate_before_insert: true` | Empties each target table with `TRUNCATE TABLE ... CASCADE` before its first insert. **DESTRUCTIVE.** PostgreSQL/Oracle only. |
| `restart_identity: true` | Makes that statement `TRUNCATE ... RESTART IDENTITY CASCADE`, so identity/serial sequences restart at 1 and static `ref[parent.id, 1..N]` ranges stay valid across reseeds. PostgreSQL only; requires `truncate_before_insert`. |
