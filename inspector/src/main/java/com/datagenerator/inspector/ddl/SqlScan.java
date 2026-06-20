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

/**
 * Low-level character-scan primitives shared by the DDL splitter and preprocessor. Each {@code
 * skip} method takes the source string and the index of the construct's opening character and
 * returns the index of the first character past the construct.
 */
final class SqlScan {

  private SqlScan() {}

  /** True if a line comment ({@code --}) starts at {@code i}. */
  static boolean isLineCommentStart(String s, int i) {
    return s.charAt(i) == '-' && i + 1 < s.length() && s.charAt(i + 1) == '-';
  }

  /** Index of the {@code \n} (or end) that terminates a line comment opened at {@code i}. */
  static int skipLineComment(String s, int i) {
    int len = s.length();
    int j = i + 2;
    while (j < len && s.charAt(j) != '\n') {
      j++;
    }
    return j;
  }

  /** True if a block comment ({@code /*}) starts at {@code i}. */
  static boolean isBlockCommentStart(String s, int i) {
    return s.charAt(i) == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*';
  }

  /** Index just past the closing {@code * /} of a block comment opened at {@code i}. */
  static int skipBlockComment(String s, int i) {
    int len = s.length();
    int j = i + 2;
    while (j + 1 < len && !(s.charAt(j) == '*' && s.charAt(j + 1) == '/')) {
      j++;
    }
    return j + 2;
  }

  /** Index just past a single-quoted string opened at {@code i}; {@code ''} is an escaped quote. */
  static int skipSingleQuoted(String s, int i) {
    int len = s.length();
    int j = i + 1;
    while (j < len) {
      char c = s.charAt(j);
      j++;
      if (c == '\'') {
        if (j < len && s.charAt(j) == '\'') {
          j++; // escaped ''
        } else {
          return j;
        }
      }
    }
    return j;
  }

  /** Index just past a region delimited by {@code delim} (no escape form), opened at {@code i}. */
  static int skipDelimited(String s, int i, char delim) {
    int len = s.length();
    int j = i + 1;
    while (j < len && s.charAt(j) != delim) {
      j++;
    }
    return j + 1;
  }
}
