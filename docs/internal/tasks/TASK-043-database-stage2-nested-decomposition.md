# TASK-043: Database Destination Stage 2 — Nested Structure Auto-Decomposition

**Status:** Not Started
**Priority:** P1
**Phase:** Phase 8 (Database Destinations)
**Estimated Effort:** 20-25 hours
**Complexity:** High
**Dependencies:** TASK-018 (Stage 1 complete ✅), TASK-042 (Option B complete ✅)
**Branch:** `feature/typed-record-pipeline-option-b` (do not merge until Stage 2 complete or approach abandoned)
**User Story:** US-043-database-nested-auto-decomposition.md

---

## Goal

Enable the Database destination to write nested structures (objects, arrays) to multiple related tables automatically. The job type `database` drives the behaviour — generators, serializers, Kafka/File destinations are unchanged.

---

## Design Summary

See `docs/DATABASE-DESTINATION-PLANNING.md` — Stage 2 section for full design rationale, worked example, and architectural decisions.

**Key design rules:**
- `object[X]` field → one child-table INSERT into `X`, FK `{parent_structure}_id` injected
- `array[object[X], min..max]` field → N child-table INSERTs into `X`, same FK
- Context stack: each nesting level tracks its own `(tableName, parentId)` — children only see immediate parent
- Convention-based FK column name: `{parent_structure_name}_id`
- No composite PK support (deferred)
- No `ref[]` cross-record references (requires TASK-012)
- Tables must pre-exist; no DDL generation

---

## New Components

### 1. `NestedRecordDecomposer`

**Location:** `destinations/src/main/java/com/datagenerator/destinations/database/NestedRecordDecomposer.java`

Recursively splits a generated record into:
- A flat `Map<String, Object>` of scalar fields (parent INSERT)
- A `Map<String, List<Map<String, Object>>>` of child records keyed by table name (child INSERTs)

```java
public class NestedRecordDecomposer {

  /** Result of decomposing one record level. */
  public record DecomposedRecord(
      Map<String, Object> flatFields,
      Map<String, List<Map<String, Object>>> childrenByTable) {}

  /**
   * Splits a record into flat scalar fields and nested children.
   * Children have the FK column pre-injected from parentContext.
   */
  public DecomposedRecord decompose(Map<String, Object> record, ParentContext parentContext) {
    Map<String, Object> flat = new LinkedHashMap<>();
    Map<String, List<Map<String, Object>>> children = new LinkedHashMap<>();

    for (Map.Entry<String, Object> entry : record.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
        // array[object[X]] → child table X
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childRecords = (List<Map<String, Object>>) list;
        if (parentContext != null) {
          childRecords.forEach(child -> child.put(parentContext.fkColumnName(), parentContext.parentId()));
        }
        children.put(key, childRecords);
      } else if (value instanceof Map) {
        // object[X] → child table X (single record, wrapped in list)
        @SuppressWarnings("unchecked")
        Map<String, Object> childRecord = (Map<String, Object>) value;
        if (parentContext != null) {
          childRecord.put(parentContext.fkColumnName(), parentContext.parentId());
        }
        children.put(key, List.of(childRecord));
      } else {
        flat.put(key, value);
      }
    }

    return new DecomposedRecord(flat, children);
  }
}
```

### 2. `ParentContext`

**Location:** `destinations/src/main/java/com/datagenerator/destinations/database/ParentContext.java`

```java
public record ParentContext(String tableName, Object parentId) {
  /**
   * FK column name injected into child records.
   * E.g. tableName="order" → "order_id"
   */
  public String fkColumnName() {
    return tableName + "_id";
  }
}
```

### 3. `DecomposedInsertCoordinator`

**Location:** `destinations/src/main/java/com/datagenerator/destinations/database/DecomposedInsertCoordinator.java`

Orchestrates recursive inserts for one top-level record:

```java
public class DecomposedInsertCoordinator {

  private final NestedRecordDecomposer decomposer = new NestedRecordDecomposer();

  /**
   * Recursively inserts a record tree into the correct tables.
   * Parent is inserted first; children follow with FK injected.
   *
   * @param record       the generated record (may contain nested objects/arrays)
   * @param tableName    the table to insert the flat portion into
   * @param parentCtx    parent context for FK injection (null for root)
   * @param insertFn     function to execute a flat INSERT (table → flatRecord)
   */
  public void insert(
      Map<String, Object> record,
      String tableName,
      ParentContext parentCtx,
      BiConsumer<String, Map<String, Object>> insertFn) {

    NestedRecordDecomposer.DecomposedRecord decomposed = decomposer.decompose(record, parentCtx);

    // 1. Insert flat parent fields
    insertFn.accept(tableName, decomposed.flatFields());

    // 2. Build parent context from this record's "id" field
    Object myId = decomposed.flatFields().get("id");
    ParentContext myCtx = (myId != null) ? new ParentContext(tableName, myId) : null;

    // 3. Recurse into children
    for (Map.Entry<String, List<Map<String, Object>>> childEntry : decomposed.childrenByTable().entrySet()) {
      String childTable = childEntry.getKey();
      for (Map<String, Object> child : childEntry.getValue()) {
        insert(child, childTable, myCtx, insertFn);
      }
    }
  }
}
```

### 4. `DatabaseDestination` (updated)

**Changes to existing class:**

- Add `private final boolean nestedMode` flag (set to `true` when structure contains nested types — detected from `rawFieldTypes` or first-record inspection).
- In `write(Map<String, Object> record)`: if flat → existing path; if nested → delegate to `DecomposedInsertCoordinator`.
- Remove the `validateFlatRecord()` fail-fast guard (or keep it for non-database modes — N/A here).
- Maintain per-table `PreparedStatement` cache (statements initialised on first record for each table).

**Statement cache:**
```java
// Map from table name → (columnNames, PreparedStatement)
private final Map<String, TableInsertState> statementCache = new LinkedHashMap<>();
```

---

## Implementation Checklist

### Phase 1: Core Decomposition (no DB yet)

- [ ] Create `ParentContext` record
- [ ] Create `NestedRecordDecomposer` with unit tests
  - [ ] Flat record → flat fields only, no children
  - [ ] Single nested object → one child entry
  - [ ] Nested array → N child entries
  - [ ] FK injection from parent context
  - [ ] Multi-level: object containing array (recursive)
  - [ ] Null `id` field (no FK injected)
- [ ] Create `DecomposedInsertCoordinator` with unit tests (mock `insertFn`)
  - [ ] Depth-first ordering verified (parent before children)
  - [ ] Recursive two-level nesting
  - [ ] Array generates correct N child calls

### Phase 2: DatabaseDestination Integration

- [ ] Add `nestedMode` detection in `open()` (inspect `rawFieldTypes` for `object[` / `array[object[` prefixes)
- [ ] Add per-table `PreparedStatement` cache (`Map<String, TableInsertState>`)
- [ ] Wire `DecomposedInsertCoordinator` into `write()` when `nestedMode = true`
- [ ] Remove `validateFlatRecord()` from nested path (decomposer handles it)
- [ ] Update `flush()` and `close()` to drain all per-table batches
- [ ] Update transaction commit to cover all tables in the same batch

### Phase 3: Integration Tests (Testcontainers PostgreSQL)

Add to `DatabaseDestinationIT.java` or create `DatabaseDestinationNestedIT.java`:

- [ ] `shouldInsertOrderWithLineItems()` — 1 order, 3 line items, verify both tables
- [ ] `shouldInsertOrderWithNoLineItems()` — empty array → only parent row
- [ ] `shouldHandleArrayWithMultipleElements()` — N elements in array, FK correct on all
- [ ] `shouldInsertThreeLevelNesting()` — order → line_item → line_item_attribute
- [ ] `shouldIsolateImmediateParentFk()` — deep child does NOT get root ID
- [ ] `shouldCommitParentAndChildrenTogether()` — per_batch strategy: atomicity verified
- [ ] `shouldCountCorrectRowsAcrossTables()` — 100 orders × 1..5 line items

### Phase 4: E2E Test Update

- [ ] Add nested structure job to `config/jobs/e2e_test_database_order.yaml`
- [ ] Add `order.yaml` and `line_item.yaml` to `config/structures/`
- [ ] Update `benchmarks/run_e2e_test.sh` to include nested DB test
- [ ] Verify row counts in both tables after run

### Phase 5: Documentation

- [ ] Update `DATABASE-DESTINATION-PLANNING.md` Stage 2 checklist (tick items off)
- [ ] Update `BACKLOG.md` Stage 2 entry from "Deferred" to "Complete"
- [ ] Update `TASK-CATALOG.md` stats
- [ ] Update `DESIGN.md` with Stage 2 section

---

## Acceptance Criteria

1. `./gradlew :destinations:test` — all unit tests pass including new decomposition tests
2. `./gradlew :destinations:integrationTest` — all integration tests pass including nested IT
3. Flat-only jobs (Stage 1 path) are not affected — all existing 9 IT tests still pass
4. E2E nested database run inserts correct counts into both tables
5. `./gradlew spotlessCheck` — no formatting violations

---

## Known Risks

| Risk | Mitigation |
|------|-----------|
| First-record inspection may miss `object[]` fields if they're null | Pre-inspect `rawFieldTypes` map (available at `open()` time) instead |
| Per-table statement cache grows unbounded for dynamic table names | Table names come from YAML structure — bounded by definition |
| Deep recursion (>10 levels) → stack overflow | Practically impossible in test data scenarios; add depth guard as defensive check |
| Child `id` field not present → FK injection skipped silently | Log warning; document convention requirement |

---

**Created:** March 9, 2026
**Owner:** TBD
