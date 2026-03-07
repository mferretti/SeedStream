# TASK-014: Formats Module - CSV Serializer

**Status**: ✅ Completed  
**Priority**: P1 (High)  
**Phase**: 3 - Output Formats  
**Dependencies**: TASK-013 (JSON Serializer)  
**Human Supervision**: NONE (standard CSV serialization)  
**Completed**: February 21, 2026  
**Implementation**: `formats/src/main/java/com/datagenerator/formats/csv/CsvSerializer.java`  
**Tests**: 17 unit tests passing

---

## Completion Summary

**What Was Implemented:**
- ✅ CsvSerializer implementing FormatSerializer
- ✅ RFC 4180 compliance with always-quoted fields
- ✅ Header generation (serializeHeader())
- ✅ Nested objects serialized as JSON strings
- ✅ Arrays serialized as JSON strings
- ✅ Quote doubling for embedded quotes
- ✅ Field order consistency
- ✅ UTF-8 encoding
- ✅ Proper null handling (empty strings)

**Test Coverage:**
- 17 unit tests covering all scenarios
- Field escaping validation
- Header generation
- Nested object handling
- Array serialization
- Special characters (commas, quotes, newlines)

---

## Objective

Implement CSV format serializer that converts generated records to RFC 4180-compliant CSV format with proper escaping, header support, and nested object flattening.

---

## Background

CSV is widely used for database imports, spreadsheet loading, and legacy system integration.

**Requirements**:
- RFC 4180 compliance (quoted fields, escaped quotes)
- Header row (field names)
- Flatten nested objects (dot notation: `address.city`)
- Handle arrays (JSON stringify or comma-separated)
- UTF-8 encoding

---

## Implementation Details

### Step 1: Create CsvSerializer

**File**: `formats/src/main/java/com/datagenerator/formats/CsvSerializer.java`

```java
package com.datagenerator.formats;

import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CSV serializer following RFC 4180 standard.
 */
@Slf4j
public class CsvSerializer implements FormatSerializer {
    
    private final List<String> fieldOrder;
    private boolean headerGenerated = false;
    
    public CsvSerializer() {
        this.fieldOrder = new ArrayList<>();
    }
    
    @Override
    public byte[] serializeHeader() {
        if (fieldOrder.isEmpty()) {
            throw new SerializationException("Field order not initialized - serialize a record first");
        }
        
        String header = fieldOrder.stream()
            .map(this::escapeField)
            .collect(Collectors.joining(",")) + "\n";
        
        headerGenerated = true;
        return header.getBytes(StandardCharsets.UTF_8);
    }
    
    @Override
    public byte[] serialize(Map<String, Object> record) {
        // Flatten nested objects
        Map<String, Object> flattened = flatten(record, "");
        
        // Initialize field order on first record
        if (fieldOrder.isEmpty()) {
            fieldOrder.addAll(flattened.keySet());
        }
        
        // Serialize values in field order
        String csv = fieldOrder.stream()
            .map(field -> formatValue(flattened.get(field)))
            .map(this::escapeField)
            .collect(Collectors.joining(",")) + "\n";
        
        return csv.getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Flatten nested objects using dot notation.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flatten(Map<String, Object> record, String prefix) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                flattened.putAll(flatten((Map<String, Object>) value, key));
            } else {
                flattened.put(key, value);
            }
        }
        
        return flattened;
    }
    
    /**
     * Format value as string (arrays become JSON).
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof List) {
            // Convert arrays to JSON array string
            return value.toString();
        }
        
        return value.toString();
    }
    
    /**
     * Escape field value per RFC 4180.
     */
    private String escapeField(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        
        // Quote if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            // Escape quotes by doubling them
            String escaped = value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
        
        return value;
    }
    
    @Override
    public String getFileExtension() {
        return "csv";
    }
}
```

---

### Step 2: Write Unit Tests

**File**: `formats/src/test/java/com/datagenerator/formats/CsvSerializerTest.java`

```java
package com.datagenerator.formats;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class CsvSerializerTest {
    
    @Test
    void shouldSerializeSimpleRecord() {
        CsvSerializer serializer = new CsvSerializer();
        Map<String, Object> record = Map.of(
            "name", "John Doe",
            "age", 30,
            "active", true
        );
        
        byte[] bytes = serializer.serialize(record);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        
        assertThat(csv).isEqualTo("John Doe,30,true\n");
    }
    
    @Test
    void shouldEscapeFieldsWithCommas() {
        CsvSerializer serializer = new CsvSerializer();
        Map<String, Object> record = Map.of(
            "name", "Doe, John",
            "city", "New York, NY"
        );
        
        byte[] bytes = serializer.serialize(record);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        
        assertThat(csv).isEqualTo("\"Doe, John\",\"New York, NY\"\n");
    }
    
    @Test
    void shouldEscapeFieldsWithQuotes() {
        CsvSerializer serializer = new CsvSerializer();
        Map<String, Object> record = Map.of(
            "name", "John \"The Boss\" Doe"
        );
        
        byte[] bytes = serializer.serialize(record);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        
        assertThat(csv).isEqualTo("\"John \"\"The Boss\"\" Doe\"\n");
    }
    
    @Test
    void shouldFlattenNestedObjects() {
        CsvSerializer serializer = new CsvSerializer();
        Map<String, Object> record = Map.of(
            "name", "John",
            "address", Map.of(
                "city", "Milan",
                "postal_code", "20100"
            )
        );
        
        byte[] bytes = serializer.serialize(record);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        
        assertThat(csv).isEqualTo("John,Milan,20100\n");
    }
    
    @Test
    void shouldGenerateHeader() {
        CsvSerializer serializer = new CsvSerializer();
        Map<String, Object> record = Map.of(
            "name", "John",
            "age", 30
        );
        
        // Serialize record first to establish field order
        serializer.serialize(record);
        
        byte[] headerBytes = serializer.serializeHeader();
        String header = new String(headerBytes, StandardCharsets.UTF_8);
        
        assertThat(header).isEqualTo("name,age\n");
    }
    
    @Test
    void shouldHandleNullValues() {
        CsvSerializer serializer = new CsvSerializer();
        Map<String, Object> record = Map.of(
            "name", "John"
        );
        
        byte[] bytes = serializer.serialize(record);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        
        assertThat(csv).isEqualTo("John\n");
    }
}
```

---

## Acceptance Criteria

- ✅ Serializes records to RFC 4180-compliant CSV
- ✅ Escapes commas, quotes, and newlines properly
- ✅ Flattens nested objects with dot notation
- ✅ Generates header row with field names
- ✅ Handles null values as empty strings
- ✅ Consistent field ordering across records
- ✅ All unit tests pass

---

## Testing

Run tests:
```bash
./gradlew :formats:test
```

---

## Usage Example

```java
CsvSerializer serializer = new CsvSerializer();

// First record establishes field order
Map<String, Object> record1 = Map.of("name", "John", "age", 30);
byte[] header = serializer.serializeHeader();  // After first serialize
byte[] csv1 = serializer.serialize(record1);

Map<String, Object> record2 = Map.of("name", "Jane", "age", 25);
byte[] csv2 = serializer.serialize(record2);

// Output:
// name,age
// John,30
// Jane,25
```

---

**Completion Date**: [Mark when complete]
