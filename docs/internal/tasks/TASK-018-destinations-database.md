# TASK-018: Destinations Module - Database Adapter (Stage 1)

**Status**: ✅ Complete (Stage 1)
**Priority**: P1 (High)
**Phase**: 4 - Destinations
**Dependencies**: TASK-013 (JSON Serializer), TASK-016 (File Destination)
**Human Supervision**: MEDIUM (SQL generation, connection pooling)
**Completion Date**: March 9, 2026

---

## Objective

Implement database destination adapter that inserts generated records into relational databases via JDBC with HikariCP connection pooling and batch inserts.

**Stage 1 scope**: flat structures only (primitives, enums, Datafaker string types). Nested objects and arrays rejected with a clear error. See `docs/DATABASE-DESTINATION-PLANNING.md` for the full staging plan.

---

## What Was Delivered

### New Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `DatabaseDestinationConfig` | `destinations/.../database/` | Config model — JDBC URL, credentials, table, batch size, pool size, transaction strategy |
| `JdbcTypeMapper` | `destinations/.../database/` | Static `bind()` — maps Java values to `PreparedStatement.setXxx()` via `instanceof` (Option A) |
| `DatabaseDestination` | `destinations/.../database/` | Full `DestinationAdapter` — HikariCP pool, batch INSERT, 3 transaction strategies, flat-only validation |
| `package-info.java` | `destinations/.../database/` | Package docs |

### CLI Integration

- `ExecuteCommand.java` — `case "database"` wired in `createDestination()`
- `resolveEnvVar()` — `${VAR_NAME}` substitution from `System.getenv()` for any config string field
- Table name resolution: `conf.table` override → structure name fallback

### Configuration

```yaml
# config/jobs/db_passport.yaml
source: passport.yaml
type: database
seed:
  type: embedded
  value: 42
conf:
  jdbc_url: "jdbc:postgresql://localhost:5432/testdb"
  username: "dbuser"
  password: "${DB_PASSWORD}"   # resolved from environment variable
  table: "passports"           # optional — defaults to structure name
  batch_size: 1000
  pool_size: 5
  transaction_strategy: per_batch  # per_batch | per_job | auto_commit
```

### Key Design Decisions

See `docs/DATABASE-DESTINATION-PLANNING.md` — Technical Decisions Summary.

- **Option A type binding**: `instanceof` on generated Java values (Option B deferred to TASK-042)
- **Table name**: `source` field = default table name; optional `conf.table` override
- **Env var substitution**: `${VAR_NAME}` resolved at parse time; fail fast if unset
- **Fail fast on nested/arrays**: Stage 1 guard at `write()` time

### Dependencies Updated

- `gradle/libs.versions.toml` — H2 `2.3.232` added for unit testing
- `destinations/build.gradle.kts` — `testImplementation(libs.h2)`, `testImplementation(libs.postgresql)`

---

## Tests

### Unit Tests (10) — `JdbcTypeMapperTest`, `DatabaseDestinationTest`

| Test class | Count | Covers |
|------------|-------|--------|
| `JdbcTypeMapperTest` | 10 | All JDBC bindings (null, int, long, BigDecimal, boolean, LocalDate, Instant, String, enum fallback, SQLException propagation) |
| `DatabaseDestinationTest` | 10 | Insert, batch, partial flush on close, nested/array rejection, pre-open guard, transaction strategies — via H2 in-memory |

### Integration Tests (9) — `DatabaseDestinationIT`

Against real PostgreSQL via Testcontainers (`postgres:16-alpine`). Uses passport structure (flat, all field types).

| Test | Verifies |
|------|---------|
| `shouldInsertPassportRecords` | Basic connection + INSERT |
| `shouldPersistCorrectFieldValues` | String field round-trip |
| `shouldPersistDateFieldsCorrectly` | `LocalDate` → `DATE` column (dob, issue_date, expiry_date) |
| `shouldInsertAcrossMultipleBatches` | Batch flush with real PG (35 records, batchSize=10) |
| `shouldFlushPartialBatchOnClose` | `close()` flushes remainder |
| `shouldCommitWithPerJobStrategy` | Single commit at `flush()` |
| `shouldWorkWithAutoCommitStrategy` | JDBC auto-commit mode |
| `shouldRespectTableNameOverride` | `tableName` config routes to correct table |
| `shouldRejectNestedObjectWithClearError` | Stage 1 guard |

```bash
./gradlew :destinations:test              # unit tests
./gradlew :destinations:integrationTest   # PostgreSQL integration tests
```

---

## Stage 2 (Future — see DATABASE-DESTINATION-PLANNING.md)

- Nested objects → separate table inserts with FK injection
- Arrays → child table inserts with FK injection
- Insert ordering (topological sort)
- `ref[]` type support (TASK-042 decision first)

---

## Acceptance Criteria

- ✅ Connects to PostgreSQL via JDBC
- ✅ HikariCP connection pooling
- ✅ Batch inserts (`addBatch` / `executeBatch`)
- ✅ Three transaction strategies: `per_batch`, `per_job`, `auto_commit`
- ✅ Flat-only validation with clear Stage 1 error
- ✅ Table name override support
- ✅ Env var substitution for credentials
- ✅ 10 unit tests passing
- ✅ 9 PostgreSQL integration tests passing
