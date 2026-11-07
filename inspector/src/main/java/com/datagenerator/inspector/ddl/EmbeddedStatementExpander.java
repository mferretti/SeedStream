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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Splits a statement chunk that glues several statements together without a separator into its
 * embedded statements, so each goes through the normal pipeline (#459). Only chunks that start
 * (after leading {@code SET|PROMPT|USE|EXEC} lines) with {@code CREATE SCHEMA}, {@code IF}, {@code
 * BEGIN} or {@code DROP} are expanded, cutting at statement-starting keywords at parenthesis depth
 * 0. A routine ({@code CREATE PROCEDURE|FUNCTION|TRIGGER|...}) ends the expansion: it and
 * everything after it stays one piece, its body is never read. Any other chunk, and any chunk that
 * cannot be split (unbalanced parentheses, a single piece), is returned unchanged. Scans run on a
 * same-length copy with comments, literals and quoted identifiers blanked out; the pieces are taken
 * from the original text.
 */
final class EmbeddedStatementExpander {

  private static final Set<String> DIRECTIVES = Set.of("SET", "PROMPT", "USE", "EXEC");
  private static final Set<String> ENTRY = Set.of("IF", "BEGIN", "DROP");
  private static final Set<String> STATEMENT_START =
      Set.of(
          "CREATE",
          "ALTER",
          "DROP",
          "GRANT",
          "REVOKE",
          "DENY",
          "INSERT",
          "UPDATE",
          "DELETE",
          "SELECT",
          "EXEC",
          "EXECUTE",
          "PRINT",
          "IF",
          "ELSE",
          "BEGIN",
          "END",
          "COMMIT",
          "ROLLBACK");
  private static final Set<String> CREATE_MODIFIERS =
      Set.of("OR", "REPLACE", "ALTER", "EDITIONABLE", "NONEDITIONABLE");
  private static final Set<String> ROUTINES =
      Set.of(
          "PROCEDURE",
          "PROC",
          "FUNCTION",
          "TRIGGER",
          "PACKAGE",
          "EVENT",
          "PUBLICATION",
          "SUBSCRIPTION");
  // Words after which a statement keyword is part of the previous statement, not a new one.
  private static final Set<String> PRIVILEGE_CONTEXT = Set.of("GRANT", "REVOKE", "DENY", ",", ".");
  private static final Set<String> SET_OPERATORS = Set.of("UNION", "ALL", "INTERSECT", "EXCEPT");

  /** The statements embedded in {@code chunk}, or {@code chunk} itself when there is nothing. */
  List<String> expand(String chunk) {
    String code = new String(mask(chunk));
    int start = firstStatementWord(code);
    if (start < 0 || !isExpandable(code, start)) {
      return List.of(chunk);
    }
    List<Integer> cuts = cutPoints(code, start);
    if (cuts == null || cuts.size() < 2) {
      return List.of(chunk);
    }
    List<String> pieces = new ArrayList<>();
    for (int k = 0; k < cuts.size(); k++) {
      int end = k + 1 < cuts.size() ? cuts.get(k + 1) : chunk.length();
      pieces.add(chunk.substring(cuts.get(k), end).strip());
    }
    return pieces;
  }

  /** Same-length copy: comments and literals become spaces, quoted identifiers underscores. */
  private static char[] mask(String s) {
    char[] masked = s.toCharArray();
    int n = s.length();
    int i = 0;
    while (i < n) {
      char c = s.charAt(i);
      int end;
      char fill = ' ';
      if (SqlScan.isLineCommentStart(s, i)) {
        end = SqlScan.skipLineComment(s, i);
      } else if (SqlScan.isBlockCommentStart(s, i)) {
        end = SqlScan.skipBlockComment(s, i);
      } else if (c == '\'') {
        end = SqlScan.skipSingleQuoted(s, i);
      } else if (c == '"' || c == '`' || c == '[') {
        end = SqlScan.skipDelimited(s, i, c == '[' ? ']' : c);
        fill = '_';
      } else if (c == '$' && SqlScan.skipDollarQuoted(s, i) > i) {
        end = SqlScan.skipDollarQuoted(s, i);
      } else {
        i++;
        continue;
      }
      end = Math.min(end, n);
      Arrays.fill(masked, i, end, fill);
      i = end;
    }
    return masked;
  }

  /** Index of the first word after leading directive lines, or -1 when there is none. */
  private static int firstStatementWord(String code) {
    int i = skipWhitespace(code, 0);
    while (DIRECTIVES.contains(wordAt(code, i))) {
      int newline = code.indexOf('\n', i);
      if (newline < 0) {
        return -1;
      }
      i = skipWhitespace(code, newline);
    }
    return wordAt(code, i).isEmpty() ? -1 : i;
  }

  private static boolean isExpandable(String code, int start) {
    String first = wordAt(code, start);
    if (ENTRY.contains(first)) {
      return true;
    }
    return "CREATE".equals(first)
        && "SCHEMA".equals(wordAt(code, skipWhitespace(code, start + first.length())));
  }

  /** Start indexes of the embedded statements, or null when parentheses are unbalanced. */
  private static List<Integer> cutPoints(String code, int start) {
    List<Integer> cuts = new ArrayList<>();
    int depth = 0;
    String previous = "";
    String head = "";
    int i = start;
    while (i < code.length()) {
      char c = code.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
      } else if (isWordChar(c)) {
        String word = wordAt(code, i);
        int end = i + word.length();
        if (depth == 0 && isStatementStart(code, word, end, previous, head)) {
          cuts.add(i);
          head = word;
          if ("CREATE".equals(word) && isRoutine(code, end)) {
            return cuts; // a routine body is never read
          }
        }
        previous = word;
        i = end;
      } else {
        depth += c == '(' ? 1 : c == ')' ? -1 : 0;
        if (depth < 0) {
          return null;
        }
        previous = String.valueOf(c);
        i++;
      }
    }
    return depth == 0 ? cuts : null;
  }

  private static boolean isStatementStart(
      String code, String word, int end, String previous, String head) {
    if (!STATEMENT_START.contains(word) || PRIVILEGE_CONTEXT.contains(previous)) {
      return false;
    }
    return switch (word) {
      case "ALTER" ->
          !"OR".equals(previous)
              && !("ALTER".equals(head)
                  && "COLUMN".equals(wordAt(code, skipWhitespace(code, end))));
      case "DROP" -> !"ALTER".equals(head);
      case "GRANT" -> !"WITH".equals(previous);
      case "DELETE", "UPDATE" -> !"ON".equals(previous);
      case "SELECT" -> !SET_OPERATORS.contains(previous);
      case "IF" -> !isIfExistsClause(code, end);
      default -> true;
    };
  }

  /** {@code IF [NOT] EXISTS name} (DDL clause); T-SQL's {@code IF EXISTS (subquery)} differs. */
  private static boolean isIfExistsClause(String code, int afterIf) {
    int i = skipWhitespace(code, afterIf);
    if ("NOT".equals(wordAt(code, i))) {
      i = skipWhitespace(code, i + 3);
    }
    if (!"EXISTS".equals(wordAt(code, i))) {
      return false;
    }
    int next = skipWhitespace(code, i + 6);
    return next >= code.length() || code.charAt(next) != '(';
  }

  private static boolean isRoutine(String code, int afterCreate) {
    int i = skipWhitespace(code, afterCreate);
    String word = wordAt(code, i);
    while (CREATE_MODIFIERS.contains(word)) {
      i = skipWhitespace(code, i + word.length());
      word = wordAt(code, i);
    }
    return ROUTINES.contains(word);
  }

  private static int skipWhitespace(String code, int from) {
    int i = from;
    while (i < code.length() && Character.isWhitespace(code.charAt(i))) {
      i++;
    }
    return i;
  }

  /** Upper-cased word starting at {@code from}, or empty when none starts there. */
  private static String wordAt(String code, int from) {
    int end = from;
    while (end < code.length() && isWordChar(code.charAt(end))) {
      end++;
    }
    return code.substring(Math.min(from, code.length()), end).toUpperCase(Locale.ROOT);
  }

  private static boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '#' || c == '@';
  }
}
