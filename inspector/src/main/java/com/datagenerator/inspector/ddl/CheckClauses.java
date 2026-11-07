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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds the {@code CHECK (...)} clauses of a {@code CREATE TABLE} or {@code ALTER TABLE} statement
 * by scanning its text (quotes, brackets and comments are skipped), so the condition is kept as
 * written whatever the parser makes of it. {@link #neutralize} swaps each condition for a
 * placeholder the parser always accepts and drops the state words some dialects put after it
 * ({@code ENABLE}, {@code DISABLE}, {@code VALIDATE}, {@code NOVALIDATE}, {@code NO INHERIT}), so a
 * {@code CHECK} can never make a statement fail to parse. Those words are only removed right after
 * a clause's closing parenthesis: a column or identifier of that name is never touched.
 */
final class CheckClauses {

  /**
   * A clause: its constraint name (null if unnamed), condition text and {@code NO INHERIT} flag.
   */
  record Clause(String name, String condition, boolean noInherit) {}

  /** Where a clause sits in the statement text. */
  private record Span(int start, int conditionStart, int conditionEnd, int end, Clause clause) {}

  private static final Set<String> STATE_WORDS =
      Set.of("ENABLE", "DISABLE", "VALIDATE", "NOVALIDATE");
  private static final String PLACEHOLDER = "1 = 1";
  private static final String SYNTHETIC_NAME = "CONSTRAINT seedstream_check ";

  private CheckClauses() {}

  /** Every {@code CHECK (...)} clause of the statement, in order. */
  static List<Clause> find(String sql) {
    return scan(sql).stream().map(Span::clause).toList();
  }

  /**
   * The statement with every condition replaced by {@code 1 = 1} and the state words after it
   * removed. With {@code nameAll}, an unnamed clause gets a synthetic constraint name, because the
   * parser rejects {@code ALTER TABLE t ADD CHECK (...)}.
   */
  static String neutralize(String sql, boolean nameAll) {
    StringBuilder out = new StringBuilder(sql.length());
    int pos = 0;
    for (Span span : scan(sql)) {
      out.append(sql, pos, span.start());
      if (nameAll && span.clause().name() == null) {
        out.append(SYNTHETIC_NAME);
      }
      out.append(sql, span.start(), span.conditionStart()).append(PLACEHOLDER).append(')');
      pos = span.end();
    }
    return out.append(sql, pos, sql.length()).toString();
  }

  private static List<Span> scan(String s) {
    List<Span> spans = new ArrayList<>();
    int len = s.length();
    String name = null;
    int nameEnd = -1;
    int i = 0;
    while (i < len) {
      int opaque = SqlScan.skipOpaque(s, i);
      if (opaque > i) {
        i = Math.min(opaque, len);
      } else if (isWordChar(s.charAt(i))) {
        int end = wordEnd(s, i);
        String word = s.substring(i, end);
        if ("CONSTRAINT".equalsIgnoreCase(word)) {
          int identStart = skipSpaces(s, end);
          nameEnd = identEnd(s, identStart);
          name = TableModel.unquote(s.substring(identStart, nameEnd));
          i = nameEnd;
        } else if ("CHECK".equalsIgnoreCase(word) && opensParen(s, end)) {
          boolean named = name != null && s.substring(nameEnd, i).isBlank();
          int open = skipSpaces(s, end);
          int close = matchingParen(s, open);
          if (close < 0) {
            return spans; // unbalanced: leave the rest to the parser
          }
          spans.add(span(s, i, open, close, named ? name : null));
          i = spans.get(spans.size() - 1).end();
        } else {
          i = end;
        }
      } else {
        i++;
      }
    }
    return spans;
  }

  private static Span span(String s, int start, int open, int close, String name) {
    int end = close + 1;
    boolean noInherit = false;
    while (true) {
      int wordStart = skipSpaces(s, end);
      int wordEnd = wordEnd(s, wordStart);
      String word = s.substring(wordStart, wordEnd);
      if (STATE_WORDS.contains(word.toUpperCase(Locale.ROOT))) {
        end = wordEnd;
      } else if ("NO".equalsIgnoreCase(word) && followedByInherit(s, wordEnd)) {
        noInherit = true;
        end = wordEnd(s, skipSpaces(s, wordEnd));
      } else {
        break;
      }
    }
    String condition = s.substring(open + 1, close).strip();
    return new Span(start, open + 1, close, end, new Clause(name, condition, noInherit));
  }

  private static boolean followedByInherit(String s, int from) {
    int start = skipSpaces(s, from);
    return "INHERIT".equalsIgnoreCase(s.substring(start, wordEnd(s, start)));
  }

  private static boolean opensParen(String s, int from) {
    int k = skipSpaces(s, from);
    return k < s.length() && s.charAt(k) == '(';
  }

  /** Index of the {@code )} matching the {@code (} at {@code open}, or -1 if unbalanced. */
  private static int matchingParen(String s, int open) {
    int depth = 0;
    int i = open;
    while (i < s.length()) {
      int opaque = SqlScan.skipOpaque(s, i);
      if (opaque > i) {
        i = opaque;
        continue;
      }
      char c = s.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')' && --depth == 0) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private static int identEnd(String s, int start) {
    if (start >= s.length()) {
      return start;
    }
    int opaque = SqlScan.skipOpaque(s, start);
    return opaque > start ? Math.min(opaque, s.length()) : wordEnd(s, start);
  }

  private static int skipSpaces(String s, int from) {
    int i = from;
    while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
    return i;
  }

  private static int wordEnd(String s, int from) {
    int i = from;
    while (i < s.length() && isWordChar(s.charAt(i))) {
      i++;
    }
    return i;
  }

  private static boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#' || c == '@';
  }
}
