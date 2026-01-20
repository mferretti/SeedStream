# TASK-018: Destinations Module - Database Adapter

**Status**: ⏸️ Not Started  
**Priority**: P1 (High)  
**Phase**: 4 - Destinations  
**Dependencies**: TASK-013 (JSON Serializer), TASK-016 (File Destination)  
**Human Supervision**: MEDIUM (SQL generation, connection pooling)

---

## Objective

Implement database destination adapter that inserts generated records into relational databases (PostgreSQL, MySQL) using JDBC with HikariCP connection pooling and batch inserts.

---

## Background

Databases are a common destination for test data:
- Populate test databases
- Load testing database performance
- Integration test data setup

**Requirements**:
- Connection pooling (HikariCP)
- Batch inserts for performance
- Support PostgreSQL and MySQL
- Auto-create tables from structure definitions
- Transaction management
- Proper error handling

---

## Implementation Details

### Step 1: Create DatabaseDestination

**File**: `destinations/src/main/java/com/datagenerator/destinations/DatabaseDestination.java`

```java
package com.datagenerator.destinations;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Database destination adapter using JDBC and HikariCP.
 */
@Slf4j
public class DatabaseDestination implements DestinationAdapter {
    
    private final HikariDataSource dataSource;
    private final String tableName;
    private final List<String> fieldNames;
    private final int batchSize;
    private final List<byte[]> batch;
    private Connection connection;
    private PreparedStatement statement;
    private int batchCount = 0;
    
    public DatabaseDestination(Map<String, Object> config) {
        this.tableName = (String) config.get("table");
        if (tableName == null) {
            throw new DestinationException("Database table name is required");
        }
        
        this.batchSize = (Integer) config.getOrDefault("batch_size", 1000);
        this.batch = new ArrayList<>(batchSize);
        this.fieldNames = new ArrayList<>();
        
        // Initialize HikariCP connection pool
        HikariConfig hikariConfig = buildHikariConfig(config);
        this.dataSource = new HikariDataSource(hikariConfig);
        
        log.info("Created database destination: table={}, batchSize={}", 
            tableName, batchSize);
    }
    
    private HikariConfig buildHikariConfig(Map<String, Object> config) {
        HikariConfig hikariConfig = new HikariConfig();
        
        String url = (String) config.get("url");
        if (url == null) {
            throw new DestinationException("Database URL is required");
        }
        hikariConfig.setJdbcUrl(url);
        
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        if (username != null) {
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
        }
        
        // Connection pool settings
        hikariConfig.setMaximumPoolSize(
            (Integer) config.getOrDefault("pool_size", 10));
        hikariConfig.setMinimumIdle(
            (Integer) config.getOrDefault("min_idle", 2));
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        
        return hikariConfig;
    }
    
    @Override
    public void write(byte[] record) {
        batch.add(record);
        
        if (batch.size() >= batchSize) {
            flush();
        }
    }
    
    @Override
    public void flush() {
        if (batch.isEmpty()) {
            return;
        }
        
        try {
            // Parse first record to get field structure
            if (fieldNames.isEmpty()) {
                initializeFieldNames(batch.get(0));
            }
            
            // Prepare statement if needed
            if (statement == null) {
                connection = dataSource.getConnection();
                connection.setAutoCommit(false);
                statement = prepareInsertStatement();
            }
            
            // Add all records to batch
            for (byte[] recordBytes : batch) {
                Map<String, Object> record = parseRecord(recordBytes);
                addToBatch(record);
            }
            
            // Execute batch
            int[] results = statement.executeBatch();
            connection.commit();
            
            batchCount++;
            log.info("Inserted {} records (batch #{})", batch.size(), batchCount);
            
            batch.clear();
            
        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException re) {
                log.error("Failed to rollback", re);
            }
            throw new DestinationException("Failed to insert batch", e);
        }
    }
    
    private void initializeFieldNames(byte[] recordBytes) {
        Map<String, Object> record = parseRecord(recordBytes);
        fieldNames.addAll(record.keySet());
    }
    
    private Map<String, Object> parseRecord(byte[] recordBytes) {
        // Parse JSON record
        try {
            String json = new String(recordBytes, java.nio.charset.StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new DestinationException("Failed to parse record JSON", e);
        }
    }
    
    private PreparedStatement prepareInsertStatement() throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        sql.append(String.join(", ", fieldNames));
        sql.append(") VALUES (");
        sql.append("?, ".repeat(fieldNames.size()));
        sql.setLength(sql.length() - 2); // Remove last ", "
        sql.append(")");
        
        log.debug("Prepared statement: {}", sql);
        return connection.prepareStatement(sql.toString());
    }
    
    private void addToBatch(Map<String, Object> record) throws SQLException {
        for (int i = 0; i < fieldNames.size(); i++) {
            Object value = record.get(fieldNames.get(i));
            statement.setObject(i + 1, value);
        }
        statement.addBatch();
    }
    
    @Override
    public void close() throws Exception {
        try {
            flush(); // Flush remaining records
            
            if (statement != null) {
                statement.close();
            }
            if (connection != null) {
                connection.close();
            }
            if (dataSource != null) {
                dataSource.close();
            }
            
            log.info("Closed database destination");
        } catch (SQLException e) {
            throw new DestinationException("Failed to close database connection", e);
        }
    }
}
```

---

## Acceptance Criteria

- ✅ Connects to databases via JDBC
- ✅ Uses HikariCP connection pooling
- ✅ Batch inserts for performance
- ✅ Supports PostgreSQL and MySQL
- ✅ Transaction management (rollback on error)
- ✅ Proper resource cleanup
- ✅ Unit tests pass

---

## Configuration Example

```yaml
source: address.yaml
seed:
  type: embedded
  value: 12345
destination: database
conf:
  url: jdbc:postgresql://localhost:5432/testdb
  username: postgres
  password: secret
  table: addresses
  batch_size: 1000
  pool_size: 10
```

---

## Testing

```bash
./gradlew :destinations:test
```

Integration tests (TASK-024):
```bash
./gradlew :destinations:integrationTest
```

---

**Completion Date**: [Mark when complete]
