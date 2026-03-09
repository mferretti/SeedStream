# TASK-042: JDBC Type Binding Strategy — Option A vs Option B Decision

**Status**: 📋 Planned
**Priority**: P1 (High — must resolve before Stage 2 database work)
**Phase**: 8 - Database Destinations
**Dependencies**: TASK-018 (Stage 1 must be in production and revealing edge cases)
**Human Supervision**: HIGH (architectural decision)

---

## Objective

Evaluate whether the current Stage 1 approach (Option A: `instanceof` on generated Java values) is sufficient long-term, or whether the generation pipeline needs to carry `DataType` metadata through to the destination layer (Option B).

**This decision must be made before Stage 2 database work begins** (nested objects, FK injection, complex type mapping).

---

## Background

### Stage 1 Approach (Option A — currently implemented)

`DestinationAdapter.write(Map<String, Object>)` receives generated values with no type metadata. `JdbcTypeMapper` infers the JDBC binding from the Java runtime type:

```java
if (value instanceof Integer i)    ps.setInt(i, i);
if (value instanceof BigDecimal d) ps.setBigDecimal(i, d);
if (value instanceof String s)     ps.setString(i, s);
if (value instanceof LocalDate d)  ps.setDate(i, Date.valueOf(d));
if (value instanceof Instant t)    ps.setTimestamp(i, Timestamp.from(t));
// etc.
```

**Works because**: Each `DataType` always produces the same Java type (e.g., `IntegerType` → `Integer`, `CharType` → `String`).

### Known Limitations of Option A

1. **Datafaker types that produce numbers**: A `CustomDatafakerType` (e.g. `age`, `year`, `quantity`) generates a `String` because Datafaker returns strings. If the DB column is `INT`, the binding fails or requires a cast. The mapper can't distinguish "this string represents an age" from "this string represents a name".

2. **Future type ambiguity**: Statistical distributions, expressions, or custom generators may produce types that don't map cleanly to a single `setXxx()`.

3. **Null handling**: With Option A, a null value has no type info → can't call `setNull(i, sqlType)` correctly.

### Option B — DataType metadata in write pipeline

Thread `DataType` alongside each generated value, giving destinations full type information:

```java
// Option B: Pair of (value, DataType) instead of raw value
Map<String, TypedValue> record  // TypedValue = Object value + DataType type

// Or: separate metadata map per record
record.write(values, schema)    // schema carries DataType per field name
```

**Pros**:
- Accurate JDBC binding regardless of Java runtime type
- Correct `setNull(i, sqlType)` for null values
- Enables schema validation (verify YAML fields match DB columns)
- Cleaner Stage 2 FK injection (DataType carries structure/field info)

**Cons**:
- Breaking change to `DestinationAdapter` interface
- All destinations (File, Kafka) must be updated or given a compatibility shim
- More complex serialization pipeline

---

## Decision Spike Tasks

1. **Audit Option A gaps in production**: Run Stage 1 with real schemas. Catalog any type mismatches or binding failures. Assess whether Datafaker numeric types actually cause issues in practice.

2. **Design Option B interface**: Propose a concrete API change that is backward-compatible (e.g., overloaded `write()`, optional metadata, or a `TypedRecord` wrapper). Evaluate effort to migrate Kafka and File destinations.

3. **Decide**: Based on audit results and Stage 2 requirements (FK injection needs type info anyway), choose the approach and document the rationale in `DESIGN.md`.

---

## Acceptance Criteria

- [ ] Option A gap analysis complete (real-world type mismatch cases documented)
- [ ] Option B interface proposal written (concrete Java API, migration cost estimated)
- [ ] Decision recorded in `DESIGN.md` with rationale
- [ ] If Option B chosen: migration task created and scheduled before Stage 2 database work

---

## References

- `docs/DATABASE-DESTINATION-PLANNING.md` — Type Binding Strategy section
- `destinations/src/main/java/com/datagenerator/destinations/DestinationAdapter.java`
- `destinations/src/main/java/com/datagenerator/destinations/database/JdbcTypeMapper.java` (Stage 1 implementation)
- TASK-018 (Database Stage 1)

---

**Last Updated**: March 9, 2026
