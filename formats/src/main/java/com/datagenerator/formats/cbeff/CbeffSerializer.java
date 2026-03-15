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
 * <p>Wraps any generated record in a Common Biometric Exchange Formats Framework (CBEFF) inspired
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
 *   "payload": { ...original record... }
 * }
 * }</pre>
 *
 * <p><b>Thread Safety:</b> Stateless — {@code Instant.now()} is called per record. ObjectMapper is
 * thread-safe after configuration.
 */
@Slf4j
public class CbeffSerializer implements FormatSerializer {

  public static final String CBEFF_VERSION = "1.1";
  public static final String DEFAULT_FORMAT_OWNER = "ISO/IEC-JTC1-SC37";
  public static final String DEFAULT_FORMAT_TYPE = "biometric-json";

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
  public CbeffSerializer(String formatOwner, String formatType) {
    this.formatOwner = formatOwner;
    this.formatType = formatType;
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
  public String serialize(Map<String, Object> record) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("cbeff_version", CBEFF_VERSION);
    envelope.put("format_owner", formatOwner);
    envelope.put("format_type", formatType);
    envelope.put("creation_date", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

    Object subjectId = record.get("subject_id");
    if (subjectId != null) {
      envelope.put("subject_id", subjectId);
    }

    envelope.put("payload", record);

    try {
      return mapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize record to CBEFF JSON: {}", record, e);
      throw new SerializationException("CBEFF serialization failed", e);
    }
  }

  @Override
  public String getFormatName() {
    return "cbeff";
  }
}
