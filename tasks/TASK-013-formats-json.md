# TASK-013: Formats Module - JSON Serializer

**Status**: ⏸️ Not Started  
**Priority**: P1 (High)  
**Phase**: 3 - Output Formats  
**Dependencies**: TASK-007 (Primitive Generators), TASK-008 (Composite Generators)  
**Human Supervision**: NONE (fully automated)

---

## Objective

Implement JSON format serializer that converts generated records to Newline-Delimited JSON (NDJSON) format with field alias support, proper escaping, and UTF-8 encoding.

---

## Background

Generated records are Map<String, Object> with potentially nested structures and arrays. Need to serialize to JSON for:
- File output (one JSON object per line for streaming)
- Kafka messages (JSON payload)
- HTTP APIs (JSON response body)

**Output Format**: NDJSON (Newline-Delimited JSON)
- One complete JSON object per line
- No trailing comma after last record
- UTF-8 encoding
- Proper escaping of special characters

---

## Implementation Details

### Step 1: Create FormatSerializer Interface

**File**: `formats/src/main/java/com/datagenerator/formats/FormatSerializer.java`

```java
package com.datagenerator.formats;

import java.util.Map;

/**
 * Interface for serializing generated records to specific output formats.
 */
public interface FormatSerializer {
    
    /**
     * Serialize a single record to byte array.
     * 
     * @param record Generated record with field name → value mapping
     * @return Serialized bytes (UTF-8 encoded)
     */
    byte[] serialize(Map<String, Object> record);
    
    /**
     * Serialize header (if format requires one, e.g., CSV headers).
     * 
     * @return Header bytes, or empty array if no header needed
     */
    default byte[] serializeHeader() {
        return new byte[0];
    }
    
    /**
     * Get file extension for this format.
     * 
     * @return File extension (e.g., "json", "csv", "proto")
     */
    String getFileExtension();
}
```

---

### Step 2: Create JsonSerializer Implementation

**File**: `formats/src/main/java/com/datagenerator/formats/JsonSerializer.java`

**Implementation**:
```java
package com.datagenerator.formats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON serializer for NDJSON (Newline-Delimited JSON) format.
 * Each record is serialized as a single-line JSON object followed by newline.
 */
@Slf4j
public class JsonSerializer implements FormatSerializer {
    
    private final ObjectMapper objectMapper;
    
    public JsonSerializer() {
        this.objectMapper = createObjectMapper();
    }
    
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Disable pretty-printing (single line per record)
        mapper.disable(SerializationFeature.INDENT_OUTPUT);
        
        // Write dates as ISO-8601 strings, not timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Don't write null values
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        
        return mapper;
    }
    
    @Override
    public byte[] serialize(Map<String, Object> record) {
        try {
            // Serialize to JSON string
            String json = objectMapper.writeValueAsString(record);
            
            // Add newline for NDJSON format
            String ndjson = json + "\n";
            
            // Convert to UTF-8 bytes
            return ndjson.getBytes(StandardCharsets.UTF_8);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize record to JSON: {}", record, e);
            throw new SerializationException("JSON serialization failed", e);
        }
    }
    
    @Override
    public String getFileExtension() {
        return "json";
    }
}
```

---

### Step 3: Create SerializationException

**File**: `formats/src/main/java/com/datagenerator/formats/SerializationException.java`

```java
package com.datagenerator.formats;

/**
 * Exception thrown when serialization fails.
 */
public class SerializationException extends RuntimeException {
    
    public SerializationException(String message) {
        super(message);
    }
    
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

### Step 4: Handle Field Aliases

**Challenge**: Generated records use field names from structure definition, but output should use aliases.

**Solution**: Apply aliases BEFORE serialization (in generator, not serializer).

**File**: `generators/src/main/java/com/datagenerator/generators/ObjectGenerator.java`

**Current code** (simplified):
```java
public Map<String, Object> generate(Random random, TypeConfig config) {
    DataStructure structure = structureRegistry.getStructure(structureName);
    
    Map<String, Object> record = new HashMap<>();
    for (FieldDefinition field : structure.getFields()) {
        DataTypeGenerator<?> generator = factory.createGenerator(field.getType(), structure.getGeolocation());
        Object value = generator.generate(random, /* config */);
        record.put(field.getName(), value); // Uses field name
    }
    return record;
}
```

**Modified code** (apply aliases):
```java
public Map<String, Object> generate(Random random, TypeConfig config) {
    DataStructure structure = structureRegistry.getStructure(structureName);
    
    Map<String, Object> record = new HashMap<>();
    for (FieldDefinition field : structure.getFields()) {
        DataTypeGenerator<?> generator = factory.createGenerator(field.getType(), structure.getGeolocation());
        Object value = generator.generate(random, /* config */);
        
        // Use alias if present, otherwise use field name
        String outputName = field.getAlias() != null ? field.getAlias() : field.getName();
        record.put(outputName, value);
    }
    return record;
}
```

**Verify FieldDefinition has alias field**:
```java
@Value
@Builder
public class FieldDefinition {
    String name;
    String alias; // Optional: output field name
    DataType type;
}
```

---

### Step 5: Handle Special Data Types

**Jackson configuration** for custom types:

1. **LocalDate** (serialize as ISO-8601 string):
   - Already handled by `mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)`
   - Output: `"2025-01-20"` (not epoch milliseconds)

2. **Instant** (serialize as ISO-8601 string):
   - Already handled by same configuration
   - Output: `"2025-01-20T14:30:00Z"`

3. **BigDecimal** (serialize as number, not string):
   - Jackson default behavior (correct)
   - Output: `123.45` (not `"123.45"`)

4. **Nested Maps** (from ObjectType):
   - Jackson recursively serializes nested maps
   - Output: `{"address": {"city": "Milano", "street": "Via Roma"}}`

5. **Lists** (from ArrayType):
   - Jackson serializes lists as JSON arrays
   - Output: `{"tags": ["tag1", "tag2", "tag3"]}`

**No custom serializers needed** - Jackson handles all types correctly.

---

### Step 6: Add Jackson Dependency

**File**: `formats/build.gradle.kts`

**Verify dependency exists**:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))
    implementation(project(":generators"))
    
    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.0") // For Java 8 date/time
}
```

**If not present, add them**.

Run:
```bash
./gradlew :formats:dependencies --configuration compileClasspath
# Verify Jackson appears
```

---

## Acceptance Criteria

- ✅ FormatSerializer interface created with clear contract
- ✅ JsonSerializer implements FormatSerializer
- ✅ Serializes primitives correctly (string, number, boolean, null)
- ✅ Serializes dates as ISO-8601 strings (not epoch milliseconds)
- ✅ Serializes nested objects correctly (recursive maps)
- ✅ Serializes arrays correctly (JSON arrays)
- ✅ Each record on single line (NDJSON format)
- ✅ Newline character appended to each record
- ✅ UTF-8 encoding
- ✅ Field aliases applied (output uses alias, not field name)
- ✅ Special characters escaped (quotes, newlines, backslashes)
- ✅ SerializationException thrown on failure with descriptive message

---

## Testing Requirements

### Unit Tests

**File**: `formats/src/test/java/com/datagenerator/formats/JsonSerializerTest.java`

**Test cases**:

1. **Test Simple Record Serialization**:
```java
@Test
void shouldSerializeSimpleRecordToJson() {
    JsonSerializer serializer = new JsonSerializer();
    
    Map<String, Object> record = Map.of(
        "name", "Mario Rossi",
        "age", 30,
        "active", true
    );
    
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    assertThat(json).contains("\"name\":\"Mario Rossi\"");
    assertThat(json).contains("\"age\":30");
    assertThat(json).contains("\"active\":true");
    assertThat(json).endsWith("\n"); // NDJSON format
    assertThat(json).doesNotContain("\n\n"); // Single line + newline only
}
```

2. **Test Nested Object Serialization**:
```java
@Test
void shouldSerializeNestedObjectToJson() {
    JsonSerializer serializer = new JsonSerializer();
    
    Map<String, Object> address = Map.of(
        "city", "Milano",
        "street", "Via Roma"
    );
    
    Map<String, Object> record = Map.of(
        "name", "Mario Rossi",
        "address", address
    );
    
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    assertThat(json).contains("\"address\":{\"city\":\"Milano\",\"street\":\"Via Roma\"}");
}
```

3. **Test Array Serialization**:
```java
@Test
void shouldSerializeArrayToJson() {
    JsonSerializer serializer = new JsonSerializer();
    
    Map<String, Object> record = Map.of(
        "name", "Mario Rossi",
        "tags", List.of("tag1", "tag2", "tag3")
    );
    
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    assertThat(json).contains("\"tags\":[\"tag1\",\"tag2\",\"tag3\"]");
}
```

4. **Test Date Serialization**:
```java
@Test
void shouldSerializeDateAsIso8601String() {
    JsonSerializer serializer = new JsonSerializer();
    
    LocalDate date = LocalDate.of(2025, 1, 20);
    Map<String, Object> record = Map.of("birthDate", date);
    
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    assertThat(json).contains("\"birthDate\":\"2025-01-20\"");
    assertThat(json).doesNotContain("1737331200000"); // Not epoch milliseconds
}
```

5. **Test Instant Serialization**:
```java
@Test
void shouldSerializeInstantAsIso8601String() {
    JsonSerializer serializer = new JsonSerializer();
    
    Instant instant = Instant.parse("2025-01-20T14:30:00Z");
    Map<String, Object> record = Map.of("createdAt", instant);
    
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    assertThat(json).contains("\"createdAt\":\"2025-01-20T14:30:00Z\"");
}
```

6. **Test BigDecimal Serialization**:
```java
@Test
void shouldSerializeBigDecimalAsNumber() {
    JsonSerializer serializer = new JsonSerializer();
    
    BigDecimal price = new BigDecimal("123.45");
    Map<String, Object> record = Map.of("price", price);
    
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    assertThat(json).contains("\"price\":123.45");
    assertThat(json).doesNotContain("\"price\":\"123.45\""); // Not a string
}
```

7. **Test Special Character Escaping**:
```java
@Test
void shouldEscapeSpecialCharacters() {
    JsonSerializer serializer = new JsonSerializer();
    
    Map<String, Object> record = Map.of(
        "text", "Line 1\nLine 2",
        "quote", "He said \"hello\"",
        "backslash", "C:\\path\\to\\file"
    );
    
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    assertThat(json).contains("\\n"); // Newline escaped
    assertThat(json).contains("\\\""); // Quote escaped
    assertThat(json).contains("\\\\"); // Backslash escaped
}
```

8. **Test Null Value Handling**:
```java
@Test
void shouldOmitNullValues() {
    JsonSerializer serializer = new JsonSerializer();
    
    Map<String, Object> record = new HashMap<>();
    record.put("name", "Mario Rossi");
    record.put("middleName", null);
    
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    assertThat(json).contains("\"name\":\"Mario Rossi\"");
    assertThat(json).doesNotContain("middleName"); // Null omitted
}
```

9. **Test UTF-8 Encoding**:
```java
@Test
void shouldEncodeAsUtf8() {
    JsonSerializer serializer = new JsonSerializer();
    
    Map<String, Object> record = Map.of(
        "name", "François Müller",
        "city", "São Paulo"
    );
    
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    assertThat(json).contains("François Müller");
    assertThat(json).contains("São Paulo");
}
```

10. **Test File Extension**:
```java
@Test
void shouldReturnJsonFileExtension() {
    JsonSerializer serializer = new JsonSerializer();
    
    assertThat(serializer.getFileExtension()).isEqualTo("json");
}
```

**Minimum**: 10 unit tests (as above)

---

### Integration Tests

**File**: `formats/src/test/java/com/datagenerator/formats/JsonSerializationIntegrationTest.java`

**Test end-to-end** with generated records:

```java
@Test
void shouldSerializeGeneratedRecordWithAliases() throws Exception {
    // Given: Generate record with aliases
    String yaml = """
        name: user
        data:
          name:
            datatype: char[5..10]
            alias: "nome"
          age:
            datatype: int[18..65]
            alias: "eta"
        """;
    
    DataStructure structure = parser.parse(yaml);
    ObjectGenerator generator = new ObjectGenerator(structure.getName(), factory, registry);
    
    Random random = new Random(12345L);
    Map<String, Object> record = generator.generate(random, null);
    
    // When: Serialize to JSON
    JsonSerializer serializer = new JsonSerializer();
    byte[] bytes = serializer.serialize(record);
    String json = new String(bytes, StandardCharsets.UTF_8);
    
    // Then: Aliases are used
    assertThat(json).contains("\"nome\""); // Alias, not "name"
    assertThat(json).contains("\"eta\""); // Alias, not "age"
    assertThat(json).doesNotContain("\"name\"");
    assertThat(json).doesNotContain("\"age\"");
}
```

---

## Files Created

- `formats/src/main/java/com/datagenerator/formats/FormatSerializer.java`
- `formats/src/main/java/com/datagenerator/formats/JsonSerializer.java`
- `formats/src/main/java/com/datagenerator/formats/SerializationException.java`
- `formats/src/test/java/com/datagenerator/formats/JsonSerializerTest.java`
- `formats/src/test/java/com/datagenerator/formats/JsonSerializationIntegrationTest.java`

---

## Files Modified

- `formats/build.gradle.kts` (verify Jackson dependencies)
- `generators/src/main/java/com/datagenerator/generators/ObjectGenerator.java` (apply aliases)

---

## Common Issues & Solutions

**Issue**: Date serialized as epoch milliseconds  
**Solution**: Ensure `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` is disabled

**Issue**: JSON pretty-printed (multi-line)  
**Solution**: Ensure `SerializationFeature.INDENT_OUTPUT` is disabled

**Issue**: Field names wrong (aliases not applied)  
**Solution**: Check ObjectGenerator applies aliases when building record map

**Issue**: Special characters not escaped  
**Solution**: Jackson handles escaping automatically - verify test case

**Issue**: UTF-8 characters corrupted  
**Solution**: Ensure `StandardCharsets.UTF_8` used for byte[] → String conversion

---

## Completion Checklist

- [ ] FormatSerializer interface created
- [ ] JsonSerializer implementation complete
- [ ] SerializationException created
- [ ] Jackson dependencies verified
- [ ] ObjectGenerator applies aliases
- [ ] Unit tests pass (10 tests)
- [ ] Integration test passes
- [ ] Build succeeds: `./gradlew :formats:build`
- [ ] All tests pass: `./gradlew :formats:test`
- [ ] Code formatted: `./gradlew :formats:spotlessApply`
- [ ] NDJSON format verified (one line per record)
- [ ] UTF-8 encoding verified
- [ ] Special character escaping verified

---

**Estimated Effort**: 3-4 hours  
**Complexity**: Low (straightforward Jackson usage)
