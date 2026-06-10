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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Flyweight {@code Map<String, Object>} for a generated record: an interned {@link RecordSchema}
 * (shared by all records of the structure) plus a per-record {@code Object[]} of values.
 *
 * <p>Replaces a per-record {@code LinkedHashMap}, which allocates a hash table plus one {@code
 * Node} per field and re-stores the field-name strings for every record. At millions of records
 * that short-lived allocation dominates GC; this flyweight allocates only the value array per
 * record.
 *
 * <p>It is a full {@link java.util.Map} so every downstream consumer (serializers, destinations,
 * {@code ref[parent.*]} resolution) works unchanged. Iteration order is the schema's field order,
 * matching the previous {@code LinkedHashMap} output order.
 *
 * <p><b>Thread Safety:</b> Not thread-safe; each record is built and consumed within one worker's
 * scope, exactly like the {@code LinkedHashMap} it replaces.
 */
public final class FieldRecord extends AbstractMap<String, Object> {

  private final RecordSchema schema;
  private final Object[] values;

  /**
   * Create an empty record for the given schema.
   *
   * @param schema interned field layout shared across records of one structure
   */
  public FieldRecord(RecordSchema schema) {
    this.schema = schema;
    this.values = new Object[schema.size()];
  }

  @Override
  public Object get(Object key) {
    if (!(key instanceof String name)) {
      return null;
    }
    int i = schema.indexOf(name);
    return i < 0 ? null : values[i];
  }

  @Override
  public boolean containsKey(Object key) {
    return key instanceof String name && schema.indexOf(name) >= 0;
  }

  @Override
  public Object put(String key, Object value) {
    int i = schema.indexOf(key);
    if (i < 0) {
      throw new IllegalArgumentException("Field not declared in schema: " + key);
    }
    Object previous = values[i];
    values[i] = value;
    return previous;
  }

  @Override
  public int size() {
    return values.length;
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    return new FieldEntrySet();
  }

  /** Live, field-ordered view over the record's entries. */
  private final class FieldEntrySet extends AbstractSet<Entry<String, Object>> {
    @Override
    public int size() {
      return values.length;
    }

    @Override
    public Iterator<Entry<String, Object>> iterator() {
      return new Iterator<>() {
        private int cursor;

        @Override
        public boolean hasNext() {
          return cursor < values.length;
        }

        @Override
        public Entry<String, Object> next() {
          if (cursor >= values.length) {
            throw new NoSuchElementException();
          }
          int idx = cursor++;
          return new SimpleImmutableEntry<>(schema.nameAt(idx), values[idx]);
        }
      };
    }
  }
}
