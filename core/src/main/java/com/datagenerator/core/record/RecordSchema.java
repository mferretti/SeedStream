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

package com.datagenerator.core.record;

import java.util.HashMap;
import java.util.Map;

/**
 * Interned, immutable field layout shared by every {@link FieldRecord} of one structure.
 *
 * <p>Built once per structure and reused for all of its records, so per-record generation allocates
 * only a value array — not a fresh field-name set and hash table. The ordering of {@code
 * orderedNames} is the exact serialization order of the record's fields.
 *
 * <p><b>Thread Safety:</b> Immutable after construction; safe to share across worker threads.
 */
public final class RecordSchema {

  private final String[] names;
  private final Map<String, Integer> indexByName;

  /**
   * Create a schema from field names in serialization order.
   *
   * @param orderedNames field names, in the order they should appear in output (defensively copied)
   */
  public RecordSchema(String[] orderedNames) {
    this.names = orderedNames.clone();
    Map<String, Integer> idx = HashMap.newHashMap(names.length);
    for (int i = 0; i < names.length; i++) {
      idx.put(names[i], i);
    }
    this.indexByName = idx;
  }

  /**
   * Number of fields in this schema.
   *
   * @return field count
   */
  public int size() {
    return names.length;
  }

  /**
   * Field name at the given slot.
   *
   * @param i slot index in {@code [0, size())}
   * @return field name
   */
  public String nameAt(int i) {
    return names[i];
  }

  /**
   * Slot index for a field name.
   *
   * @param name field name
   * @return slot index, or {@code -1} if the name is not part of this schema
   */
  public int indexOf(String name) {
    Integer i = indexByName.get(name);
    return i == null ? -1 : i;
  }
}
