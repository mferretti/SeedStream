# Spec: Record Layouts (Fingerprint minutiae & Face images) and Reference Documents

## Summary

This document specifies the record field layouts the generator must implement for fingerprints
(ISO/IEC 19794-2 style minutiae templates and ISO/IEC 19794-4 image fields) and faces
(ISO/IEC 19794-5 face image container). It lists authoritative reference documents and
implementation guidance, and defines the delivery scope for this project.

---

## Scope Boundaries

This is a **data generator**, not a biometric SDK. The following are explicitly **in scope**:

- YAML structure definitions that map to ISO/IEC 19794 logical fields
- Generators that produce synthetic, realistic metadata values for all fields
- A CBEFF-like JSON envelope format (new serializer in `formats/`)
- A structural validator for generated records (new component in `core/` or a dedicated module)
- Example datasets (100 fingerprint + 100 face records) reproducible via seeded CLI
- ISO field mapping documentation

The following are explicitly **out of scope** (require a separate specialised tool):

- Pixel-level image generation (JPEG / JPEG2000 / WSQ / raw grayscale) — image fields use
  filename placeholders or base64 stubs only
- Biometric matching algorithms (FAR, FRR, EER) — the generator produces test fixtures, not
  a matching engine
- Intra-subject correlation across records — records are independent; subject grouping is
  achieved via a `subject_id` enum field, not cross-record state
- Exact binary ISO conformance — the binary serializer (optional Phase 2) documents the layout
  but full byte-level normative conformance requires the purchased ISO PDFs

---

## A. What We Are Implementing (High-Level Mapping)

### 1. Fingerprint Minutiae Record (ISO/IEC 19794-2 style — JSON mapping)

**Top-level header fields:**

| Field | Type (YAML) | ISO equivalent |
|---|---|---|
| `record_format` | `enum[FMR]` | Format identifier |
| `version` | `enum[19794-2:2011,19794-2:2005]` | Version string |
| `subject_id` | `char[8..20]` | Subject identifier |
| `sample_id` | `int[0..9]` | Sample index within subject |
| `finger_position` | `enum[left_thumb,left_index,left_middle,left_ring,left_little,right_thumb,right_index,right_middle,right_ring,right_little]` | ISO finger position code |
| `impression_type` | `enum[live_scan_plain,live_scan_rolled,nonlive_scan_plain,nonlive_scan_rolled,latent]` | ISO impression type code |
| `image_width` | `int[300..1000]` | 2-byte unsigned (binary) |
| `image_height` | `int[300..1000]` | 2-byte unsigned (binary) |
| `resolution_dpi` | `enum[500,1000]` | Resolution (ppi) |
| `quality` | `int[0..100]` | Overall record quality |
| `minutiae` | `array[object[fingerprint_minutia], 30..80]` | Minutiae entries array |

**Per-minutia entry structure (`fingerprint_minutia.yaml`):**

| Field | Type (YAML) | Notes |
|---|---|---|
| `x` | `int[0..999]` | Pixel x; validator checks x < image_width |
| `y` | `int[0..999]` | Pixel y; validator checks y < image_height |
| `angle_deg` | `decimal[0.0..359.9]` | JSON degrees; ISO binary = byte 0..255 → 0..360° |
| `type` | `enum[ending,bifurcation]` | ISO codes 0x01 / 0x02 |
| `quality` | `int[0..100]` | Per-minutia quality (optional) |

**Optional extended fields:**

- `ridge_counts`: list of ridge-count pairs between minutiae pairs (Phase 2)
- `core`: `{x, y}` coordinates (Phase 2)
- `delta`: `{x, y}` coordinates (Phase 2)

**Binary mapping notes:**

- Record header: 4-byte ASCII `"FMR\0"`, 4-byte version string, 2-byte total length,
  2-byte minutiaeCount
- Each minutia entry: 6 bytes (2B x, 2B y, 1B angle 0..255, 1B type+quality packed)
- Endianness: big-endian for all multi-byte fields

---

### 2. Fingerprint Image Metadata (ISO/IEC 19794-4 style — JSON mapping)

| Field | Type (YAML) | Notes |
|---|---|---|
| `image_type` | `enum[plain,rolled,partial]` | — |
| `width` | `int[300..1000]` | — |
| `height` | `int[300..1000]` | — |
| `resolution_dpi` | `enum[500,1000]` | — |
| `color_space` | `enum[GRAY,YUV,RGB]` | GRAY typical for fingerprints |
| `compression` | `enum[none,JPEG,WSQ,JPEG2000]` | WSQ and JPEG2000 for passport use |
| `image_file` | `char[10..40]` | Filename placeholder (e.g., `subject-001_fp_000.wsq`) |

---

### 3. Face Image Record (ISO/IEC 19794-5 style — JSON mapping)

**Top-level header:**

| Field | Type (YAML) | Notes |
|---|---|---|
| `record_format` | `enum[FAC]` | Format identifier |
| `version` | `enum[19794-5:2011,19794-5:2005]` | — |
| `subject_id` | `char[8..20]` | — |
| `sample_id` | `int[0..9]` | — |
| `image_type` | `enum[still_controlled,still_uncontrolled,video]` | — |
| `width` | `int[640..2048]` | — |
| `height` | `int[480..1536]` | — |
| `color_space` | `enum[RGB,YUV]` | — |
| `compression` | `enum[JPEG,JPEG2000]` | JPEG2000 for ePassport (ICAO DG2) |
| `capture_device` | `char[5..30]` | — |
| `capture_time` | `timestamp[now-365d..now]` | — |
| `image_file` | `char[10..40]` | Filename placeholder |

**Face-specific fields (nested objects):**

| Field | Structure | Notes |
|---|---|---|
| `face_box` | `object[face_bounding_box]` | x, y, width, height |
| `landmarks` | `object[face_landmarks]` | left_eye, right_eye, nose_tip, mouth_left, mouth_right |
| `pose` | `object[face_pose]` | yaw, pitch, roll in degrees |
| `quality` | `object[face_quality]` | overall, occlusion, illumination (0..100) |

**face_bounding_box structure:**

| Field | Type | Notes |
|---|---|---|
| `x` | `int[100..600]` | — |
| `y` | `int[80..400]` | — |
| `width` | `int[200..800]` | — |
| `height` | `int[250..1000]` | — |

**face_landmarks structure (each landmark is `object[image_point]`):**

| Field | Type | Notes |
|---|---|---|
| `x` | `int[0..2047]` | Validator checks within face_box |
| `y` | `int[0..1535]` | Validator checks within face_box |

**face_pose structure:**

| Field | Type |
|---|---|
| `yaw` | `decimal[-30.0..30.0]` |
| `pitch` | `decimal[-20.0..20.0]` |
| `roll` | `decimal[-15.0..15.0]` |

**face_quality structure:**

| Field | Type |
|---|---|
| `overall` | `int[0..100]` |
| `occlusion` | `int[0..100]` |
| `illumination` | `int[0..100]` |

**Notes:**
- ISO/IEC 19794-5 describes a container holding one or more face image blocks. In ePassports
  (ICAO DG2) a face image block typically wraps a single JPEG/JPEG2000 image plus annotations.
- Landmark coordinate geometric coherence (eyes above nose, nose above mouth, all within
  face_box) is enforced by the validator, not the generator; the generator produces
  independently randomised values within the declared ranges.

---

## B. Field-Level Guidance and Binary Mapping

Where ISO uses numeric codes or compact binary fields, the implementation provides a
one-to-one mapping table:

| ISO concept | Binary | JSON token |
|---|---|---|
| Minutia type: ending | `0x01` | `"ending"` |
| Minutia type: bifurcation | `0x02` | `"bifurcation"` |
| Angle: 0..255 | 1 byte | `angle_deg = angle_byte * 360.0 / 256` |
| Finger position: left index | `0x02` | `"left_index"` |
| Impression type: live scan plain | `0x00` | `"live_scan_plain"` |

For binary emulation (Phase 2): the minimal binary layout mirrors ISO field ordering and is
documented in the binary serializer. Full byte-level normative conformance requires the official
ISO PDFs.

---

## C. Reference Documents

### 1. ISO/IEC 19794-2 — Finger Minutiae Data

- **Verified evidence:**
  - Relaton metadata entry: https://github.com/relaton/relaton-data-iec/blob/6b98fb61c33c958640dea963f264cc86834eee97/data/iso_iec_19794-2_2011.yaml
    (links to IEC webstore preview https://webstore.iec.ch/publication/10792)
  - OpenAFIS README references ISO/IEC 19794-2:2005 and shows header handling:
    https://github.com/neilharan/openafis/blob/3ae1c757c6dafea977a33ef51380e37f1715e626/README.md#L65-L140
  - FingerJetFXOSE header referencing ISO/IEC output formats:
    https://github.com/FingerJetFXOSE/FingerJetFXOSE/blob/1726ba08bf7f2137d2f861ac1ae124d5cd355eee/FingerJetFXOSE/libFJFX/include/FJFX.h#L25-L101
- **Conclusion:** Standard exists; public metadata, previews, and multiple OSS projects confirm
  structure. Full normative text via ISO/IEC webstore.

### 2. ISO/IEC 19794-5 — Face Image Data

- **Verified evidence:**
  - DG2/DG1 parsing project referencing ISO/IEC 19794-5:
    https://github.com/iland112/local-pkd/commit/3dcd85c00268940e4dd899c02d14e1fc7cc7c8e9
  - Public parsers reference "ISO/IEC 19794-5 (Face image container)" for DG2 implementation.
  - ISO/IEC 19794-5 is part of the published 19794 series; purchase/preview at iso.org / iec.ch.
- **Conclusion:** Published standard; broadly referenced for face image handling (DG2).

### 3. ICAO Doc 9303 — Machine-Readable Travel Documents (ePassports)

- **Verified evidence:**
  - ICAO publication page: https://www.icao.int/publications/pages/publication.aspx?docnum=9303
  - Governs ePassport chip DG structure; DG2 face image uses ISO/IEC 19794-5 as container.
- **Conclusion:** Normative ePassport standard; obtain official copy from ICAO.

### 4. Supporting Materials

- NIST/NBIS and FBI formats are commonly used in practice; OSS libs (OpenAFIS, NBIS)
  demonstrate practical encodings and conversions.

**Caveat:** Official ISO/IEC standards are sold/licensed. Preview pages and metadata are publicly
accessible. For full normative text and exact binary field encodings, purchase official ISO/IEC
documents.

---

## D. Implementation Notes & Fidelity Targets

- **JSON mapping:** high fidelity — include every ISO-referenced logical field, document binary
  equivalents in a mapping table.
- **Binary emulation (Phase 2):** minimal documented binary layout mirroring ISO field ordering.
  Not a substitute for full conformance; flag clearly in docs.
- **Landmark geometry:** validator checks that eye/nose/mouth coordinates fall within the
  declared face_box; generator produces values inside the declared ranges (not geometrically
  coherent by construction).
- **Image data:** filename placeholders only (e.g., `subject-001_fp_000.wsq`). Actual pixel
  data generation is out of scope.
- **`minutiae_count` field:** derived from the length of the `minutiae` array at serialisation
  time; not stored as an independent generated field.

---

## E. Example JSON Templates

### Fingerprint Minutiae

```json
{
  "record_format": "FMR",
  "version": "19794-2:2011",
  "subject_id": "subject-001",
  "sample_id": 0,
  "finger_position": "left_index",
  "impression_type": "live_scan_plain",
  "image_width": 800,
  "image_height": 800,
  "resolution_dpi": 500,
  "quality": 82,
  "minutiae_count": 56,
  "minutiae": [
    {"x": 123, "y": 456, "angle_deg": 72.5, "type": "ending",      "quality": 80},
    {"x": 234, "y": 367, "angle_deg": 12.3, "type": "bifurcation", "quality": 70}
  ]
}
```

### Face Image Container

```json
{
  "record_format": "FAC",
  "version": "19794-5:2011",
  "subject_id": "subject-001",
  "sample_id": 0,
  "image_type": "still_controlled",
  "width": 2048,
  "height": 1536,
  "color_space": "RGB",
  "compression": "JPEG2000",
  "capture_time": "2025-06-14T10:32:00Z",
  "face_box": {"x": 420, "y": 320, "width": 640, "height": 800},
  "landmarks": {
    "left_eye":    {"x": 520, "y": 480},
    "right_eye":   {"x": 760, "y": 480},
    "nose_tip":    {"x": 640, "y": 640},
    "mouth_left":  {"x": 550, "y": 760},
    "mouth_right": {"x": 730, "y": 760}
  },
  "pose":    {"yaw": -5.2, "pitch": 2.1, "roll": 0.8},
  "quality": {"overall": 91, "occlusion": 95, "illumination": 88},
  "image_file": "subject-001_face_000.jp2"
}
```

### CBEFF-like Envelope

```json
{
  "cbeff_version": "1.1",
  "format_owner": "ISO/IEC-JTC1-SC37",
  "format_type": "19794-2-json",
  "creation_date": "2025-06-14T10:32:00Z",
  "subject_id": "subject-001",
  "payload": { "...": "biometric record object above" }
}
```

---

## F. Recommended Approach for Exact Binary Conformance (Phase 2)

1. Obtain ISO/IEC 19794-2 and 19794-5 PDFs from the ISO or IEC webstore.
2. Implement a parser matching the byte-level field sizes and encodings in the normative text.
3. Run interoperability tests with NBIS utilities or other ISO-compatible libraries.
4. Compare against any available official test vectors.
