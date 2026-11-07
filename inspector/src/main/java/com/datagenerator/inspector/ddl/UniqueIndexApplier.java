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

import static com.datagenerator.inspector.ddl.TableModel.unquote;

import com.datagenerator.inspector.Names;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Applies {@code CREATE UNIQUE INDEX} statements to the {@link TableModel}s as if the same columns
 * were declared by an inline {@code UNIQUE (...)}. The name, table and column list are read
 * directly from comment-free text (vendor tails such as Oracle's {@code STORAGE(...)} defeat the
 * SQL parser). A partial ({@code WHERE}) or expression index is not a key on its columns: warned
 * and skipped. A key already declared as PRIMARY KEY or UNIQUE (in any column order) counts once.
 * Nothing here ever aborts the inspection.
 */
final class UniqueIndexApplier {

  private static final Pattern PLAIN_COLUMN =
      Pattern.compile(
          "(?is)(?<column>\"[^\"]*\"|`[^`]*`|\\[[^\\]]*\\]|[\\p{L}\\p{N}_$#@]+)"
              + "(?:\\s+(?:ASC|DESC))?(?:\\s+NULLS\\s+(?:FIRST|LAST))?");
  // Quoted identifiers are consumed (so "where" is not a keyword); group "w" is a real WHERE.
  private static final Pattern WHERE_OR_QUOTED =
      Pattern.compile("(?i)\"[^\"]*\"|`[^`]*`|\\[[^\\]]*\\]|(?<w>\\bWHERE\\b)");

  /** A parsed statement; {@code columns} is empty when the key cannot be a plain column list. */
  private record Parsed(String name, String table, List<String> columns, String skipReason) {}

  /** Whether {@code code} (comment- and literal-free statement text) is a CREATE UNIQUE INDEX. */
  static boolean isUniqueIndex(String code) {
    String statement = DdlPreprocessor.stripLeadingDirectives(code);
    int open = openingParenthesis(statement);
    return afterIndexKeyword(headTokens(open < 0 ? statement : statement.substring(0, open))) >= 0;
  }

  /** Index of the first word after {@code CREATE [OR REPLACE] UNIQUE [CLUSTERED] INDEX}, or -1. */
  private static int afterIndexKeyword(List<String> words) {
    int i = 0;
    if (!isWord(words, i++, "CREATE")) {
      return -1;
    }
    if (isWord(words, i, "OR")) {
      i += 2;
    }
    if (!isWord(words, i++, "UNIQUE")) {
      return -1;
    }
    if (isWord(words, i, "CLUSTERED") || isWord(words, i, "NONCLUSTERED")) {
      i++;
    }
    return isWord(words, i, "INDEX") ? i + 1 : -1;
  }

  private static boolean isWord(List<String> words, int i, String expected) {
    return i < words.size() && expected.equalsIgnoreCase(words.get(i));
  }

  /** Applies every statement to the table of that name in {@code tables} (keyed by snake_case). */
  void apply(List<String> statements, Map<String, TableModel> tables, List<String> warnings) {
    for (String raw : statements) {
      Optional<Parsed> parsed = parse(SqlScan.stripCommentsAndLiterals(raw));
      if (parsed.isEmpty()) {
        warnings.add(
            "could not parse CREATE UNIQUE INDEX statement, ignored: "
                + DdlInspector.shortSnippet(raw));
      } else {
        applyIndex(parsed.get(), tables, warnings);
      }
    }
  }

  private void applyIndex(Parsed index, Map<String, TableModel> tables, List<String> warnings) {
    String label = "UNIQUE INDEX " + (index.name().isEmpty() ? "(unnamed)" : index.name());
    TableModel table = tables.get(Names.toSnakeCase(index.table()));
    if (table == null) {
      warnings.add(label + " ON " + index.table() + ": table is not in the script — ignored");
      return;
    }
    if (index.skipReason() != null) {
      warnings.add(
          table.name + ": " + label + " not applied — " + index.skipReason() + ", not a key");
      return;
    }
    List<String> columns = new ArrayList<>();
    for (String wanted : index.columns()) {
      Optional<String> declared =
          table.columns.stream()
              .map(TableModel.Column::name)
              .filter(name -> name.equalsIgnoreCase(wanted))
              .findFirst();
      if (declared.isEmpty()) {
        warnings.add(
            table.name + ": " + label + " not applied — column " + wanted + " is not in the table");
        return;
      }
      columns.add(declared.get());
    }
    boolean known =
        Stream.concat(table.primary.stream(), table.uniques.stream())
            .anyMatch(key -> sameColumnSet(key, columns));
    if (!known) {
      table.addUnique(columns);
    }
  }

  private static boolean sameColumnSet(List<String> a, List<String> b) {
    return lowerSet(a).equals(lowerSet(b));
  }

  private static Set<String> lowerSet(List<String> names) {
    Set<String> set = new HashSet<>();
    names.forEach(n -> set.add(n.toLowerCase(Locale.ROOT)));
    return set;
  }

  /** Empty when name, table or a balanced column list cannot be read. */
  private static Optional<Parsed> parse(String code) {
    String rest = DdlPreprocessor.stripLeadingDirectives(code);
    int open = openingParenthesis(rest);
    if (open < 0) {
      return Optional.empty();
    }
    List<String> all = headTokens(rest.substring(0, open));
    int first = afterIndexKeyword(all);
    if (first < 0) {
      return Optional.empty();
    }
    List<String> head = all.subList(first, all.size());
    int on = head.indexOf("ON");
    int tableAt =
        on
            + 1
            + (on >= 0 && on + 1 < head.size() && "ONLY".equalsIgnoreCase(head.get(on + 1))
                ? 1
                : 0);
    if (on < 0 || tableAt >= head.size() || !validTail(head.subList(tableAt + 1, head.size()))) {
      return Optional.empty();
    }
    boolean hasName = on > 0 && !"USING".equalsIgnoreCase(head.get(0));
    int close = closingParenthesis(rest, open);
    if (close < 0) {
      return Optional.empty();
    }
    String name = hasName ? lastSegment(head.get(0)) : "";
    String table = lastSegment(head.get(tableAt));
    List<String> columns = new ArrayList<>();
    String reason = null;
    for (String item : splitTopLevel(rest.substring(open + 1, close))) {
      Matcher column = PLAIN_COLUMN.matcher(item.trim());
      if (column.matches()) {
        columns.add(TableModel.keyColumn(column.group("column")));
      } else {
        reason = "it indexes an expression or a column prefix";
      }
    }
    if (hasWhere(rest.substring(close + 1))) {
      reason = "it is a partial index (WHERE)";
    }
    return Optional.of(new Parsed(name, table, columns, reason));
  }

  /** Nothing, or {@code USING method}, may sit between the table and the column list. */
  private static boolean validTail(List<String> words) {
    return words.isEmpty() || (words.size() == 2 && "USING".equalsIgnoreCase(words.get(0)));
  }

  /** Words of the head, minus CONCURRENTLY / IF NOT EXISTS; an unquoted ON is upper-cased. */
  private static List<String> headTokens(String head) {
    List<String> tokens = new ArrayList<>();
    int i = 0;
    while (i < head.length()) {
      if (Character.isWhitespace(head.charAt(i))) {
        i++;
        continue;
      }
      int start = i;
      while (i < head.length() && !Character.isWhitespace(head.charAt(i))) {
        char c = head.charAt(i);
        i =
            c == '"' || c == '`' || SqlScan.opensBracketIdentifier(head, i)
                ? SqlScan.skipDelimited(head, i, c == '[' ? ']' : c)
                : i + 1;
      }
      i = Math.min(i, head.length());
      String word = head.substring(start, i);
      tokens.add("ON".equalsIgnoreCase(word) ? "ON" : word);
    }
    tokens.removeIf(t -> "CONCURRENTLY".equalsIgnoreCase(t));
    for (String word : List.of("IF", "NOT", "EXISTS")) {
      tokens.removeIf(t -> word.equalsIgnoreCase(t));
    }
    return tokens;
  }

  /** Index of the first {@code (} outside a quoted identifier, or -1. */
  private static int openingParenthesis(String s) {
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == '"' || c == '`' || SqlScan.opensBracketIdentifier(s, i)) {
        i = SqlScan.skipDelimited(s, i, c == '[' ? ']' : c);
      } else if (c == '(') {
        return i;
      } else {
        i++;
      }
    }
    return -1;
  }

  private static String lastSegment(String qualified) {
    return unquote(qualified.substring(qualified.lastIndexOf('.') + 1));
  }

  private static boolean hasWhere(String tail) {
    Matcher m = WHERE_OR_QUOTED.matcher(tail);
    while (m.find()) {
      if (m.group("w") != null) {
        return true;
      }
    }
    return false;
  }

  /** Index of the {@code )} matching the {@code (} at {@code open}, or -1. */
  private static int closingParenthesis(String s, int open) {
    int depth = 0;
    int i = open;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == '"' || c == '`' || SqlScan.opensBracketIdentifier(s, i)) {
        i = SqlScan.skipDelimited(s, i, c == '[' ? ']' : c);
        continue;
      }
      if (c == '(') {
        depth++;
      } else if (c == ')' && --depth == 0) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private static List<String> splitTopLevel(String list) {
    List<String> items = new ArrayList<>();
    int depth = 0;
    int start = 0;
    int i = 0;
    while (i < list.length()) {
      char c = list.charAt(i);
      if (c == '"' || c == '`' || SqlScan.opensBracketIdentifier(list, i)) {
        i = SqlScan.skipDelimited(list, i, c == '[' ? ']' : c);
        continue;
      }
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        items.add(list.substring(start, i));
        start = i + 1;
      }
      i++;
    }
    items.add(list.substring(start));
    return items;
  }
}
