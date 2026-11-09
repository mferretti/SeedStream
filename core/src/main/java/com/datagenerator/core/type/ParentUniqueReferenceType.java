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

import lombok.Value;

/**
 * {@code ref[t.c, unique]} / {@code ref[t.c, unique=g]}: a one-to-one reference whose pool is not
 * written down. It only exists between parsing and startup; the job's row counts turn it into the
 * {@link UniqueType} the user could have written by hand ({@code ref[t.c, lo..hi, unique]}).
 */
@Value
public class ParentUniqueReferenceType implements DataType {
  String targetStructure;
  String targetField;

  /** Explicit group name, or {@code null} for the implicit single-field group. */
  String group;

  @Override
  public String describe() {
    return "ref["
        + targetStructure
        + "."
        + targetField
        + ", unique"
        + (group != null ? "=" + group : "")
        + "]";
  }
}
