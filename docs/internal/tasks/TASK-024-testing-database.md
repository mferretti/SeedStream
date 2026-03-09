# TASK-024: Testing - Database Integration Tests

**Status**: ✅ Complete (PostgreSQL — Stage 1)
**Priority**: P1 (High)
**Phase**: 6 - Testing & Quality
**Dependencies**: TASK-018 (Database Adapter), TASK-022 (Integration Tests Setup)
**Human Supervision**: LOW
**Completion Date**: March 9, 2026

---

## Objective

Write integration tests for the database destination using Testcontainers against a real PostgreSQL instance. MySQL deferred — original scope reduced to PostgreSQL only for Stage 1 (verifying integration works, not configuration breadth).

---

## What Was Delivered

**File**: `destinations/src/test/java/com/datagenerator/destinations/database/DatabaseDestinationIT.java`

- Extends `IntegrationTest` base class (`@Tag("integration")` + `@Testcontainers`)
- Static `PostgreSQLContainer` (`postgres:16-alpine`) shared across tests
- `passports` table created in `@BeforeEach`, dropped in `@AfterEach`
- Uses passport structure (flat, all field types: VARCHAR, DATE, enum-as-string)

### Test Cases (9)

| Test | Verifies |
|------|---------|
| `shouldInsertPassportRecords` | Basic connection + INSERT works end-to-end |
| `shouldPersistCorrectFieldValues` | String fields round-trip correctly through PostgreSQL |
| `shouldPersistDateFieldsCorrectly` | `LocalDate` → `DATE` column (dob, issue_date, expiry_date) |
| `shouldInsertAcrossMultipleBatches` | Batch flush with real DB (35 records, batchSize=10) |
| `shouldFlushPartialBatchOnClose` | `close()` flushes the partial final batch |
| `shouldCommitWithPerJobStrategy` | Single commit at `flush()`, all records persisted |
| `shouldWorkWithAutoCommitStrategy` | JDBC auto-commit mode works end-to-end |
| `shouldRespectTableNameOverride` | `tableName` config routes inserts to correct table |
| `shouldRejectNestedObjectWithClearError` | Stage 1 guard fires with a clear error message |

### Why Passport Structure

Passport is a flat structure with all relevant Stage 1 types:
- `char[]` → `VARCHAR` (passport number)
- Datafaker types → `VARCHAR` (first_name, last_name, nationality, etc.)
- `date[]` → `DATE` (dob, issue_date, expiry_date)
- `enum[]` → `VARCHAR` (sex: M/F/X)

No nested objects, no arrays — fully compatible with Stage 1 scope.

---

## Running

```bash
./gradlew :destinations:integrationTest
```

Requires Docker running on the host.

---

## Scope Notes

**In scope (done)**:
- ✅ PostgreSQL integration tests (9 tests)
- ✅ All three transaction strategies validated
- ✅ Date field round-trip verified
- ✅ Table name override verified
- ✅ Stage 1 flat-only guard verified

**Deferred to Stage 2**:
- MySQL integration tests (not needed until MySQL-specific behaviour needs testing)
- Transaction rollback on error (requires triggering a real constraint violation)
- Schema validation tests (Stage 2 feature)
