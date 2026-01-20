# US-014: CSV Output Format

**Status**: ⏸️ Not Started  
**Priority**: P1 (High)  
**Phase**: 3 - Output Formats  
**Dependencies**: US-013

---

## User Story

As a **data analyst**, I want **generated records serialized to CSV format** so that **I can import test data into spreadsheets, databases, and legacy systems that require CSV**.

---

## Acceptance Criteria

- ✅ CSV serializer following RFC 4180 standard
- ✅ Header row with field names
- ✅ Proper field escaping (quotes, commas, newlines)
- ✅ Nested objects flattened with dot notation (e.g., `address.city`)
- ✅ Arrays converted to JSON string representation
- ✅ UTF-8 encoding
- ✅ Field order consistent across records
- ✅ Null values represented as empty strings
- ✅ File extension: `.csv`

---

## Implementation Notes

### RFC 4180 Compliance
- Fields containing comma, quote, or newline must be quoted
- Quotes inside fields must be doubled ("" for ")
- Line endings: CRLF or LF (LF for simplicity)
- Header row optional but recommended

### CsvSerializer Behavior
1. First record establishes field order (flatten nested objects)
2. serializeHeader() returns CSV header row
3. serialize() returns data row in established field order
4. Escape fields according to RFC 4180
5. Maintain field order for all subsequent records

### Nested Object Flattening
```
Input: {name: "John", address: {city: "Milan", zip: "20100"}}
Output columns: name, address.city, address.zip
CSV row: John,Milan,20100
```

### Array Handling
Convert arrays to JSON string for CSV field:
```
Input: {items: [1, 2, 3]}
CSV: "[1, 2, 3]"
```

---

## Testing Requirements

### Unit Tests
- Simple record serialization
- Field escaping (commas, quotes, newlines)
- Header generation
- Nested object flattening
- Array conversion to string
- Field order consistency
- Null value handling

### Integration Tests
- Generate 1000 records to CSV
- Parse CSV back and verify data
- Test with complex nested structures
- Import CSV into actual spreadsheet (manual verification)

### Edge Cases
- Record with only nested objects (no top-level fields)
- Record with deeply nested structures (3+ levels)
- Record with special characters in all fields
- Record with empty arrays
- Record with null values

---

## Definition of Done

- [ ] CsvSerializer implements FormatSerializer
- [ ] RFC 4180 compliant escaping
- [ ] Header generation works
- [ ] Nested object flattening implemented
- [ ] Array to string conversion
- [ ] Field order consistency maintained
- [ ] Unit tests for all scenarios
- [ ] Integration tests with real CSV parsing
- [ ] Test coverage >= 90%
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
