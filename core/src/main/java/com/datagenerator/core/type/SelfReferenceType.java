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

package com.datagenerator.core.type;

import com.datagenerator.core.structure.StructureRegistry;
import lombok.Value;

/**
 * A bare {@code ref[t.c]} inside structure {@code t} itself (#213): record {@code i} points at the
 * key of a uniformly random earlier record. Built by the engine from the key field's declared type;
 * the registry swaps in the key's resolved layout once unique groups are resolved.
 */
@Value
public class SelfReferenceType implements DataType {
  /** {@code structure.field} of the referencing field; seeds the choice of the earlier record. */
  String path;

  /** Stable hash of {@link #path}, computed once so the per-record choice does not hash text. */
  long pathHash;

  /** Field of the same structure holding the referenced key. */
  String keyField;

  /** The key's type: {@link SerialType} or (after resolution) a resolved {@link UniqueType}. */
  DataType keyType;

  @SuppressWarnings("checkstyle:HiddenField")
  public SelfReferenceType(String path, String keyField, DataType keyType) {
    this.path = path;
    this.pathHash = StructureRegistry.fnv1a64("self-ref:" + path);
    this.keyField = keyField;
    this.keyType = keyType;
  }

  /** Copy carrying {@code resolvedKey} (the key field's type after unique layout resolution). */
  public SelfReferenceType withKeyType(DataType resolvedKey) {
    return new SelfReferenceType(path, keyField, resolvedKey);
  }

  @Override
  public String describe() {
    int dot = path.indexOf('.');
    return "ref[" + path.substring(0, dot) + "." + keyField + "]";
  }
}
