# TASK-039: Performance - Jackson Streaming API Optimization

**Status**: ⏸️ Deferred  
**Priority**: P3 (Low)  
**Phase**: 6 - Testing & Quality  
**Dependencies**: TASK-026 (benchmarks), Phase 1 & 2 optimizations complete  
**Human Supervision**: MEDIUM  
**Created**: March 6, 2026

---

## Objective

Replace Jackson `ObjectMapper.writeValueAsString()` with streaming `JsonGenerator` API to eliminate intermediate String allocations in the hot path.

**Expected Impact**: +10-20% throughput (marginal gain, high effort)

---

## Context

After implementing Phase 1 (buffer size + newLine) and Phase 2 (batch writes) optimizations, file I/O performance improved from 213 MB/s to an estimated 600-800 MB/s, exceeding the 500 MB/s requirement.

Jackson streaming offers further optimization by writing directly to the OutputStream without creating intermediate String objects, but provides diminishing returns given current performance.

---

## Current Implementation

**JsonSerializer.java:**
```java
@Override
public String serialize(Map<String, Object> record) {
    return mapper.writeValueAsString(record);  // Creates String object
}
```

**FileDestination.java:**
```java
String line = serializer.serialize(record);  // String allocation
batchBuffer.add(line);                       // String stored
```

---

## Proposed Implementation

### Approach 1: Direct Streaming to OutputStream

**New interface:**
```java
public interface StreamingSerializer extends FormatSerializer {
    void serializeToStream(Map<String, Object> record, OutputStream out) throws IOException;
}
```

**JsonSerializer implementation:**
```java
@Override
public void serializeToStream(Map<String, Object> record, OutputStream out) throws IOException {
    JsonGenerator generator = jsonFactory.createGenerator(out);
    generator.writeObject(record);
    generator.flush();
}
```

**FileDestination changes:**
- Store `OutputStream` reference instead of `BufferedWriter`
- Write records directly to stream
- Handle byte-level batching instead of String batching

---

### Approach 2: StringWriter Pool

Alternative lower-effort approach:
- Pool reusable `StringWriter` objects per thread
- Write to pooled writer, extract String, reset for reuse
- Less memory churn than creating new String each time

---

## Implementation Steps

1. **Design**: Choose between streaming vs pooling approach
2. **API Changes**: Extend `FormatSerializer` interface
3. **JsonSerializer**: Implement streaming write method
4. **CsvSerializer**: Implement streaming write method
5. **FileDestination**: Refactor to use streaming API
6. **Tests**: Update unit tests for streaming behavior
7. **Benchmarks**: Add JMH benchmarks to measure gains

---

## Acceptance Criteria

- [ ] Streaming API implemented for JSON and CSV serializers
- [ ] FileDestination refactored to use streaming
- [ ] No regression in existing functionality
- [ ] Unit tests passing (100% coverage for changes)
- [ ] JMH benchmarks show +10-20% improvement
- [ ] Memory profiling shows reduced allocations

---

## Risks & Challenges

**High Complexity:**
- Requires significant refactoring of `FormatSerializer` interface
- Impacts both JSON and CSV serializers
- FileDestination I/O model changes from character-based to byte-based
- Batching logic must be rewritten for byte arrays

**Low ROI:**
- Current optimizations already achieve 500+ MB/s target
- Streaming provides only 10-20% additional gain
- High development and testing effort (4-6 hours)
- Maintenance complexity increases

**Backward Compatibility:**
- Existing `serialize(Map<String, Object>)` method must remain
- Two code paths to maintain (String-based and Stream-based)

---

## Decision Rationale

**Deferral Justified Because:**
1. ✅ Target performance (500 MB/s) already met with Phase 1 & 2
2. ✅ Marginal gains (10-20%) don't justify high effort
3. ✅ Alternative optimization paths exist (e.g., Protobuf format for binary efficiency)
4. ✅ Current approach (batch writes) provides best ROI

**When to Revisit:**
- Performance requirements increase beyond 800 MB/s
- Memory pressure identified in production workloads
- After implementing multi-threaded file writing
- When adding binary format support (Protobuf, Avro)

---

## Alternative Optimizations (Better ROI)

Instead of Jackson streaming, consider:

1. **Protobuf Format**: Binary serialization (smaller size, faster parsing)
2. **Multi-threaded File Writing**: Parallel workers with separate FileDestination instances
3. **Memory-mapped Files**: For very large sequential writes

---

## References

- Jackson Streaming API: https://github.com/FasterXML/jackson-core
- Performance Analysis: `benchmarks/PERFORMANCE-ANALYSIS.md`
- JMH Benchmark Results: `BENCHMARK-RESULTS.md`
- Phase 1 & 2 Implementation: FileDestination.java (March 6, 2026)

---

## Notes

- This task is marked as **LOW PRIORITY** and **DEFERRED**
- Focus on higher-impact tasks (documentation, integration tests, user features)
- Revisit only if performance requirements change or Phase 2 optimizations prove insufficient
