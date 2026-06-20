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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes a single SQL statement so that JSQLParser 5.x can parse it. Only modifies CREATE TABLE
 * statements; other statements are returned unchanged.
 *
 * <p>Transformations applied (in order):
 *
 * <ol>
 *   <li>Strip leading SQL*Plus/batch directive lines: {@code SET ...}, {@code PROMPT ...}, {@code
 *       USE ...}, {@code EXEC ...}.
 *   <li>Dequote MSSQL bracket identifiers ({@code [name]} → {@code name}) throughout the statement,
 *       and strip schema qualification from the table name ({@code [schema].[table]} → {@code
 *       table} after {@code CREATE TABLE}). JSQLParser 5.3 does not parse bracket-quoted
 *       identifiers in CREATE TABLE context.
 *   <li>Strip {@code IDENTITY(seed,increment)} from column definitions — JSQLParser does not parse
 *       this MSSQL column property.
 *   <li>Truncate trailing table options after the balanced closing {@code )} of the column list:
 *       {@code ENGINE=...}, {@code WITH (...)}, {@code ON [PRIMARY]}, {@code TABLESPACE ...},
 *       {@code STORAGE (...)}, {@code ORGANIZATION ...} etc. A balanced-paren scan handles nested
 *       parens in {@code DEFAULT (0)} and {@code NUMBER(10,2)} correctly.
 *   <li>Strip {@code CLUSTERED} / {@code NONCLUSTERED} keywords (whole-word, case-insensitive) that
 *       appear inside the column list after {@code PRIMARY KEY} or {@code UNIQUE} and break the
 *       parser.
 * </ol>
 */
public class DdlPreprocessor {

  /** The {@code CREATE} keyword, used both as a regex token and a literal scan target. */
  private static final String CREATE = "CREATE";

  /** Matches a leading directive line that must be stripped before CREATE TABLE. */
  private static final Pattern DIRECTIVE_LINE =
      Pattern.compile("(?im)^[ \\t]*(SET|PROMPT|USE|EXEC)\\b[^\\n]*\\n?");

  /**
   * Matches CLUSTERED or NONCLUSTERED as a whole word (case-insensitive). Used to strip these MSSQL
   * keywords from inside the column list.
   */
  private static final Pattern CLUSTERED = Pattern.compile("(?i)\\b(NON)?CLUSTERED\\b");

  /**
   * Matches {@code IDENTITY(seed, increment)} — the MSSQL auto-increment column property.
   * JSQLParser does not recognise this construct and throws a parse exception.
   */
  private static final Pattern IDENTITY =
      Pattern.compile("(?i)\\bIDENTITY\\s*\\(\\s*\\d+\\s*,\\s*\\d+\\s*\\)");

  /** Quick check: does this statement contain CREATE TABLE? */
  private static final Pattern CREATE_TABLE =
      Pattern.compile("(?is)\\bCREATE\\b.{0,50}\\bTABLE\\b");

  /**
   * After CREATE TABLE, matches an optional schema-qualified bracket or plain table name:
   * optionally {@code [schema].} or {@code schema.} prefix, followed by the actual table name.
   * Capture group 1 = the bare table name (without schema or brackets).
   */
  private static final Pattern TABLE_NAME_AFTER_CREATE =
      Pattern.compile(
          "(?i)(CREATE\\s+TABLE\\s+)"
              + "(?:\\[?[^\\[\\].(\\s]++\\]?\\.)*+" // zero or more schema.  segments (possessive)
              + "\\[?([^\\[\\].(\\s]++)\\]?"); // the final table name

  /**
   * Sanitizes one SQL statement for JSQLParser. If the input does not look like a CREATE TABLE
   * statement it is returned unchanged.
   */
  public String sanitize(String statement) {
    if (statement == null) {
      return statement;
    }

    // Strip any leading directive lines that may have slipped through
    String s = DIRECTIVE_LINE.matcher(statement).replaceAll("");

    if (!CREATE_TABLE.matcher(s).find()) {
      return s.isBlank() ? statement : s;
    }

    // Dequote bracket identifiers and strip schema prefix from table name
    s = dequoteBrackets(s);

    // Strip IDENTITY(seed,increment) column property
    s = IDENTITY.matcher(s).replaceAll("");

    // Truncate trailing table options after the balanced closing paren of the column list
    s = truncateTrailingOptions(s);

    // Strip CLUSTERED / NONCLUSTERED keywords inside the column list
    s = CLUSTERED.matcher(s).replaceAll("");

    return s;
  }

  /**
   * Replaces all {@code [identifier]} bracket-quoted tokens with the bare identifier, and removes
   * schema qualification from the table name position (everything up to and including the last
   * {@code .} before the opening paren of the column list).
   *
   * <p>The pass strips brackets from the whole statement text because JSQLParser does not
   * understand them in CREATE TABLE column-list context. Bracket pairs that were already handled as
   * identifiers by the splitter are now fully replaced with their contents here.
   */
  private String dequoteBrackets(String s) {
    return stripSchemaPrefix(stripBrackets(s));
  }

  /**
   * Replaces every {@code [identifier]} bracket-quoted token with the bare identifier (handling the
   * {@code ]]} escape), throughout the statement text.
   */
  private static String stripBrackets(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    int len = s.length();
    int i = 0;
    while (i < len) {
      char c = s.charAt(i);
      if (c == '[') {
        i = appendBracketContent(s, i, sb);
      } else {
        sb.append(c);
        i++;
      }
    }
    return sb.toString();
  }

  /**
   * Appends the bare identifier of a {@code [...]} token opened at {@code i} to {@code sb} and
   * returns the index just past the closing bracket. {@code ]]} is treated as a literal {@code ]}.
   */
  private static int appendBracketContent(String s, int i, StringBuilder sb) {
    int len = s.length();
    int j = i + 1;
    while (j < len) {
      char c = s.charAt(j);
      if (c == ']') {
        j++;
        if (j < len && s.charAt(j) == ']') {
          sb.append(']'); // escaped ]] → literal ]
          j++;
        } else {
          return j;
        }
      } else {
        sb.append(c);
        j++;
      }
    }
    return j;
  }

  /**
   * Strips schema qualification from the table name after {@code CREATE TABLE} (e.g. {@code
   * dbo.customers} → {@code customers}).
   */
  private static String stripSchemaPrefix(String dequoted) {
    Matcher m = TABLE_NAME_AFTER_CREATE.matcher(dequoted);
    if (m.find()) {
      // group(1) = "CREATE TABLE "  group(2) = bare table name
      String replacement = m.group(1) + m.group(2);
      return dequoted.substring(0, m.start()) + replacement + dequoted.substring(m.end());
    }
    return dequoted;
  }

  /**
   * Finds the first {@code (} after {@code CREATE TABLE <name>}, then walks to its balanced closing
   * {@code )}, and drops everything between that {@code )} and the end of the statement (the
   * trailing table options). The scan is quote- and comment-aware so that nested parens inside
   * string literals and type arguments are not miscounted.
   */
  private String truncateTrailingOptions(String s) {
    int createIdx = indexOfCreateTable(s);
    if (createIdx < 0) {
      return s;
    }
    // Start of the column list: first '(' at or after CREATE TABLE.
    int firstParen = s.indexOf('(', createIdx);
    if (firstParen < 0) {
      return s;
    }
    int closingParen = findColumnListClose(s, firstParen);
    if (closingParen < 0) {
      return s;
    }
    // Everything after closingParen (up to an optional trailing ';') is the trailing table options.
    String tail = s.substring(closingParen + 1).trim();
    if (tail.equals(";")) {
      return s.substring(0, closingParen + 1) + ";";
    }
    return s.substring(0, closingParen + 1);
  }

  /**
   * Walks from the opening {@code (} of the column list tracking paren depth (skipping quotes and
   * comments) and returns the index of its balanced closing {@code )}, or -1 if unbalanced.
   */
  private static int findColumnListClose(String s, int firstParen) {
    int len = s.length();
    int depth = 0;
    int i = firstParen;
    while (i < len) {
      char c = s.charAt(i);
      if (SqlScan.isBlockCommentStart(s, i)) {
        i = SqlScan.skipBlockComment(s, i);
      } else if (SqlScan.isLineCommentStart(s, i)) {
        i = SqlScan.skipLineComment(s, i);
      } else if (c == '\'') {
        i = SqlScan.skipSingleQuoted(s, i);
      } else if (c == '"') {
        i = SqlScan.skipDelimited(s, i, '"');
      } else if (c == '`') {
        i = SqlScan.skipDelimited(s, i, '`');
      } else if (c == '(') {
        depth++;
        i++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
        i++;
      } else {
        i++;
      }
    }
    return -1;
  }

  /**
   * Returns the index of the start of the {@code CREATE} keyword of the {@code CREATE TABLE}
   * clause, or -1 if not found.
   */
  private int indexOfCreateTable(String s) {
    String upper = s.toUpperCase(Locale.ROOT);
    int idx = upper.indexOf(CREATE);
    while (idx >= 0) {
      int after = idx + CREATE.length();
      while (after < upper.length() && Character.isWhitespace(upper.charAt(after))) {
        after++;
      }
      if (upper.startsWith("TABLE", after)) {
        return idx;
      }
      idx = upper.indexOf(CREATE, idx + 1);
    }
    return -1;
  }
}
