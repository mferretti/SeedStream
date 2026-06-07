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

package com.datagenerator.generators;

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;

public final class GeneratorValidation {

  private GeneratorValidation() {}

  public static PrimitiveType requirePrimitiveKind(
      DataType dataType, PrimitiveType.Kind expected, String generatorName) {
    if (!(dataType instanceof PrimitiveType pt)) {
      throw new GeneratorException(
          generatorName + " requires PrimitiveType, got: " + dataType.getClass().getSimpleName());
    }
    if (pt.getKind() != expected) {
      throw new GeneratorException(
          generatorName + " requires " + expected + " type, got: " + pt.getKind());
    }
    return pt;
  }

  public static <T extends Comparable<? super T>> void requireValidRange(
      T min, T max, String typeName) {
    if (min.compareTo(max) > 0) {
      throw new GeneratorException(
          "Invalid " + typeName + " range: min (" + min + ") > max (" + max + ")");
    }
  }
}
