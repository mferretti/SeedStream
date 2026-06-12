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

package com.datagenerator.inspector.ddl;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.InspectorException;
import com.datagenerator.inspector.ddl.NestingOptions.Mode;
import org.junit.jupiter.api.Test;

class NestingOptionsTest {

  @Test
  void noneFactoryIsDisabled() {
    NestingOptions opts = NestingOptions.none();
    assertThat(opts.mode()).isEqualTo(Mode.NONE);
    assertThat(opts.enabled()).isFalse();
  }

  @Test
  void emptyAndAutoBothMeanAuto() {
    assertThat(NestingOptions.parse("", null).mode()).isEqualTo(Mode.AUTO);
    assertThat(NestingOptions.parse("auto", null).mode()).isEqualTo(Mode.AUTO);
    assertThat(NestingOptions.parse("AUTO", null).enabled()).isTrue();
  }

  @Test
  void allAndNoneParse() {
    assertThat(NestingOptions.parse("all", null).mode()).isEqualTo(Mode.ALL);
    assertThat(NestingOptions.parse("none", null).mode()).isEqualTo(Mode.NONE);
  }

  @Test
  void defaultCountDefaultsToOneToTen() {
    NestingOptions opts = NestingOptions.parse("auto", null);
    assertThat(opts.defaultMin()).isEqualTo(1);
    assertThat(opts.defaultMax()).isEqualTo(10);
  }

  @Test
  void defaultCountIsParsed() {
    NestingOptions opts = NestingOptions.parse("auto", "3..7");
    assertThat(opts.defaultMin()).isEqualTo(3);
    assertThat(opts.defaultMax()).isEqualTo(7);
  }

  @Test
  void invalidModeThrows() {
    assertThatThrownBy(() -> NestingOptions.parse("sideways", null))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("auto|all|none");
  }

  @Test
  void countWithoutSeparatorThrows() {
    assertThatThrownBy(() -> NestingOptions.parse("auto", "5"))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("min..max");
  }

  @Test
  void nonIntegerCountThrows() {
    assertThatThrownBy(() -> NestingOptions.parse("auto", "a..b"))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("integer");
  }

  @Test
  void invertedRangeThrows() {
    assertThatThrownBy(() -> NestingOptions.parse("auto", "9..2"))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("min <= max");
  }
}
