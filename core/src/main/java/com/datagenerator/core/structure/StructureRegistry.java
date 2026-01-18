package com.datagenerator.core.structure;

import com.datagenerator.core.exception.CircularReferenceException;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for loaded data structures with circular reference detection. Maintains a cache of
 * parsed structures and tracks loading stack to detect cycles.
 */
public class StructureRegistry {
  private final Map<String, Map<String, DataType>> structureCache = new HashMap<>();
  private final StructureLoader loader;
  private final ThreadLocal<Deque<String>> loadingStack = ThreadLocal.withInitial(ArrayDeque::new);

  public StructureRegistry(StructureLoader loader) {
    this.loader = loader;
  }

  /**
   * Load a structure by name, detecting circular references.
   *
   * @param structureName the name of the structure to load
   * @param structuresPath the base path for structure files
   * @return map of field names to DataType objects
   * @throws CircularReferenceException if a circular reference is detected
   */
  public Map<String, DataType> loadStructure(String structureName, Path structuresPath) {
    // Check cache first
    if (structureCache.containsKey(structureName)) {
      return structureCache.get(structureName);
    }

    // Check for circular reference
    Deque<String> stack = loadingStack.get();
    if (stack.contains(structureName)) {
      List<String> cycle = new ArrayList<>(stack);
      cycle.add(structureName);
      throw new CircularReferenceException(
          "Circular reference detected: " + String.join(" → ", cycle));
    }

    // Add to stack and load
    stack.push(structureName);
    try {
      Map<String, DataType> fields = loader.load(structureName, structuresPath, this);
      structureCache.put(structureName, fields);
      return fields;
    } finally {
      stack.pop();
      if (stack.isEmpty()) {
        loadingStack.remove(); // Clean up thread-local
      }
    }
  }

  /**
   * Validate all object references in a structure (recursive). Ensures all referenced structures
   * can be loaded without circular references.
   *
   * @param fields the fields to validate
   * @param structuresPath the base path for structure files
   */
  public void validateReferences(Map<String, DataType> fields, Path structuresPath) {
    for (DataType type : fields.values()) {
      validateType(type, structuresPath);
    }
  }

  private void validateType(DataType type, Path structuresPath) {
    if (type instanceof ObjectType objectType) {
      // This will trigger loading and cycle detection
      loadStructure(objectType.getStructureName(), structuresPath);
    } else if (type instanceof com.datagenerator.core.type.ArrayType arrayType) {
      validateType(arrayType.getElementType(), structuresPath);
    }
  }

  /** Clear the cache (useful for testing). */
  public void clearCache() {
    structureCache.clear();
  }
}
