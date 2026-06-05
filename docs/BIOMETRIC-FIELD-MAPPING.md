# Biometric ISO Field Mapping

Developer reference mapping every generated field to its ISO/IEC 19794 equivalent,
with binary encoding notes and known limitations.

---

## Overview

SeedStream generates synthetic biometric metadata records for testing biometric exchange
pipelines. Records are JSON-only; image pixel data uses filename placeholders.

**ISO standards covered:**

| Standard | Biometric modality | Coverage |
|---|---|---|
| ISO/IEC 19794-2 | Fingerprint minutiae | Full logical field set (JSON) |
| ISO/IEC 19794-4 | Fingerprint image metadata | Full logical field set (JSON) |
| ISO/IEC 19794-5 | Face image container | Full logical field set (JSON) |
| CBEFF (ISO/IEC 19785) | Biometric envelope | JSON-only envelope wrapper |

**Scope:** JSON field mapping. Binary emitter (TASK-052) is deferred and will document
byte-level layout separately. Pixel-level image generation is explicitly out of scope.

---

## Fingerprint Minutiae Record (ISO/IEC 19794-2)

YAML structure: `config/structures/fingerprint_minutiae.yaml`

### Top-level header fields

| Generator Field | JSON Type | ISO/IEC 19794-2 Field | Binary Encoding | Notes |
|---|---|---|---|---|
| `record_format` | `"FMR"` | Format identifier | 4-byte ASCII `"FMR\0"` | Fixed value |
| `version` | `"19794-2:2011"` or `"19794-2:2005"` | Version string | 4-byte ASCII | e.g. `" 020"` |
| `subject_id` | string (8–20 chars) | Subject identifier | variable-length field | Not in ISO binary header; application-level metadata |
| `sample_id` | int (0–9) | Sample index within subject | — | Identifies which sample for this subject |
| `finger_position` | enum string | Finger position code | 1 byte (0x00–0x0A) | See finger position codes below |
| `impression_type` | enum string | Impression type code | 1 byte (0x00–0x04) | See impression type codes below |
| `image_width` | int (300–1000) | Image width in pixels | 2-byte unsigned big-endian | Minutia x coordinates must be < image_width |
| `image_height` | int (300–1000) | Image height in pixels | 2-byte unsigned big-endian | Minutia y coordinates must be < image_height |
| `resolution_dpi` | `500` or `1000` | Finger image sampling rate | 2-byte unsigned (ppi) | ISO specifies ppi; 500 is standard for AFIS |
| `quality` | int (0–100) | Overall record quality score | 1 byte | 0 = not computed, 1–100 = quality |
| `minutiae` | array of minutia objects | Minutiae list | 6 bytes per minutia entry | 30–80 minutiae generated |

### Finger position codes

| JSON value | ISO binary code |
|---|---|
| `right_thumb` | `0x01` |
| `right_index` | `0x02` |
| `right_middle` | `0x03` |
| `right_ring` | `0x04` |
| `right_little` | `0x05` |
| `left_thumb` | `0x06` |
| `left_index` | `0x07` |
| `left_middle` | `0x08` |
| `left_ring` | `0x09` |
| `left_little` | `0x0A` |

### Impression type codes

| JSON value | ISO binary code |
|---|---|
| `live_scan_plain` | `0x00` |
| `live_scan_rolled` | `0x01` |
| `nonlive_scan_plain` | `0x02` |
| `nonlive_scan_rolled` | `0x03` |
| `latent` | `0x04` |

### Per-minutia entry fields

YAML structure: `config/structures/fingerprint_minutia.yaml`

| Generator Field | JSON Type | ISO/IEC 19794-2 Field | Binary Encoding | Notes |
|---|---|---|---|---|
| `x` | int (0–299) | Minutia x coordinate | 2 bytes, upper 2 bits = type | Pixel coordinate; validator checks x < image_width |
| `y` | int (0–299) | Minutia y coordinate | 2 bytes | Pixel coordinate; validator checks y < image_height |
| `angle_deg` | decimal (0.0–359.9) | Minutia angle | 1 byte (0–255) | Conversion: `byte = angle_deg * 256 / 360` |
| `type` | `"ending"` or `"bifurcation"` | Minutia type | 2 bits packed in x MSBs | `ending = 0x01`, `bifurcation = 0x02` |
| `quality` | int (0–100) | Per-minutia quality | 1 byte | Optional; 0 = not computed |

**Binary record layout (per entry, 6 bytes):**

```
[x_high][x_low][y_high][y_low][angle_byte][type_quality_byte]
```
All multi-byte fields: big-endian.

---

## Fingerprint Image Metadata (ISO/IEC 19794-4)

YAML structure: `config/structures/fingerprint_image.yaml`

| Generator Field | JSON Type | ISO/IEC 19794-4 Field | Binary Encoding | Notes |
|---|---|---|---|---|
| `record_format` | `"FIR"` | Format identifier | 4-byte ASCII `"FIR\0"` | Fixed value |
| `version` | `"19794-4:2011"` or `"19794-4:2005"` | Version string | 4-byte ASCII | — |
| `subject_id` | string (8–20 chars) | Subject identifier | — | Application-level metadata |
| `sample_id` | int (0–9) | Sample index | — | — |
| `image_type` | enum string | Image acquisition level | 1 byte | `plain=0x01`, `rolled=0x02`, `partial=0x03` |
| `width` | int (300–1000) | Horizontal line length | 2-byte unsigned big-endian | Image width in pixels |
| `height` | int (300–1000) | Vertical line count | 2-byte unsigned big-endian | Image height in pixels |
| `resolution_dpi` | `500` or `1000` | Scanning resolution | 2-byte unsigned (ppi) | — |
| `color_space` | enum string | Image colour space | 1 byte | `GRAY=0x00`, `YUV=0x01`, `RGB=0x02`; GRAY typical for fingerprints |
| `compression` | enum string | Compression algorithm | 1 byte | `none=0x00`, `WSQ=0x01`, `JPEG=0x02`, `JPEG2000=0x03` |
| `image_file` | string (10–40 chars) | — | — | Filename placeholder (e.g., `subject-001_fp_000.wsq`); no pixel data generated |

---

## Face Image Container (ISO/IEC 19794-5)

YAML structure: `config/structures/face_image.yaml`

### Top-level header fields

| Generator Field | JSON Type | ISO/IEC 19794-5 Field | Binary Encoding | Notes |
|---|---|---|---|---|
| `record_format` | `"FAC"` | Format identifier | 4-byte ASCII `"FAC\0"` | Fixed value |
| `version` | `"19794-5:2011"` or `"19794-5:2005"` | Version string | 4-byte ASCII | — |
| `subject_id` | string (8–20 chars) | Subject identifier | — | Application-level metadata |
| `sample_id` | int (0–9) | Sample index | — | — |
| `image_type` | enum string | Image type | 2 bytes | `still_controlled=0x0001`, `still_uncontrolled=0x0002`, `video=0x0003` |
| `width` | int (640–2048) | Width | 2-byte unsigned big-endian | Pixels; ICAO DG2 minimum 480px |
| `height` | int (480–1536) | Height | 2-byte unsigned big-endian | Pixels |
| `color_space` | enum string | Colour space | 1 byte | `RGB=0x00`, `YUV=0x01` |
| `compression` | enum string | Compression algorithm | 1 byte | `JPEG=0x00`, `JPEG2000=0x01`; JPEG2000 required for ICAO DG2 ePassport |
| `capture_device` | string (5–30 chars) | Capture device technology | — | Free-form device identifier |
| `capture_time` | ISO-8601 timestamp | Capture datetime | — | Within last 365 days |
| `image_file` | string (10–40 chars) | — | — | Filename placeholder; no pixel data generated |
| `face_box` | object | Bounding box | — | See face_bounding_box below |
| `landmarks` | object | Feature points | — | See face_landmarks below |
| `pose` | object | Head pose angles | — | See face_pose below |
| `quality` | object | Quality metrics | — | See face_quality below |

### face_bounding_box

YAML structure: `config/structures/face_bounding_box.yaml`

| Generator Field | JSON Type | ISO/IEC 19794-5 Concept | Notes |
|---|---|---|---|
| `x` | int (50–150) | Left edge of face bounding box (pixels) | Origin = top-left |
| `y` | int (40–120) | Top edge of face bounding box (pixels) | — |
| `width` | int (200–400) | Width of face bounding box (pixels) | — |
| `height` | int (250–350) | Height of face bounding box (pixels) | — |

### face_landmarks

YAML structure: `config/structures/face_landmarks.yaml`  
Each landmark is `object[image_point]` with fields `x: int[0..2047]`, `y: int[0..1535]`.

| Generator Field | ISO/IEC 19794-5 Feature Point | Notes |
|---|---|---|
| `left_eye` | Left eye centre | Pixel coordinates; validator checks within face_box |
| `right_eye` | Right eye centre | Pixel coordinates; validator checks within face_box |
| `nose_tip` | Nose tip | Pixel coordinates; validator checks within face_box |
| `mouth_left` | Left mouth corner | Pixel coordinates; validator checks within face_box |
| `mouth_right` | Right mouth corner | Pixel coordinates; validator checks within face_box |

Geometric coherence (eyes above nose, nose above mouth) is enforced by `BiometricValidator`,
not the generator. The generator produces independently randomised values within declared ranges.

### face_pose

YAML structure: `config/structures/face_pose.yaml`

| Generator Field | JSON Type | ISO/IEC 19794-5 Concept | Notes |
|---|---|---|---|
| `yaw` | decimal (-30.0–30.0) | Head yaw (left/right rotation) | Degrees; ISO calls this pan angle |
| `pitch` | decimal (-20.0–20.0) | Head pitch (up/down tilt) | Degrees; ISO calls this tilt angle |
| `roll` | decimal (-15.0–15.0) | Head roll (in-plane rotation) | Degrees; ISO calls this roll angle |

### face_quality

YAML structure: `config/structures/face_quality.yaml`

| Generator Field | JSON Type | ISO/IEC 19794-5 Concept | Notes |
|---|---|---|---|
| `overall` | int (0–100) | Overall face image quality score | 0 = failed/not computed, 100 = perfect |
| `occlusion` | int (0–100) | Occlusion score (glasses, hair, mask) | Higher = less occlusion |
| `illumination` | int (0–100) | Illumination uniformity score | — |

---

## CBEFF Envelope (ISO/IEC 19785)

The `cbeff` format wraps any biometric payload in a Common Biometric Exchange Formats
Framework-inspired JSON envelope. Implemented in `CbeffSerializer`.

| Envelope Field | JSON Type | CBEFF 2.x Concept | Notes |
|---|---|---|---|
| `cbeff_version` | `"1.1"` | CBEFF version | Fixed; references CBEFF 2.x structure concept |
| `format_owner` | string | BDB Format Owner | Default `"ISO/IEC-JTC1-SC37"`; configurable |
| `format_type` | string | BDB Format Type | Default `"biometric-json"`; configurable (e.g. `"19794-2-json"`) |
| `creation_date` | ISO-8601 UTC | SBH Creation Date | Generated at serialization time via `Instant.now()` |
| `subject_id` | string | — | Promoted from payload's `subject_id` field if present |
| `payload` | object | Biometric Data Block (BDB) | The full original generated record |

**Example envelope wrapping a fingerprint minutiae record:**

```json
{
  "cbeff_version": "1.1",
  "format_owner": "ISO/IEC-JTC1-SC37",
  "format_type": "19794-2-json",
  "creation_date": "2026-06-05T09:00:00Z",
  "subject_id": "subject-001",
  "payload": {
    "record_format": "FMR",
    "version": "19794-2:2011",
    "subject_id": "subject-001",
    "finger_position": "left_index",
    "minutiae": [...]
  }
}
```

---

## Known Limitations

| Limitation | Detail |
|---|---|
| **No pixel data** | `image_file` fields contain filename placeholders (e.g. `subject-001_fp_000.wsq`). Actual JPEG, JPEG2000, or WSQ image bytes are not generated. |
| **No geometric coherence** | Landmark coordinates (eyes, nose, mouth) are generated independently within their declared ranges. Geometric relationships (e.g. eyes above nose) are validated post-hoc by `BiometricValidator`, not enforced at generation time. |
| **No cross-record correlation** | Records are independent. Subject grouping via `subject_id` is a string field — records with the same `subject_id` are not otherwise correlated. |
| **Approximate binary encoding** | Binary layout notes in this document are derived from public OSS implementations and standard previews. Full byte-level normative conformance requires the purchased ISO/IEC PDF documents. |
| **No binary emitter (yet)** | The binary FMR/FAC serializer (TASK-052) is deferred. Current output is JSON only. |
| **Minutia coordinate range** | Minutia `x`, `y` values are generated in range 0–299 regardless of `image_width`/`image_height`. `BiometricValidator` catches out-of-bounds coordinates post-generation. |
| **`minutiae_count` field** | Not stored as a generated field. Derived from `minutiae` array length at serialization time. |

---

## References

### ISO/IEC Standards

- **ISO/IEC 19794-2** — Finger Minutiae Data  
  Webstore: https://webstore.iec.ch/publication/10792  
  Relaton metadata: https://github.com/relaton/relaton-data-iec/blob/6b98fb61c33c958640dea963f264cc86834eee97/data/iso_iec_19794-2_2011.yaml

- **ISO/IEC 19794-4** — Finger Image Data  
  Part of the ISO/IEC 19794 series; obtain via iso.org or iec.ch.

- **ISO/IEC 19794-5** — Face Image Data  
  Referenced by ICAO DG2 implementations. Preview and purchase: iso.org  
  OSS reference: https://github.com/iland112/local-pkd/commit/3dcd85c00268940e4dd899c02d14e1fc7cc7c8e9

- **ISO/IEC 19785** — Common Biometric Exchange Formats Framework (CBEFF)  
  Defines BDB format owner/type registration scheme.

### ePassport Standard

- **ICAO Doc 9303** — Machine-Readable Travel Documents  
  DG2 face image uses ISO/IEC 19794-5 as container. Official copy: https://www.icao.int/publications/pages/publication.aspx?docnum=9303

### Open-Source Implementations

- **OpenAFIS** — ISO/IEC 19794-2 header handling:  
  https://github.com/neilharan/openafis/blob/3ae1c757c6dafea977a33ef51380e37f1715e626/README.md#L65-L140

- **FingerJetFXOSE** — ISO/IEC output format reference:  
  https://github.com/FingerJetFXOSE/FingerJetFXOSE/blob/1726ba08bf7f2137d2f861ac1ae124d5cd355eee/FingerJetFXOSE/libFJFX/include/FJFX.h#L25-L101

- **NIST NBIS** — National Biometric Image Software; practical reference for minutiae encoding.

---

*Keep mapping tables in sync with YAML structures under `config/structures/`. Internal design
discussion and scope rationale: `docs/internal/BIOMETRIC-DISCUSSION.md`.*
