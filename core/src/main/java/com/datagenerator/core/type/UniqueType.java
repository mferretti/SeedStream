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
 * A collision-free integer column: {@code unique[min..max]} or {@code unique[group, min..max]}.
 * Fields sharing a group form one composite key whose tuples are distinct across all records.
 *
 * <p>The layout fields (groupKey onward) are filled by the structure-level resolution pass in
 * {@code StructureRegistry}; a freshly parsed instance is unresolved.
 */
@Value
public class UniqueType implements DataType {
  /** Explicit group name, or {@code null} for the implicit single-field group. */
  String group;

  long min;

  /** Upper bound; for {@code ..count} ranges 0 until resolved, then the effective max. */
  long max;

  /** True when the range was written as {@code min..count}. */
  boolean maxIsCount;

  /** Documentation-only {@code structure.field} for {@code ref[..., unique]} forms; nullable. */
  String refTarget;

  /** {@code structureName.groupName}; {@code null} until resolved. */
  String groupKey;

  long groupHash;

  /** Product of the group's field range sizes. */
  long domain;

  /** Mixed-radix divisor of this field within the group (1 for the last field). */
  long divisor;

  /** This field's range size ({@code max - min + 1}). */
  long size;

  /** Feistel half-bits: smallest h >= 1 with 4^h >= domain. */
  int halfBits;

  /** Creates an unresolved instance as produced by the parser. */
  public UniqueType(String group, long min, long max) {
    this(group, min, max, false, null);
  }

  /** Creates an unresolved instance as produced by the parser. */
  public UniqueType(String group, long min, long max, boolean maxIsCount, String refTarget) {
    this(group, min, max, maxIsCount, refTarget, null, 0L, 0L, 0L, 0L, 0);
  }

  @SuppressWarnings("java:S107")
  private UniqueType(
      String group,
      long min,
      long max,
      boolean maxIsCount,
      String refTarget,
      String groupKey,
      long groupHash,
      long domain,
      long divisor,
      long size,
      int halfBits) {
    this.group = group;
    this.min = min;
    this.max = max;
    this.maxIsCount = maxIsCount;
    this.refTarget = refTarget;
    this.groupKey = groupKey;
    this.groupHash = groupHash;
    this.domain = domain;
    this.divisor = divisor;
    this.size = size;
    this.halfBits = halfBits;
  }

  public boolean isResolved() {
    return groupKey != null;
  }

  /** Returns a resolved copy carrying the given group layout. */
  public UniqueType withLayout(
      long resolvedMax,
      String groupKey,
      long groupHash,
      long domain,
      long divisor,
      long size,
      int halfBits) {
    return new UniqueType(
        group,
        min,
        resolvedMax,
        maxIsCount,
        refTarget,
        groupKey,
        groupHash,
        domain,
        divisor,
        size,
        halfBits);
  }

  @Override
  public String describe() {
    String range = min + ".." + (maxIsCount ? "count" : String.valueOf(max));
    if (refTarget != null) {
      return "ref["
          + refTarget
          + ", "
          + range
          + ", unique"
          + (group != null ? "=" + group : "")
          + "]";
    }
    return "unique[" + (group != null ? group + ", " : "") + range + "]";
  }
}
