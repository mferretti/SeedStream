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

import com.datagenerator.core.engine.RecordIndex;
import com.datagenerator.core.type.CycleType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorException;
import java.util.List;
import java.util.Random;

/**
 * Generates {@code values[recordIndex mod n]} for {@code cycle[...]} fields. Stateless, draws
 * nothing from the per-record {@link Random}, so it never shifts other fields' values.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * cycle[NEW,OPEN,CLOSED] → records 0,1,2,3,... get NEW,OPEN,CLOSED,NEW,...
 * </pre>
 */
public class CycleGenerator implements DataGenerator {

  @Override
  public Object generate(Random random, DataType dataType) {
    if (!(dataType instanceof CycleType cycle)) {
      throw new GeneratorException(
          "CycleGenerator requires CycleType, got: " + dataType.getClass().getSimpleName());
    }
    long index = RecordIndex.current();
    if (index < 0) {
      throw new GeneratorException("cycle requires the generation engine's record index");
    }
    List<String> values = cycle.getValues();
    if (values.isEmpty()) {
      throw new GeneratorException("CycleType has no values to generate from");
    }
    return values.get(Math.floorMod(index, values.size()));
  }

  @Override
  public boolean supports(DataType dataType) {
    return dataType instanceof CycleType;
  }
}
