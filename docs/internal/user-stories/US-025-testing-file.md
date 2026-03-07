# US-025: File Destination Integration Tests

**Status**: 🔒 Blocked  
**Priority**: P1 (High)  
**Phase**: 6 - Testing & Quality  
**Dependencies**: US-016, US-022

---

## User Story

As a **developer**, I want **integration tests for file destination** so that **I can verify files are written correctly with various formats and compression options**.

---

## Acceptance Criteria

- ✅ Write JSON files and verify content
- ✅ Write CSV files with headers and verify content
- ✅ Write compressed files (gzip) and verify decompression works
- ✅ Test append mode (incremental writes)
- ✅ Verify file atomicity (proper close and flush)
- ✅ Test parent directory creation
- ✅ Verify file permissions (readable)
- ✅ Verify UTF-8 encoding
- ✅ Test with large files (10,000+ records)

---

## Implementation Notes

### Test Scenarios
1. **JSON output**: Generate 100 records, verify JSON file parseable
2. **CSV output**: Generate 100 records, verify CSV with headers
3. **Gzip compression**: Generate compressed file, decompress and verify
4. **Append mode**: Write 50 records, append 50 more, verify 100 total
5. **Large files**: Generate 10,000 records, verify file size and integrity

### File Verification
```java
// Read and verify JSON
List<String> lines = Files.readAllLines(outputFile, StandardCharsets.UTF_8);
assertThat(lines).hasSize(100);

// Parse each line as JSON
ObjectMapper mapper = new ObjectMapper();
for (String line : lines) {
    Map<String, Object> record = mapper.readValue(line, Map.class);
    assertThat(record).containsKey("id");
}
```

### Compression Verification
```java
// Verify gzip file
try (GZIPInputStream gis = new GZIPInputStream(
        Files.newInputStream(gzipFile))) {
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(gis, StandardCharsets.UTF_8));
    long count = reader.lines().count();
    assertThat(count).isEqualTo(100);
}
```

---

## Testing Requirements

### Functional Tests
- JSON file written and parseable
- CSV file written with correct headers
- Compressed files readable
- Append mode works correctly
- Parent directories created automatically

### Data Integrity Tests
- All records present in file
- UTF-8 encoding preserved
- No truncated records
- No missing data

### Performance Tests
- Large file generation (10,000+ records)
- Compare compressed vs uncompressed file sizes
- Measure write throughput

### Error Tests
- Write to read-only directory (permission error)
- Invalid file path (clear error)
- Disk full simulation (if possible)

---

## Definition of Done

- [ ] Tests for JSON file writing
- [ ] Tests for CSV file writing
- [ ] Tests for gzip compression
- [ ] Tests for append mode
- [ ] Tests for large files
- [ ] Tests for error scenarios
- [ ] Verification of file content and integrity
- [ ] Test coverage >= 90%
- [ ] Tests pass consistently
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
