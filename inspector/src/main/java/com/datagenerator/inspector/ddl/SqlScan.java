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

import java.util.Locale;
import java.util.Set;

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

  /**
   * Index just past the comment, string literal or quoted identifier ({@code "..."}, {@code `...`},
   * {@code [...]}) that starts at {@code i}, else {@code i} itself.
   */
  static int skipOpaque(String s, int i) {
    char c = s.charAt(i);
    if (isBlockCommentStart(s, i)) {
      return skipBlockComment(s, i);
    } else if (isLineCommentStart(s, i)) {
      return skipLineComment(s, i);
    } else if (c == '\'') {
      return skipSingleQuoted(s, i);
    } else if (c == '"' || c == '`' || opensBracketIdentifier(s, i)) {
      return skipDelimited(s, i, c == '[' ? ']' : c);
    }
    return i;
  }

  /** Keywords after which an identifier comes, so a {@code [} directly after one opens it. */
  private static final Set<String> IDENTIFIER_KEYWORDS =
      Set.of(
          "TABLE",
          "REFERENCES",
          "CONSTRAINT",
          "ON",
          "AS",
          "INDEX",
          "UNIQUE",
          "KEY",
          "FOREIGN",
          "PRIMARY",
          "CHECK",
          "ALTER",
          "COLUMN",
          "ADD",
          "INTO",
          "FROM",
          "JOIN",
          "EXISTS",
          "ONLY",
          "SCHEMA",
          "VIEW",
          "CLUSTERED",
          "NONCLUSTERED",
          "BY");

  /**
   * True if the {@code [} at {@code i} opens a bracket-quoted identifier. A bracket after a space
   * or punctuation does. A bracket right after a word is an identifier only when that word is a
   * keyword such as {@code REFERENCES[p]}; after a type or other word ({@code ARRAY[...]}, {@code
   * text[]}, {@code int[]}) it is a subscript or array.
   */
  static boolean opensBracketIdentifier(String s, int i) {
    if (s.charAt(i) != '[') {
      return false;
    }
    if (i == 0) {
      return true;
    }
    char before = s.charAt(i - 1);
    if (!(Character.isLetterOrDigit(before) || before == '_')) {
      return true;
    }
    int start = i - 1;
    while (start > 0
        && (Character.isLetterOrDigit(s.charAt(start - 1)) || s.charAt(start - 1) == '_')) {
      start--;
    }
    return IDENTIFIER_KEYWORDS.contains(s.substring(start, i).toUpperCase(Locale.ROOT));
  }

  /**
   * Returns {@code s} with comments removed and single-quoted strings and dollar-quoted bodies
   * replaced by {@code ''}, so keyword checks cannot be fooled by text inside them. Quoted
   * identifiers are copied verbatim (a {@code --} or {@code '} inside one is not a
   * comment/literal).
   */
  static String stripCommentsAndLiterals(String s) {
    StringBuilder out = new StringBuilder(s.length());
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (isLineCommentStart(s, i)) {
        i = skipLineComment(s, i);
      } else if (isBlockCommentStart(s, i)) {
        i = skipBlockComment(s, i);
        out.append(' ');
      } else if (c == '\'') {
        i = skipSingleQuoted(s, i);
        out.append("''");
      } else if (c == '"' || c == '`' || opensBracketIdentifier(s, i)) {
        int end = skipDelimited(s, i, c == '[' ? ']' : c); // identifiers: copied verbatim
        out.append(s, i, Math.min(end, s.length()));
        i = end;
      } else if (c == '$' && skipDollarQuoted(s, i) > i) {
        i = skipDollarQuoted(s, i);
        out.append("''");
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  /**
   * Index just past a dollar-quoted string ({@code $$...$$} or {@code $tag$...$tag$}) opened at
   * {@code i}, or {@code i} itself when no valid opening tag starts there.
   */
  static int skipDollarQuoted(String s, int i) {
    int tagEnd = i + 1;
    while (tagEnd < s.length()
        && (Character.isLetterOrDigit(s.charAt(tagEnd)) || s.charAt(tagEnd) == '_')) {
      tagEnd++;
    }
    if (tagEnd >= s.length() || s.charAt(tagEnd) != '$') {
      return i;
    }
    String tag = s.substring(i, tagEnd + 1);
    int close = s.indexOf(tag, tagEnd + 1);
    return close < 0 ? s.length() : close + tag.length();
  }
}
