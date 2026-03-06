# US-018: Database Output Destination

**Status**: ⏸️ Not Started  
**Priority**: P1 (High)  
**Phase**: 4 - Destinations  
**Dependencies**: US-013, US-016

---

## User Story

As a **database engineer**, I want **to insert generated records directly into databases** so that **I can populate test databases for load testing, integration tests, and data migration scenarios**.

---

## Acceptance Criteria

- ✅ DatabaseDestination inserts records to relational databases
- ✅ Support for PostgreSQL and MySQL
- ✅ HikariCP connection pooling for performance
- ✅ Batch inserts (configurable batch size, default 1000)
- ✅ Transaction management (commit per batch)
- ✅ Parse JSON records to extract field values
- ✅ Dynamic SQL generation based on table name and fields
- ✅ Configurable connection pool settings
- ✅ Rollback on batch failure
- ✅ Progress logging (per batch)

---

## Implementation Notes

### DatabaseDestination Configuration
From job YAML `conf` section:
- **url**: JDBC connection URL
- **username**: Database username
- **password**: Database password
- **table**: Target table name
- **batch_size**: Records per batch (default: 1000)
- **pool_size**: Connection pool size (default: 10)
- **min_idle**: Minimum idle connections (default: 2)

### HikariCP Integration
- Create HikariDataSource in constructor
- Configure connection pool parameters
- Proper cleanup on close

### Batch Insert Flow
1. Accumulate records in memory batch
2. When batch size reached or flush() called:
   - Get connection from pool
   - Prepare INSERT statement
   - Add each record to batch
   - Execute batch
   - Commit transaction
   - Clear batch

### SQL Generation
Dynamic SQL based on first record:
```sql
INSERT INTO table_name (field1, field2, field3)
VALUES (?, ?, ?), (?, ?, ?), ...
```

### JSON Parsing
Parse serialized JSON to extract field names and values for SQL parameters.

---

## Testing Requirements

### Unit Tests (Mocked)
- Build HikariCP configuration
- Validate required configuration
- SQL statement generation
- Batch accumulation logic

### Integration Tests (Testcontainers)
- Insert 100 records to PostgreSQL
- Insert 100 records to MySQL
- Verify data in tables with SELECT queries
- Test batch size behavior
- Test transaction rollback on error
- Test connection pooling (multiple concurrent inserts)

### Performance Tests
- Measure insert throughput (records/second)
- Test with different batch sizes
- Compare with single inserts (should be much faster)

### Edge Cases
- Table doesn't exist (clear error)
- Schema mismatch (clear error)
- Constraint violations (handle gracefully)
- Connection timeout
- Database unavailable

---

## Definition of Done

- [ ] DatabaseDestination implements DestinationAdapter
- [ ] HikariCP connection pooling configured
- [ ] Batch insert implementation
- [ ] Dynamic SQL generation
- [ ] JSON record parsing
- [ ] Transaction management (commit/rollback)
- [ ] Unit tests with mocked JDBC
- [ ] Integration tests with Testcontainers (Postgres + MySQL)
- [ ] Performance tests showing batch insert benefits
- [ ] Test coverage >= 85%
- [ ] Documentation with configuration examples
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
