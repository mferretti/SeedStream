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
import com.datagenerator.core.type.ParentReferenceType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.util.Map;
import java.util.Random;

/**
 * Copies a field value from the immediately enclosing parent record.
 *
 * <p>Reads the top of the parent-record stack maintained by {@link GeneratorContext}. The stack is
 * pushed by {@link ObjectGenerator} before generating any nested {@code array[object[...]]} or
 * {@code object[...]} field, so the parent's scalar fields (e.g. {@code id}) are already present
 * when this generator runs for a child field like {@code author_id: ref[parent.id]}.
 */
public class ParentReferenceGenerator implements DataGenerator {

  @Override
  public boolean supports(DataType type) {
    return type instanceof ParentReferenceType;
  }

  @Override
  public Object generate(Random random, DataType type) {
    if (!(type instanceof ParentReferenceType prt)) {
      throw new GeneratorException(
          "ParentReferenceGenerator only supports ParentReferenceType, got: " + type);
    }

    Map<String, Object> parentRecord = GeneratorContext.peekParentRecord();
    if (parentRecord == null) {
      throw new GeneratorException(
          "ref[parent."
              + prt.getTargetField()
              + "] used outside a nested generation context. "
              + "This type is only valid inside array[object[...]] or object[...] fields.");
    }

    Object value = parentRecord.get(prt.getTargetField());
    if (value == null) {
      throw new GeneratorException(
          "Parent field '"
              + prt.getTargetField()
              + "' not found in parent record. "
              + "Declare it before the nested field that references it.");
    }
    return value;
  }
}
