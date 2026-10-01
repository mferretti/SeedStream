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

import com.datagenerator.schema.model.DataStructure;
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
}
