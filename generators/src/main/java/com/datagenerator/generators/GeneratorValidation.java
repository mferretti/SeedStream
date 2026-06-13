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

  /**
   * Hard ceiling on any single generated collection/string length. Type bounds can originate from
   * untrusted input (e.g. the {@code inspector} module emits structures from external OpenAPI/SQL
   * specs), and {@code core} type parsing imposes no upper bound — so an unbounded {@code
   * maxLength} would let a crafted spec request a multi-billion-element array or string and trigger
   * an {@link OutOfMemoryError} at allocation time (CWE-789 / DoS). Generators reject anything
   * above this ceiling before allocating. 10M comfortably exceeds any realistic test-data field
   * while staying well clear of catastrophic allocation.
   */
  public static final int MAX_GENERATED_SIZE = 10_000_000;

  private GeneratorValidation() {}

  /**
   * Reject a generated length that exceeds {@link #MAX_GENERATED_SIZE} before it is allocated.
   *
   * @param length the upper-bound length about to drive an allocation
   * @param typeName human-readable type name for the error message (e.g. "array length", "char")
   * @throws GeneratorException if {@code length} exceeds the ceiling
   */
  public static void requireBoundedSize(long length, String typeName) {
    if (length > MAX_GENERATED_SIZE) {
      throw new GeneratorException(
          "Generated "
              + typeName
              + " size "
              + length
              + " exceeds the maximum allowed ("
              + MAX_GENERATED_SIZE
              + "); refusing to allocate to avoid memory exhaustion");
    }
  }

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
