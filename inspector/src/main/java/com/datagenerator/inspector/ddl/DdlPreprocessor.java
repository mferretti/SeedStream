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
              + "(?:\\[?[^\\[\\].(\\s]+\\]?\\.)*" // zero or more schema.  segments
              + "\\[?([^\\[\\].(\\s]+)\\]?"); // the final table name

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
    // First, replace all [identifier] with identifier throughout the statement.
    // Handle ]] escaped bracket inside identifiers.
    StringBuilder sb = new StringBuilder(s.length());
    int len = s.length();
    int i = 0;
    while (i < len) {
      char c = s.charAt(i);
      if (c == '[') {
        // Scan to closing ]
        int start = i + 1;
        StringBuilder ident = new StringBuilder();
        i++;
        while (i < len) {
          char bc = s.charAt(i);
          if (bc == ']') {
            i++;
            if (i < len && s.charAt(i) == ']') {
              // escaped ]] → literal ]
              ident.append(']');
              i++;
            } else {
              break;
            }
          } else {
            ident.append(bc);
            i++;
          }
        }
        sb.append(ident);
      } else {
        sb.append(c);
        i++;
      }
    }
    String dequoted = sb.toString();

    // Now strip schema qualification: after CREATE TABLE, remove any "schema." prefixes
    // (e.g. "dbo.customers" → "customers").
    // Pattern: CREATE TABLE <word>.<word>(  →  CREATE TABLE <lastword>(
    // We use a regex that finds the table name region and strips schema parts.
    Matcher m = TABLE_NAME_AFTER_CREATE.matcher(dequoted);
    if (m.find()) {
      // group(1) = "CREATE TABLE "  group(2) = bare table name
      String replacement = m.group(1) + m.group(2);
      dequoted = dequoted.substring(0, m.start()) + replacement + dequoted.substring(m.end());
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

    // Find the start of the column list: first '(' after the table name
    int firstParen = -1;
    int len = s.length();
    for (int i = createIdx; i < len; i++) {
      if (s.charAt(i) == '(') {
        firstParen = i;
        break;
      }
    }
    if (firstParen < 0) {
      return s;
    }

    // Walk from firstParen tracking paren depth (quote-aware)
    int depth = 0;
    int closingParen = -1;
    int i = firstParen;
    while (i < len) {
      char c = s.charAt(i);

      // block comment
      if (c == '/' && i + 1 < len && s.charAt(i + 1) == '*') {
        i += 2;
        while (i + 1 < len && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
        i += 2;
        continue;
      }

      // line comment
      if (c == '-' && i + 1 < len && s.charAt(i + 1) == '-') {
        i += 2;
        while (i < len && s.charAt(i) != '\n') i++;
        continue;
      }

      // single-quoted string with '' escape
      if (c == '\'') {
        i++;
        while (i < len) {
          char sc = s.charAt(i);
          i++;
          if (sc == '\'') {
            if (i < len && s.charAt(i) == '\'') {
              i++; // escaped ''
            } else {
              break;
            }
          }
        }
        continue;
      }

      // double-quoted identifier
      if (c == '"') {
        i++;
        while (i < len && s.charAt(i) != '"') i++;
        i++;
        continue;
      }

      // backtick identifier
      if (c == '`') {
        i++;
        while (i < len && s.charAt(i) != '`') i++;
        i++;
        continue;
      }

      if (c == '(') {
        depth++;
        i++;
        continue;
      }

      if (c == ')') {
        depth--;
        if (depth == 0) {
          closingParen = i;
          break;
        }
        i++;
        continue;
      }

      i++;
    }

    if (closingParen < 0) {
      return s;
    }

    // Everything after closingParen (up to optional trailing ';') is the trailing table options
    String tail = s.substring(closingParen + 1).trim();
    if (tail.equals(";")) {
      return s.substring(0, closingParen + 1) + ";";
    }
    return s.substring(0, closingParen + 1);
  }

  /**
   * Returns the index of the start of the {@code CREATE} keyword of the {@code CREATE TABLE}
   * clause, or -1 if not found.
   */
  private int indexOfCreateTable(String s) {
    String upper = s.toUpperCase(Locale.ROOT);
    int idx = upper.indexOf("CREATE");
    while (idx >= 0) {
      int after = idx + "CREATE".length();
      while (after < upper.length() && Character.isWhitespace(upper.charAt(after))) after++;
      if (upper.startsWith("TABLE", after)) {
        return idx;
      }
      idx = upper.indexOf("CREATE", idx + 1);
    }
    return -1;
  }
}
