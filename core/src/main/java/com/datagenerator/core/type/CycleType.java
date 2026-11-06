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

import java.util.List;
import lombok.Value;

/**
 * An index-driven enumeration: {@code cycle[a,b,c]}. Record {@code i} gets {@code values[i mod n]}.
 *
 * <p>The value is a pure function of the global record index: it is seed-independent, consumes no
 * draws from the per-record {@link java.util.Random}, and is therefore thread-count independent.
 * With {@code --count n} each value appears exactly once per {@code n} records. Not allowed under
 * {@code array[...]}, because array elements share their record's index.
 */
@Value
public class CycleType implements DataType {
  List<String> values;

  @Override
  public String describe() {
    return "cycle[" + String.join(",", values) + "]";
  }
}
