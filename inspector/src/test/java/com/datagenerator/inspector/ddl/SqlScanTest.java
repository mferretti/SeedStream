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

import org.junit.jupiter.api.Test;

class SqlScanTest {

  @Test
  void lineCommentStartDetection() {
    assertThat(SqlScan.isLineCommentStart("-- x", 0)).isTrue();
    assertThat(SqlScan.isLineCommentStart("- x", 0)).isFalse();
    assertThat(SqlScan.isLineCommentStart("-", 0)).isFalse();
  }

  @Test
  void skipLineCommentStopsAtNewlineOrEnd() {
    assertThat(SqlScan.skipLineComment("-- xy\nz", 0)).isEqualTo(5);
    assertThat(SqlScan.skipLineComment("-- xy", 0)).isEqualTo(5);
  }

  @Test
  void blockCommentStartDetection() {
    assertThat(SqlScan.isBlockCommentStart("/* x", 0)).isTrue();
    assertThat(SqlScan.isBlockCommentStart("/x", 0)).isFalse();
    assertThat(SqlScan.isBlockCommentStart("/", 0)).isFalse();
  }

  @Test
  void skipBlockCommentLandsPastClose() {
    assertThat(SqlScan.skipBlockComment("/* x */y", 0)).isEqualTo(7);
    // unterminated block comment runs to end
    assertThat(SqlScan.skipBlockComment("/* x", 0)).isEqualTo(5);
  }

  @Test
  void skipSingleQuotedHandlesEscapeAndUnterminated() {
    assertThat(SqlScan.skipSingleQuoted("'ab'", 0)).isEqualTo(4);
    assertThat(SqlScan.skipSingleQuoted("'a''b'x", 0)).isEqualTo(6);
    assertThat(SqlScan.skipSingleQuoted("'ab", 0)).isEqualTo(3);
  }

  @Test
  void skipDelimitedConsumesToClosingDelimiter() {
    assertThat(SqlScan.skipDelimited("\"ab\"x", 0, '"')).isEqualTo(4);
    assertThat(SqlScan.skipDelimited("`ab`x", 0, '`')).isEqualTo(4);
  }
}
