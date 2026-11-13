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
 * A field declared with {@code nullable: <rate>} (rate &gt; 0): the inner type's value, replaced by
 * null with probability {@code rate}. Added by the structure loader around the field's type; the
 * path ({@code structure.field}) keys the null decision.
 */
@Value
public class NullableType implements DataType {
  DataType inner;
  double rate;
  String path;

  /** Stable hash of {@link #path}, computed once so the per-record decision does not hash text. */
  long pathHash;

  @SuppressWarnings("checkstyle:HiddenField")
  public NullableType(DataType inner, double rate, String path) {
    this.inner = inner;
    this.rate = rate;
    this.path = path;
    this.pathHash = StructureRegistry.fnv1a64(path);
  }

  @Override
  public DataType unwrap() {
    return inner.unwrap();
  }

  @Override
  public String describe() {
    return inner.describe() + " nullable " + rate;
  }
}
