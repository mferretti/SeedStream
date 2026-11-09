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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Parsing of the count-resolved reference forms: {@code ref[t.c]}, {@code ref[t.c, unique]}. */
class ParentRefParserTest {
  private TypeParser parser;

  @BeforeEach
  void setUp() {
    parser = new TypeParser();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "ref[t.c]|ref[t.c]",
        "ref[t.c, unique]|ref[t.c, unique]",
        "ref[t.c,unique]|ref[t.c, unique]",
        "ref[t.c ,   unique]|ref[t.c, unique]",
        "ref[t.c, unique=g]|ref[t.c, unique=g]",
        "ref[t.c,unique=pk_1]|ref[t.c, unique=pk_1]"
      })
  void shouldParseAndDescribeCanonicallyWhenBareRefForms(String input, String canonical) {
    DataType parsed = parser.parse(input);

    assertThat(parsed.describe()).isEqualTo(canonical);
    assertThat(parser.parse(parsed.describe()).describe()).isEqualTo(canonical);
  }

  @Test
  void shouldKeepBareRefWithoutPoolWhenParsed() {
    ReferenceType ref = (ReferenceType) parser.parse("ref[t.c]");

    assertThat(ref.getTargetStructure()).isEqualTo("t");
    assertThat(ref.getTargetField()).isEqualTo("c");
    assertThat(ref.getMin()).isNull();
    assertThat(ref.getMax()).isNull();
  }

  @Test
  void shouldNotChangeExistingRefFormsWhenParsed() {
    assertThat(parser.parse("ref[t.c, 1..500]").describe()).isEqualTo("ref[t.c, 1..500]");
    assertThat(parser.parse("ref[t.c, 1..count]").describe()).isEqualTo("ref[t.c, 1..count]");
    assertThat(parser.parse("ref[t.c, 1..500, unique]").describe())
        .isEqualTo("ref[t.c, 1..500, unique]");
    assertThat(parser.parse("ref[t.c, 1..count, unique=g]").describe())
        .isEqualTo("ref[t.c, 1..count, unique=g]");
  }
}
