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

package com.datagenerator.generators.primitive;

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates random timestamps (timestamp[start..end]) within specified range.
 *
 * <p><b>Formats Supported:</b>
 *
 * <ul>
 *   <li>ISO-8601: "2024-01-15T10:30:00Z"
 *   <li>Relative: "now", "now-30d", "now+7d" (d=days, h=hours, m=minutes, s=seconds)
 * </ul>
 *
 * <p><b>Algorithm:</b> Convert timestamps to epoch seconds, generate random second in range,
 * convert back to Instant:
 *
 * <pre>
 * secondsBetween = endTimestamp - startTimestamp
 * randomSeconds = random * secondsBetween
 * resultTimestamp = startTimestamp + randomSeconds
 * </pre>
 *
 * <p><b>Range:</b> Inclusive on both ends [start, end].
 *
 * <p><b>Output:</b> Returns Instant (UTC).
 */
public class TimestampGenerator implements DataGenerator {
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
  private static final Pattern RELATIVE_PATTERN =
      Pattern.compile("now([+-])(\\d+)([dhms])"); // now±N{d|h|m|s}

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof PrimitiveType primitiveType)) {
      throw new GeneratorException(
          "TimestampGenerator requires PrimitiveType, got: " + dataType.getClass().getSimpleName());
    }
    if (primitiveType.getKind() != PrimitiveType.Kind.TIMESTAMP) {
      throw new GeneratorException(
          "TimestampGenerator requires TIMESTAMP type, got: " + primitiveType.getKind());
    }

    // Parse start/end timestamps
    Instant startTimestamp = parseTimestamp(primitiveType.getMinValue(), "minValue");
    Instant endTimestamp = parseTimestamp(primitiveType.getMaxValue(), "maxValue");

    if (startTimestamp.isAfter(endTimestamp)) {
      throw new GeneratorException(
          "Invalid timestamp range: minValue ("
              + startTimestamp
              + ") > maxValue ("
              + endTimestamp
              + ")");
    }

    // Calculate seconds between timestamps
    long secondsBetween = ChronoUnit.SECONDS.between(startTimestamp, endTimestamp);

    if (secondsBetween == 0) {
      return startTimestamp; // Same timestamp
    }

    // Generate random seconds offset
    long randomSeconds = (long) (random.nextDouble() * (secondsBetween + 1));

    return startTimestamp.plusSeconds(randomSeconds);
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof PrimitiveType primitiveType
        && primitiveType.getKind() == PrimitiveType.Kind.TIMESTAMP;
  }

  private Instant parseTimestamp(String value, String fieldName) {
    if (value == null) {
      throw new GeneratorException(
          "Missing required field: %s for timestamp type".formatted(fieldName));
    }

    // Try relative format first: "now", "now-30d", "now+7d"
    if (value.equals("now")) {
      return Instant.now();
    }

    Matcher matcher = RELATIVE_PATTERN.matcher(value);
    if (matcher.matches()) {
      String sign = matcher.group(1);
      int amount = Integer.parseInt(matcher.group(2));
      String unit = matcher.group(3);

      Instant now = Instant.now();
      ChronoUnit chronoUnit =
          switch (unit) {
            case "d" -> ChronoUnit.DAYS;
            case "h" -> ChronoUnit.HOURS;
            case "m" -> ChronoUnit.MINUTES;
            case "s" -> ChronoUnit.SECONDS;
            default -> throw new GeneratorException("Invalid time unit: " + unit);
          };

      return sign.equals("+") ? now.plus(amount, chronoUnit) : now.minus(amount, chronoUnit);
    }

    // Try ISO-8601 format
    try {
      LocalDateTime dateTime = LocalDateTime.parse(value, FORMATTER);
      return dateTime.toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException e) {
      throw new GeneratorException(
          "Invalid "
              + fieldName
              + " for timestamp type: "
              + value
              + " (expected ISO-8601 or relative format like 'now-30d')",
          e);
    }
  }
}
