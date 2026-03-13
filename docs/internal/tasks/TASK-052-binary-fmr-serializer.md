# TASK-052: Binary FMR-like Serializer (Phase 2 / Optional)

**Status:** Deferred ⏸️ (Future Enhancement)
**Priority:** P3 (Post-v1.0 / optional)
**Phase:** Phase 10 (Biometric Data Generation)
**Effort:** 8–12h
**Complexity:** High
**User Stories:** (future)
**Dependencies:** TASK-047, TASK-048

---

## Goal

Add a `--format fmr-binary` serializer that emits a minimal binary layout mirroring the
ISO/IEC 19794-2 field ordering for fingerprint minutiae records. This enables testing of
ingestion pipelines that parse FMR-like binary streams.

**This task requires obtaining the official ISO/IEC 19794-2 PDF before implementation.**

---

## Scope

This is approximate binary conformance, not full normative compliance. The output follows
the documented field ordering and byte widths but may not pass an ISO conformance test suite
without the full normative PDF.

---

## Binary Layout (documented layout — verify against ISO PDF before implementation)

```
Header block:
  Bytes 0..3   : "FMR\0" (4-byte ASCII magic)
  Bytes 4..7   : version string ("020 " for 19794-2:2005, "030 " for 2011)
  Bytes 8..9   : total record length (2-byte big-endian unsigned)
  Bytes 10..11 : 0x0000 (reserved)
  Bytes 12..13 : image width (2-byte big-endian unsigned)
  Bytes 14..15 : image height (2-byte big-endian unsigned)
  Byte  16     : x resolution (encoded)
  Byte  17     : y resolution (encoded)
  Byte  18     : finger count (always 1 for single-finger records)
  Byte  19     : 0x00 (reserved)

Finger view block (one per finger):
  Byte  0      : finger position code (0x00..0x0A)
  Byte  1      : view number | impression type (packed nibbles)
  Byte  2      : finger quality (0..100)
  Byte  3      : minutiae count

Minutia entries (6 bytes each, count from above):
  Bytes 0..1   : type (2 bits) | x (14 bits), big-endian
  Bytes 2..3   : quality flag (2 bits) | y (14 bits), big-endian
  Byte  4      : angle (0..255 → 0..360°)
  Byte  5      : minutia quality (0..100)

Extended data block:
  2-byte length = 0x0000 (no extended data)
```

---

## Implementation

### New class: `FmrBinarySerializer`

**Package:** `com.datagenerator.formats.fmr`

- Implements `FormatSerializer`.
- `serialize(Map<String, Object> record)` builds a `ByteArrayOutputStream`, populates all
  fields per the layout above, returns Base64-encoded string (for NDJSON compatibility).
- Add `--format fmr-binary` to CLI format switch.

### Companion parser: `FmrBinaryParser`

- Parses Base64 + binary bytes back to `Map<String, Object>`.
- Used by the validator for binary round-trip tests.

---

## Acceptance Criteria

- [ ] `--format fmr-binary` produces Base64-encoded binary NDJSON
- [ ] `FmrBinaryParser` round-trips all fields
- [ ] Unit tests: encode → decode → assert all fields equal original
- [ ] Binary layout documented in `docs/BIOMETRIC-FIELD-MAPPING.md` section 3
- [ ] Limitation clearly flagged: "approximate conformance — verify against ISO/IEC 19794-2 PDF"
- [ ] Spotless compliant, Apache 2.0 license header

---

## Prerequisites

- Obtain ISO/IEC 19794-2 PDF and verify the layout above against the normative text.
- Adjust byte offsets and field packing if normative text differs from the documented layout.

---

## Notes

- Face image binary (ISO/IEC 19794-5) is even more complex (TLV structure). Leave for a
  separate task.
- This task is P3 — do not start until US-044 through US-048 are complete and validated.
