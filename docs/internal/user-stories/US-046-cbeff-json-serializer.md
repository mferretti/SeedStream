# US-046: Wrap Biometric Records in a CBEFF-like JSON Envelope

**Status:** Deferred ⏸️ (Future Enhancement)
**Priority:** P3
**Phase:** Phase 10 (Biometric Data Generation)
**Task:** TASK-048-cbeff-json-serializer.md

---

## User Story

As a **developer building a biometric data exchange system**,
I want to **emit generated records wrapped in a CBEFF-like JSON envelope**,
so that I can **test ingestion pipelines that expect a format-owner/format-type metadata wrapper
around the biometric payload**.

---

## Acceptance Criteria

1. A new `--format cbeff` CLI option produces CBEFF-wrapped NDJSON output.
2. Each output record has the envelope fields: `cbeff_version`, `format_owner`, `format_type`, `creation_date`, `subject_id`, and `payload` (the raw generated record).
3. `format_owner` and `format_type` are configurable via job `conf` section.
4. `creation_date` is the generation timestamp (ISO 8601).
5. Existing formats (json, csv, protobuf) are unaffected.
6. Unit tests cover serialisation of all envelope fields and that `payload` equals the original record.

---

## Implementation Notes

- Add `CbeffSerializer` implementing `FormatSerializer` in `formats/src/main/java/com/datagenerator/formats/cbeff/`.
- Constructor accepts `formatOwner` (default `"ISO/IEC-JTC1-SC37"`) and `formatType` (e.g., `"19794-2-json"`), both configurable from job `conf`.
- `serialize(Map<String, Object> record)` wraps the record under a `"payload"` key and adds envelope fields.
- Register `"cbeff"` in `ExecuteCommand` serializer switch statement.
- `FormatName` constant: `"cbeff"`.

---

## Dependencies

- US-044 or US-045 (to have meaningful biometric records to wrap; can be developed in parallel)

---

## Definition of Done

- [ ] `CbeffSerializer` implemented and registered
- [ ] `--format cbeff` works end-to-end via CLI
- [ ] `format_owner` and `format_type` configurable from job `conf`
- [ ] Unit tests for envelope structure and passthrough of payload fields
- [ ] Spotless compliant, license header applied
