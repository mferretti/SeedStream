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

import com.datagenerator.core.type.TypeParser;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.DataStructureParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Test helper: write an inspected structure to YAML and load it back with the real parsers. */
public final class WrittenStructureSupport {

  private WrittenStructureSupport() {}

  /**
   * Writes the named structure (with its review comments) under {@code outDir}, loads it with the
   * real {@link DataStructureParser}, and checks that every datatype is accepted by the engine's
   * {@link TypeParser}.
   */
  public static DataStructure writeAndLoad(Inspection inspection, String name, Path outDir) {
    DataStructure structure =
        inspection.structures().stream()
            .filter(s -> s.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no structure named " + name));
    new StructureYamlWriter()
        .write(structure, outDir, true, inspection.comments().getOrDefault(name, Map.of()));
    DataStructure loaded = new DataStructureParser().parse(outDir.resolve(name + ".yaml"));
    TypeParser typeParser = new TypeParser();
    loaded.getData().forEach((field, def) -> typeParser.parse(def.getDatatype()));
    return loaded;
  }

  /** The YAML text {@link #writeAndLoad} produced for {@code name}. */
  public static String yamlOf(String name, Path outDir) throws IOException {
    return Files.readString(outDir.resolve(name + ".yaml"));
  }
}
