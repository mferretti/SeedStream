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
import com.datagenerator.generators.DataGenerator;
import java.util.Random;

/** STUB (#456): compile-only placeholder, the developer implements it. */
public class CycleGenerator implements DataGenerator {

  @Override
  public Object generate(Random random, DataType dataType) {
    throw new UnsupportedOperationException("cycle[...] not implemented yet (#456)");
  }

  @Override
  public boolean supports(DataType dataType) {
    return false;
  }
}
