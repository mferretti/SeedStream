# US-024: Database Integration Tests

**Status**: 🔒 Blocked  
**Priority**: P1 (High)  
**Phase**: 6 - Testing & Quality  
**Dependencies**: US-018, US-022

---

## User Story

As a **developer**, I want **integration tests for database destination** so that **I can verify records are inserted correctly to PostgreSQL and MySQL with proper batching and transaction management**.

---

## Acceptance Criteria

- ✅ Insert records to PostgreSQL using Testcontainers
- ✅ Insert records to MySQL using Testcontainers
- ✅ Verify data in tables with SELECT queries
- ✅ Test batch insert behavior (batch_size parameter)
- ✅ Test transaction commit per batch
- ✅ Test transaction rollback on error
- ✅ Test connection pooling (concurrent inserts)
- ✅ Verify data integrity (all fields correct)
- ✅ Test with different data types (strings, numbers, dates)

---

## Implementation Notes

### Test Scenarios
1. **PostgreSQL inserts**: Create table, insert 100 records, verify with SELECT
2. **MySQL inserts**: Create table, insert 100 records, verify with SELECT
3. **Batch behavior**: Different batch sizes, verify commits per batch
4. **Error handling**: Constraint violation, rollback verification
5. **Connection pooling**: Multiple concurrent insert operations

### Database Setup
```java
@BeforeEach
void setupDatabase() {
    // Create test table with appropriate schema
    try (Connection conn = postgres.createConnection()) {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE test_records (id INT, name VARCHAR(100), active BOOLEAN)");
    }
}
```

### Verification Queries
```java
// Verify records inserted
try (Connection conn = postgres.createConnection()) {
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_records");
    rs.next();
    assertThat(rs.getInt(1)).isEqualTo(100);
}
```

---

## Testing Requirements

### Functional Tests
- PostgreSQL: 100 records inserted and verified
- MySQL: 100 records inserted and verified
- Data types handled correctly (int, string, boolean, date)
- Batch commits work as expected

### Performance Tests
- Measure insert throughput with batching
- Compare different batch sizes (100, 1000, 10000)
- Verify batch inserts much faster than individual

### Error Tests
- Constraint violations handled gracefully
- Transaction rollback on error
- Invalid table name (clear error)
- Schema mismatch (clear error)
- Connection timeout

### Concurrency Tests
- Multiple threads inserting concurrently
- Verify connection pool reuse
- No connection leaks

---

## Definition of Done

- [ ] Test class extends IntegrationTest
- [ ] Tests for PostgreSQL inserts
- [ ] Tests for MySQL inserts
- [ ] Tests for batch behavior
- [ ] Tests for transaction management
- [ ] Tests for error scenarios
- [ ] Tests for concurrent inserts
- [ ] Test coverage >= 85%
- [ ] Tests pass consistently
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
