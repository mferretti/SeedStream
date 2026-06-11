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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.MappedType.Reason;
import org.junit.jupiter.api.Test;

class MappedTypeTest {

  // --- factory methods ---

  @Test
  void declaredFactorySetsDatatypeAndReason() {
    MappedType mt = MappedType.declared("boolean");
    assertThat(mt.datatype()).isEqualTo("boolean");
    assertThat(mt.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void defaultRangeFactorySetsDatatypeAndReason() {
    MappedType mt = MappedType.defaultRange("int[1..999999]");
    assertThat(mt.datatype()).isEqualTo("int[1..999999]");
    assertThat(mt.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void nameHintFactorySetsDatatypeAndReason() {
    MappedType mt = MappedType.nameHint("email");
    assertThat(mt.datatype()).isEqualTo("email");
    assertThat(mt.reason()).isEqualTo(Reason.NAME_HINT);
  }

  @Test
  void unknownTypeFactorySetsDatatypeAndReason() {
    MappedType mt = MappedType.unknownType("char[1..50]");
    assertThat(mt.datatype()).isEqualTo("char[1..50]");
    assertThat(mt.reason()).isEqualTo(Reason.UNKNOWN_TYPE);
  }

  // --- flagged() ---

  @Test
  void declaredIsNotFlagged() {
    assertThat(MappedType.declared("boolean").flagged()).isFalse();
  }

  @Test
  void defaultRangeIsNotFlagged() {
    assertThat(MappedType.defaultRange("int[1..999999]").flagged()).isFalse();
  }

  @Test
  void nameHintIsFlagged() {
    assertThat(MappedType.nameHint("email").flagged()).isTrue();
  }

  @Test
  void unknownTypeIsFlagged() {
    assertThat(MappedType.unknownType("char[1..50]").flagged()).isTrue();
  }

  // --- comment() ---

  @Test
  void nameHintCommentText() {
    assertThat(MappedType.nameHint("email").comment())
        .isEqualTo("guessed from column name — verify");
  }

  @Test
  void unknownTypeCommentText() {
    assertThat(MappedType.unknownType("char[1..50]").comment())
        .isEqualTo("unrecognized source type, defaulted — verify");
  }

  @Test
  void declaredCommentIsNull() {
    assertThat(MappedType.declared("boolean").comment()).isNull();
  }

  @Test
  void defaultRangeCommentIsNull() {
    assertThat(MappedType.defaultRange("int[1..999999]").comment()).isNull();
  }
}
