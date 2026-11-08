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

package com.datagenerator.core.type;

import lombok.Value;

/**
 * A sequential integer column: {@code serial} (starts at 1), {@code serial[min]} or {@code
 * serial[min..max]}. The ceiling only bounds the record count (checked at startup); values are
 * always {@code min + index}.
 */
@Value
public class SerialType implements DataType {
  long min;
  long max;
  boolean bounded;

  public SerialType(long min) {
    this(min, Long.MAX_VALUE, false);
  }

  public SerialType(long min, long max) {
    this(min, max, true);
  }

  private SerialType(long min, long max, boolean bounded) {
    this.min = min;
    this.max = max;
    this.bounded = bounded;
  }

  @Override
  public String describe() {
    if (bounded) {
      return "serial[" + min + ".." + max + "]";
    }
    return min == 1 ? "serial" : "serial[" + min + "]";
  }
}
