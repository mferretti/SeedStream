# US-013: JSON Output Format

**Status**: ✅ Completed  
**Priority**: P1 (High)  
**Phase**: 3 - Output Formats  
**Dependencies**: US-007, US-008  
**Completed**: February 21, 2026  
**Implementation**: `formats/src/main/java/com/datagenerator/formats/json/JsonSerializer.java`
**Tests**: 16 unit tests passing

---

## User Story

As a **test data consumer**, I want **generated records serialized to JSON format** so that **I can use the data with JSON-based systems, Kafka topics, and REST APIs**.

---

## Acceptance Criteria

- ✅ FormatSerializer interface for all serializers
- ✅ JsonSerializer implements NDJSON (Newline-Delimited JSON) format
- ✅ One JSON object per line for streaming
- ✅ UTF-8 encoding
- ✅ ISO-8601 date/timestamp formatting (not epoch)
- ✅ Null values excluded from output
- ✅ No pretty-printing (single line per record)
- ✅ Field aliases respected in output
- ✅ SerializationException with clear error messages
- ✅ File extension: `.json`

---

## Implementation Notes

### FormatSerializer Interface
```java
public interface FormatSerializer {
    byte[] serialize(Map<String, Object> record);
    byte[] serializeHeader(); // For formats like CSV
    String getFileExtension();
}
```

### JsonSerializer Implementation
- Use Jackson ObjectMapper with:
  - Disabled pretty-printing (single line)
  - Disabled date-as-timestamp (use ISO-8601)
  - Non-null inclusion (skip null values)
- Serialize record to JSON string
- Append newline character
- Convert to UTF-8 bytes

### NDJSON Format
Each record is a complete JSON object followed by newline:
```
{"id":1,"name":"John","active":true}
{"id":2,"name":"Jane","active":false}
```

Benefits:
- Streamable (process line by line)
- No array wrapping needed
- Works with log aggregation systems
- Easy to split and merge files

---

## Testing Requirements

### Unit Tests
- Serialize simple record to JSON
- Verify single line output (no newlines in JSON)
- UTF-8 encoding verified
- Dates formatted as ISO-8601
- Null values excluded
- Special characters escaped properly
- Nested objects serialized correctly
- Arrays serialized correctly

### Integration Tests
- Serialize 1000 records
- Parse output back with JSON parser
- Verify all fields present and correct
- Test with complex nested structures

### Edge Cases
- Empty record (just braces)
- Record with special characters (quotes, newlines)
- Record with Unicode characters
- Very large records

---

## Definition of Done

- ✅ FormatSerializer interface created
- ✅ JsonSerializer implemented with Jackson
- ✅ NDJSON format (one object per line)
- ✅ UTF-8 encoding
- ✅ ISO-8601 date formatting
- ✅ SerializationException class created
- ✅ Unit tests for serialization (16 tests passing)
- ✅ Compact output (no whitespace)
- ✅ Proper null handling
- ✅ Nested objects and arrays supported
- ✅ Special character escaping
- ✅ Field aliases respected
- ✅ Code follows project style guidelines
- [ ] Integration tests with complex structures
- [ ] Test coverage >= 90%
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
