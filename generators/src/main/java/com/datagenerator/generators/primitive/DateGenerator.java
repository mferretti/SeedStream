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
import com.datagenerator.generators.GeneratorValidation;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * Generates random dates (date[start..end]) within specified range.
 *
 * <p><b>Format:</b> ISO-8601 dates (yyyy-MM-dd), e.g., "2020-01-01".
 *
 * <p><b>Algorithm:</b> Convert dates to epoch days, generate random day in range, convert back to
 * LocalDate:
 *
 * <pre>
 * daysBetween = endDate - startDate
 * randomDays = random.nextInt(daysBetween + 1)
 * resultDate = startDate + randomDays
 * </pre>
 *
 * <p><b>Range:</b> Inclusive on both ends [start, end].
 */
public class DateGenerator implements DataGenerator {
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  @Override
  public Object generate(Random random, DataType dataType) {
    PrimitiveType primitiveType =
        GeneratorValidation.requirePrimitiveKind(
            dataType, PrimitiveType.Kind.DATE, "DateGenerator");

    // Parse start/end dates
    LocalDate startDate = parseDate(primitiveType.getMinValue(), "minValue");
    LocalDate endDate = parseDate(primitiveType.getMaxValue(), "maxValue");

    GeneratorValidation.requireValidRange(startDate, endDate, "date");

    // Calculate days between dates
    long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);

    if (daysBetween == 0) {
      return startDate; // Same date
    }

    // Generate random days offset — clamp to include the end date
    long randomDays = random.nextLong(daysBetween + 1);

    return startDate.plusDays(randomDays);
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof PrimitiveType primitiveType
        && primitiveType.getKind() == PrimitiveType.Kind.DATE;
  }

  private LocalDate parseDate(String value, String fieldName) {
    if (value == null) {
      throw new GeneratorException("Missing required field: %s for date type".formatted(fieldName));
    }
    try {
      return LocalDate.parse(value, FORMATTER);
    } catch (DateTimeParseException e) {
      throw new GeneratorException(
          "Invalid " + fieldName + " for date type: " + value + " (expected yyyy-MM-dd format)", e);
    }
  }
}
