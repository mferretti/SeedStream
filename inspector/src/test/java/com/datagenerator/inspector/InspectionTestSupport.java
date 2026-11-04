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

package com.datagenerator.inspector;

import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.TypeParser;
import com.datagenerator.schema.model.DataStructure;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared test helpers for asserting on {@link Inspection} results. */
public final class InspectionTestSupport {

  private InspectionTestSupport() {}

  /** Field name to exact datatype string, in declaration order, for one named structure. */
  public static Map<String, String> datatypesOf(Inspection inspection, String structureName) {
    DataStructure structure =
        inspection.structures().stream()
            .filter(s -> s.getName().equals(structureName))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no structure named " + structureName));
    Map<String, String> result = new LinkedHashMap<>();
    structure.getData().forEach((field, def) -> result.put(field, def.getDatatype()));
    return result;
  }

  /**
   * Asserts the datatype is accepted by the engine's {@link TypeParser} and that a char/int/decimal
   * range is not inverted (the generators reject {@code min > max}, e.g. {@code char[1..0]}).
   */
  public static DataType assertEngineLoadable(String datatype) {
    DataType parsed = new TypeParser().parse(datatype);
    if (parsed instanceof PrimitiveType p) {
      switch (p.getKind()) {
        case CHAR, INT ->
            assertThat(Long.parseLong(p.getMinValue()))
                .as("range must not be inverted: %s", datatype)
                .isLessThanOrEqualTo(Long.parseLong(p.getMaxValue()));
        case DECIMAL ->
            assertThat(new BigDecimal(p.getMinValue()))
                .as("range must not be inverted: %s", datatype)
                .isLessThanOrEqualTo(new BigDecimal(p.getMaxValue()));
        default -> {
          // dates / timestamps / boolean carry no numeric range to check
        }
      }
    }
    return parsed;
  }

  /** Decimal places the engine's DecimalGenerator would render: max scale of min and max. */
  public static int decimalScaleOf(String datatype) {
    PrimitiveType p = (PrimitiveType) new TypeParser().parse(datatype);
    assertThat(p.getKind()).as("not a decimal: %s", datatype).isEqualTo(PrimitiveType.Kind.DECIMAL);
    return Math.max(
        new BigDecimal(p.getMinValue()).scale(), new BigDecimal(p.getMaxValue()).scale());
  }
}
