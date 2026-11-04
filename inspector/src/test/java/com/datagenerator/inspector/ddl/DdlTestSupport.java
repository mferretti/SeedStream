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

package com.datagenerator.inspector.ddl;

import com.datagenerator.core.type.TypeParser;
import com.datagenerator.inspector.Inspection;
import com.datagenerator.inspector.StructureYamlWriter;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.DataStructureParser;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test helpers that load inspected structures back with the real parsers. */
final class DdlTestSupport {

  private static final Pattern REF = Pattern.compile("^ref\\[([^,\\]]+),");

  private DdlTestSupport() {}

  /**
   * Writes every structure to {@code dir} and loads it back with {@link DataStructureParser}, then
   * parses every datatype with {@link TypeParser}. Fails (throws) when anything does not load.
   */
  static Map<String, DataStructure> loadAll(Inspection inspection, Path dir) {
    StructureYamlWriter writer = new StructureYamlWriter();
    DataStructureParser parser = new DataStructureParser();
    TypeParser typeParser = new TypeParser();
    Map<String, DataStructure> loaded = new LinkedHashMap<>();
    for (DataStructure structure : inspection.structures()) {
      writer.write(structure, dir, true, Map.of());
      DataStructure parsed = parser.parse(dir.resolve(structure.getName() + ".yaml"));
      parsed.getData().values().forEach(f -> typeParser.parse(f.getDatatype()));
      loaded.put(parsed.getName(), parsed);
    }
    return loaded;
  }

  /** {@code ref[orders.order_id, 1..count, unique]} to {@code orders.order_id}, else null. */
  static String refTarget(String datatype) {
    Matcher m = REF.matcher(datatype);
    return m.find() ? m.group(1).trim() : null;
  }
}
