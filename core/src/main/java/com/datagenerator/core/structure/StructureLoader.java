package com.datagenerator.core.structure;

import com.datagenerator.core.type.DataType;
import java.nio.file.Path;
import java.util.Map;

/**
 * Interface for loading structure definitions. Implementations parse structure files (e.g., YAML)
 * and return field definitions.
 */
@FunctionalInterface
public interface StructureLoader {
  /**
   * Load a structure definition from file.
   *
   * @param structureName the name of the structure
   * @param structuresPath the base path for structure files
   * @param registry the registry for resolving nested structures
   * @return map of field names to DataType objects
   */
  Map<String, DataType> load(String structureName, Path structuresPath, StructureRegistry registry);
}
