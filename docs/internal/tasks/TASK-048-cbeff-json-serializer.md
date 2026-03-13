# TASK-048: CBEFF JSON Wrapper Serializer

**Status:** Deferred ⏸️ (Future Enhancement)
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Effort:** 3–4h
**Complexity:** Low
**User Stories:** US-046
**Dependencies:** TASK-047

---

## Goal

Add a `CbeffSerializer` to the `formats/` module that wraps any generated record in a
CBEFF-like JSON envelope. This enables testing of biometric exchange pipelines that expect
a `format_owner` / `format_type` metadata wrapper around the payload.

---

## Implementation

### New class: `CbeffSerializer`

**Package:** `com.datagenerator.formats.cbeff`
**File:** `formats/src/main/java/com/datagenerator/formats/cbeff/CbeffSerializer.java`

```java
@RequiredArgsConstructor
public class CbeffSerializer implements FormatSerializer {
    private final String formatOwner;   // default: "ISO/IEC-JTC1-SC37"
    private final String formatType;    // e.g., "19794-2-json"

    @Override
    public String serialize(Map<String, Object> record) {
        // Build envelope map, put record under "payload", serialize to JSON string
    }

    @Override
    public String getFormatName() { return "cbeff"; }
}
```

Envelope structure:
```json
{
  "cbeff_version": "1.1",
  "format_owner": "<configurable>",
  "format_type": "<configurable>",
  "creation_date": "<ISO8601 now>",
  "subject_id": "<from record if present>",
  "payload": { ... original record ... }
}
```

### CLI integration

In `ExecuteCommand.java`, add `"cbeff"` to the format switch:
```java
case "cbeff" -> {
    String owner = jobConfig.getConf().getOrDefault("cbeff_format_owner", "ISO/IEC-JTC1-SC37");
    String type  = jobConfig.getConf().getOrDefault("cbeff_format_type",  "biometric-json");
    yield new CbeffSerializer(owner, type);
}
```

### Job config extension

Jobs can optionally specify in `conf`:
```yaml
conf:
  cbeff_format_owner: "ISO/IEC-JTC1-SC37"
  cbeff_format_type: "19794-2-json"
```

---

## Acceptance Criteria

- [ ] `--format cbeff` produces valid NDJSON with envelope fields
- [ ] `payload` contains the original record unchanged
- [ ] `format_owner` and `format_type` configurable via job `conf`
- [ ] `creation_date` is a valid ISO 8601 timestamp
- [ ] Existing serializers (json, csv, protobuf) unaffected
- [ ] Unit tests: `CbeffSerializerTest` with at least 6 test cases:
  - Required envelope fields present
  - `payload` matches input record
  - Custom `format_owner` and `format_type` applied
  - `subject_id` promoted to envelope when present in record
  - Empty record produces valid envelope
  - Output is valid JSON (Jackson parse round-trip)
- [ ] Spotless compliant, Apache 2.0 license header

---

## Test Plan

`formats/src/test/java/com/datagenerator/formats/cbeff/CbeffSerializerTest.java`

---

## Notes

- `creation_date` should use `Instant.now()` formatted with `DateTimeFormatter.ISO_INSTANT`.
- Do not add `subject_id` promotion logic if it adds complexity — envelope field can be null/absent.
- Keep the serializer stateless (Instant.now() called per record is fine for test data).
