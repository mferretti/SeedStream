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

package com.datagenerator.generators.composite;

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.MaxLengthType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.util.Random;

/**
 * Generates a {@code max_length} field: the inner value if it fits, else a fresh one, up to {@value
 * #ATTEMPTS} attempts in total; the last attempt is truncated at a code point boundary and stripped
 * of trailing whitespace (unless that would leave it empty).
 *
 * <p><b>Drift:</b> a value that fits at the first attempt is exactly the uncapped value and
 * consumes the same draws. Each regeneration consumes extra draws from the shared {@link Random},
 * so once a cap triggers, later fields of the same record and stream may differ from the uncapped
 * structure. Still deterministic and independent of the thread count; structures without {@code
 * max_length} are untouched.
 *
 * <p><b>Thread safety:</b> stateless.
 */
public class MaxLengthGenerator implements DataGenerator {
  static final int ATTEMPTS = 8;

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof MaxLengthType m)) {
      throw new GeneratorException(
          "MaxLengthGenerator requires MaxLengthType, got: " + dataType.getClass().getSimpleName());
    }
    DataType inner = m.getInner();
    DataGenerator generator = GeneratorContext.getFactory().create(inner);
    Object value = generator.generate(random, inner);
    for (int attempt = 1; value instanceof String s; attempt++) {
      if (fits(s, m.getMaxLength(), m.isBytes())) {
        return s;
      }
      if (attempt == ATTEMPTS) {
        return truncate(s, m.getMaxLength(), m.isBytes());
      }
      value = generator.generate(random, inner);
    }
    return value;
  }

  static boolean fits(String s, int max, boolean bytes) {
    if (!bytes) {
      // UTF-16 length bounds the code point count from above: no scan for the common short value
      return s.length() <= max || s.codePointCount(0, s.length()) <= max;
    }
    int total = 0;
    for (int i = 0; i < s.length(); i++) {
      total += utf8Length(s.charAt(i));
      if (total > max) {
        return false;
      }
      if (Character.isHighSurrogate(s.charAt(i))) {
        i++; // the pair is 4 bytes in total, counted by the high half
      }
    }
    return true;
  }

  /** UTF-8 bytes of a char; a high surrogate stands for its whole 4-byte pair. */
  private static int utf8Length(char c) {
    if (c < 0x80) {
      return 1;
    }
    if (c < 0x800) {
      return 2;
    }
    return Character.isHighSurrogate(c) ? 4 : 3;
  }

  static String truncate(String s, int max, boolean bytes) {
    int end = 0;
    int used = 0;
    while (end < s.length()) {
      int cp = s.codePointAt(end);
      used += bytes ? utf8Length(cp) : 1;
      if (used > max) {
        break;
      }
      end += Character.charCount(cp);
    }
    String cut = s.substring(0, end);
    String stripped = cut.stripTrailing();
    return stripped.isEmpty() ? cut : stripped;
  }

  private static int utf8Length(int cp) {
    return cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof MaxLengthType;
  }
}
