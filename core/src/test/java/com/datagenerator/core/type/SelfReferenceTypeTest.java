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

import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.core.structure.StructureRegistry;
import org.junit.jupiter.api.Test;

/** Construction, copy-on-resolve and description of the self-referencing {@code ref[t.c]} type. */
class SelfReferenceTypeTest {

  @Test
  void shouldHashPathWithSelfRefPrefixWhenConstructed() {
    SelfReferenceType type = new SelfReferenceType("employee.manager_id", "id", new SerialType(1));

    assertThat(type.getPathHash())
        .isEqualTo(StructureRegistry.fnv1a64("self-ref:employee.manager_id"));
    assertThat(type.getPathHash()).isNotEqualTo(StructureRegistry.fnv1a64("employee.manager_id"));
  }

  @Test
  void shouldProduceSameHashWhenConstructedTwiceWithSamePath() {
    SelfReferenceType first = new SelfReferenceType("t.parent", "id", new SerialType(1));
    SelfReferenceType second = new SelfReferenceType("t.parent", "id", new SerialType(1));

    assertThat(first.getPathHash()).isEqualTo(second.getPathHash());
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldProduceDifferentHashWhenPathsDiffer() {
    SelfReferenceType a = new SelfReferenceType("t.parent", "id", new SerialType(1));
    SelfReferenceType b = new SelfReferenceType("t.other", "id", new SerialType(1));

    assertThat(a.getPathHash()).isNotEqualTo(b.getPathHash());
  }

  @Test
  void shouldDescribeAsRefToOwnStructureKeyFieldWhenDescribed() {
    SelfReferenceType type = new SelfReferenceType("employee.manager_id", "id", new SerialType(1));

    assertThat(type.describe()).isEqualTo("ref[employee.id]");
  }

  @Test
  void shouldKeepPathKeyFieldAndKeyTypeWhenConstructed() {
    SerialType key = new SerialType(10);
    SelfReferenceType type = new SelfReferenceType("t.parent", "pk", key);

    assertThat(type.getPath()).isEqualTo("t.parent");
    assertThat(type.getKeyField()).isEqualTo("pk");
    assertThat(type.getKeyType()).isSameAs(key);
  }

  @Test
  void shouldReturnCopyWithResolvedKeyTypeWhenWithKeyType() {
    SelfReferenceType original = new SelfReferenceType("t.parent", "id", new SerialType(1));
    SerialType resolved = new SerialType(5, 50);

    SelfReferenceType copy = original.withKeyType(resolved);

    assertThat(copy).isNotSameAs(original);
    assertThat(copy.getKeyType()).isSameAs(resolved);
    assertThat(copy.getPath()).isEqualTo(original.getPath());
    assertThat(copy.getKeyField()).isEqualTo(original.getKeyField());
    assertThat(copy.getPathHash()).isEqualTo(original.getPathHash());
  }

  @Test
  void shouldLeaveOriginalUnchangedWhenWithKeyTypeCalled() {
    SerialType originalKey = new SerialType(1);
    SelfReferenceType original = new SelfReferenceType("t.parent", "id", originalKey);

    original.withKeyType(new SerialType(7));

    assertThat(original.getKeyType()).isSameAs(originalKey);
  }

  @Test
  void shouldUnwrapToItselfWhenNotWrapped() {
    SelfReferenceType type = new SelfReferenceType("t.parent", "id", new SerialType(1));

    assertThat(type.unwrap()).isSameAs(type);
  }
}
