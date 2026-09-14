/*
 * Copyright 2026 Marco Ferretti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datagenerator.formats.cbeff;

import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.formats.SerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Serializes generated records to a CBEFF-like JSON envelope format.
 *
 * <p>Wraps any generated data in a Common Biometric Exchange Formats Framework (CBEFF) inspired
 * JSON envelope, enabling testing of biometric exchange pipelines that expect a {@code
 * format_owner} / {@code format_type} metadata wrapper around the payload.
 *
 * <p><b>Envelope structure:</b>
 *
 * <pre>{@code
 * {
 *   "cbeff_version": "1.1",
 *   "format_owner": "ISO/IEC-JTC1-SC37",
 *   "format_type": "biometric-json",
 *   "creation_date": "2026-03-15T10:00:00Z",
 *   "subject_id": "<promoted from payload if present>",
 *   // creation_date is likewise promoted from the payload when present, else synthesized
 *   "payload": { ...original data... }
 * }
 * }</pre>
 *
 * <p><b>Creation date (two modes):</b>
 *
 * <ul>
 *   <li><b>Promoted (preferred):</b> if the record declares a {@code creation_date} field (e.g. a
 *       seeded {@code timestamp[..]} type), its value is promoted into the envelope verbatim — a
 *       proper seed-driven, range-honoring timestamp. The field is also retained in the payload,
 *       exactly like {@code subject_id}.
 *   <li><b>Synthetic fallback:</b> if the record has no {@code creation_date}, one is derived by
 *       {@link #deriveCreationDate(String)} — a deterministic hash-fold of the payload. This value
 *       is <em>synthetic</em>: reproducible but arbitrary within a ~10-year window, tied to the
 *       payload rather than the job seed (two different jobs emitting an identical record share a
 *       date). Records needing a seed-meaningful date must declare the field. See issue #280.
 * </ul>
 *
 * <p><b>Determinism:</b> both modes are deterministic (never wall-clock), so the same seed produces
 * byte-identical output across runs.
 *
 * <p><b>Thread Safety:</b> Stateless. ObjectMapper is thread-safe after configuration.
 */
@Slf4j
public class CbeffSerializer implements FormatSerializer {

  public static final String CBEFF_VERSION = "1.1";
  public static final String DEFAULT_FORMAT_OWNER = "ISO/IEC-JTC1-SC37";
  public static final String DEFAULT_FORMAT_TYPE = "biometric-json";

  /** Anchor for the deterministic {@code creation_date} derivation. */
  private static final Instant CBEFF_EPOCH = Instant.parse("2020-01-01T00:00:00Z");

  /** Width of the derived-date window (~10 years, in seconds). */
  private static final long CBEFF_DATE_RANGE_SECONDS = 10L * 365 * 24 * 60 * 60;

  private final String formatOwner;
  private final String formatType;
  private final ObjectMapper mapper;

  /** Create CBEFF serializer with default format owner and type. */
  public CbeffSerializer() {
    this(DEFAULT_FORMAT_OWNER, DEFAULT_FORMAT_TYPE);
  }

  /**
   * Create CBEFF serializer with configurable format owner and type.
   *
   * @param formatOwner CBEFF format owner identifier (e.g. "ISO/IEC-JTC1-SC37")
   * @param formatType CBEFF format type identifier (e.g. "19794-2-json")
   */
  public CbeffSerializer(String owner, String fmtType) {
    this.formatOwner = owner;
    this.formatType = fmtType;
    this.mapper = createObjectMapper();
  }

  private static ObjectMapper createObjectMapper() {
    ObjectMapper m = new ObjectMapper();
    m.registerModule(new JavaTimeModule());
    m.disable(SerializationFeature.INDENT_OUTPUT);
    m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return m;
  }

  @Override
  public String serialize(Map<String, Object> data) {
    try {
      // Canonical payload JSON drives both the deterministic creation_date and the envelope body.
      String canonicalPayload = mapper.writeValueAsString(data);

      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("cbeff_version", CBEFF_VERSION);
      envelope.put("format_owner", formatOwner);
      envelope.put("format_type", formatType);

      // Promote a seeded creation_date from the payload when the record declares one (mirrors the
      // subject_id promotion below); otherwise fall back to the deterministic synthetic derivation.
      Object providedDate = data.get("creation_date");
      if (providedDate != null) {
        envelope.put("creation_date", providedDate);
      } else {
        envelope.put(
            "creation_date",
            DateTimeFormatter.ISO_INSTANT.format(deriveCreationDate(canonicalPayload)));
      }

      Object subjectId = data.get("subject_id");
      if (subjectId != null) {
        envelope.put("subject_id", subjectId);
      }

      envelope.put("payload", data);

      return mapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize data to CBEFF JSON: {}", data, e);
      throw new SerializationException("CBEFF serialization failed", e);
    }
  }

  /**
   * Derive a stable {@code creation_date} from the record payload so identical data yields an
   * identical timestamp — preserving the same-seed byte-identical guarantee. A 64-bit FNV-1a hash
   * of the canonical payload JSON is folded into a ~10-year window anchored at {@link
   * #CBEFF_EPOCH}. The value is synthetic (not the real generation time); see the "meaningful
   * timestamps" improvement issue for a seed-plumbed alternative.
   *
   * @param canonicalPayload deterministic JSON encoding of the record
   * @return a reproducible instant within [CBEFF_EPOCH, CBEFF_EPOCH + ~10y)
   */
  private static Instant deriveCreationDate(String canonicalPayload) {
    long hash = 0xcbf29ce484222325L; // FNV-1a 64-bit offset basis
    for (int i = 0; i < canonicalPayload.length(); i++) {
      hash ^= canonicalPayload.charAt(i);
      hash *= 0x100000001b3L; // FNV-1a 64-bit prime
    }
    long offsetSeconds = Math.floorMod(hash, CBEFF_DATE_RANGE_SECONDS);
    return CBEFF_EPOCH.plusSeconds(offsetSeconds);
  }

  @Override
  public String getFormatName() {
    return "cbeff";
  }
}
