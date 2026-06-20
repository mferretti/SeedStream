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

/**
 * Character-state-machine that splits a multi-statement SQL script into individual statement
 * strings. Correctly handles all standard quoting and comment forms so that delimiters ({@code ;},
 * {@code GO}, {@code /}) inside strings or comments never cause a spurious split.
 *
 * <p>Statement boundaries:
 *
 * <ul>
 *   <li>{@code ;} at top level (outside any string, identifier quote, or comment)
 *   <li>A line whose trimmed content is exactly {@code GO} (case-insensitive) — SQL Server batch
 *       separator
 *   <li>A line whose trimmed content is exactly {@code /} — Oracle SQL*Plus terminator; a division
 *       operator inside an expression is never at the top level of a line in isolation
 * </ul>
 *
 * <p>Never splits inside:
 *
 * <ul>
 *   <li>Single-quoted strings {@code '...'} — {@code ''} is an escaped quote, not end-of-string
 *   <li>Double-quoted identifiers {@code "..."}
 *   <li>Backtick identifiers {@code `...`}
 *   <li>Bracket identifiers {@code [...]} — only {@code ]} closes; {@code ]]} is an escaped bracket
 *   <li>Line comments {@code -- ... <eol>}
 *   <li>Block comments {@code /* ... * /}
 *   <li>Dollar-quoted strings {@code $$ ... $$} and tagged {@code $tag$ ... $tag$} (Postgres) —
 *       known limitation: the tag must be a simple identifier (letters/digits/underscore); deeply
 *       nested dollar-quote tags are not supported
 * </ul>
 */
public class SqlStatementSplitter {

  /** Splits {@code sql} into individual statement strings. */
  public List<String> split(String sql) {
    if (sql == null || sql.isBlank()) {
      return List.of();
    }

    List<String> result = new ArrayList<>();
    StringBuilder stmt = new StringBuilder();

    // Track the position in stmt where the current line started.
    // When we detect GO or / at end-of-line, we truncate stmt to this position
    // (stripping the GO/slash line that was already appended character by character).
    int currentLineStart = 0;

    int len = sql.length();
    int i = 0;

    while (i < len) {
      char c = sql.charAt(i);

      // ---- block comment /* ... */ ----
      if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
        stmt.append(c);
        stmt.append(sql.charAt(i + 1));
        i += 2;
        while (i < len) {
          char bc = sql.charAt(i);
          if (bc == '\n') {
            stmt.append(bc);
            i++;
            currentLineStart = stmt.length();
            continue;
          }
          stmt.append(bc);
          if (bc == '*' && i + 1 < len && sql.charAt(i + 1) == '/') {
            stmt.append('/');
            i += 2;
            break;
          }
          i++;
        }
        continue;
      }

      // ---- line comment -- ... <eol> ----
      if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
        stmt.append(c);
        stmt.append(sql.charAt(i + 1));
        i += 2;
        while (i < len) {
          char lc = sql.charAt(i);
          if (lc == '\r') {
            i++;
            continue;
          }
          if (lc == '\n') {
            stmt.append(lc);
            i++;
            currentLineStart = stmt.length();
            break;
          }
          stmt.append(lc);
          i++;
        }
        continue;
      }

      // ---- single-quoted string '...' with '' escape ----
      if (c == '\'') {
        stmt.append(c);
        i++;
        while (i < len) {
          char sc = sql.charAt(i);
          if (sc == '\r') {
            i++;
            continue;
          }
          stmt.append(sc);
          if (sc == '\n') {
            i++;
            currentLineStart = stmt.length();
            continue;
          }
          if (sc == '\'') {
            i++;
            // doubled quote '' → escaped, not end
            if (i < len && sql.charAt(i) == '\'') {
              stmt.append('\'');
              i++;
              continue;
            }
            break;
          }
          i++;
        }
        continue;
      }

      // ---- double-quoted identifier "..." ----
      if (c == '"') {
        stmt.append(c);
        i++;
        while (i < len) {
          char dc = sql.charAt(i);
          if (dc == '\r') {
            i++;
            continue;
          }
          stmt.append(dc);
          if (dc == '\n') {
            i++;
            currentLineStart = stmt.length();
            continue;
          }
          if (dc == '"') {
            i++;
            break;
          }
          i++;
        }
        continue;
      }

      // ---- backtick identifier `...` ----
      if (c == '`') {
        stmt.append(c);
        i++;
        while (i < len) {
          char bc = sql.charAt(i);
          if (bc == '\r') {
            i++;
            continue;
          }
          stmt.append(bc);
          if (bc == '\n') {
            i++;
            currentLineStart = stmt.length();
            continue;
          }
          if (bc == '`') {
            i++;
            break;
          }
          i++;
        }
        continue;
      }

      // ---- bracket identifier [...] with ]] escape (MSSQL) ----
      if (c == '[') {
        stmt.append(c);
        i++;
        while (i < len) {
          char bc = sql.charAt(i);
          if (bc == '\r') {
            i++;
            continue;
          }
          stmt.append(bc);
          if (bc == '\n') {
            i++;
            currentLineStart = stmt.length();
            continue;
          }
          if (bc == ']') {
            i++;
            // ]] is an escaped bracket
            if (i < len && sql.charAt(i) == ']') {
              stmt.append(']');
              i++;
              continue;
            }
            break;
          }
          i++;
        }
        continue;
      }

      // ---- dollar-quoted string $tag$...$tag$ (Postgres) ----
      if (c == '$') {
        // Try to read a dollar-quote tag: $[identifier]$
        int tagEnd = i + 1;
        while (tagEnd < len && sql.charAt(tagEnd) != '$' && sql.charAt(tagEnd) != '\n') {
          char tc = sql.charAt(tagEnd);
          // tag chars: letters, digits, underscore
          if (!Character.isLetterOrDigit(tc) && tc != '_') {
            break;
          }
          tagEnd++;
        }
        if (tagEnd < len && sql.charAt(tagEnd) == '$') {
          // valid dollar-quote opening: i..tagEnd inclusive
          String openTag = sql.substring(i, tagEnd + 1); // e.g. "$$" or "$tag$"
          stmt.append(openTag);
          i = tagEnd + 1;
          // Scan until we find the matching close tag
          while (i < len) {
            if (sql.startsWith(openTag, i)) {
              stmt.append(openTag);
              i += openTag.length();
              break;
            }
            char dc = sql.charAt(i);
            if (dc == '\r') {
              i++;
              continue;
            }
            stmt.append(dc);
            if (dc == '\n') {
              i++;
              currentLineStart = stmt.length();
              continue;
            }
            i++;
          }
          continue;
        }
        // Not a dollar-quote: fall through to regular char handling
      }

      // ---- CRLF: swallow \r ----
      if (c == '\r') {
        i++;
        continue;
      }

      // ---- newline: check if the current line is a GO or / batch terminator ----
      if (c == '\n') {
        // The current line content is stmt.substring(currentLineStart)
        String lineContent = stmt.substring(currentLineStart).trim();

        if (lineContent.equalsIgnoreCase("GO") || lineContent.equals("/")) {
          // Trim back stmt to just before this line (strip the GO/slash)
          // The content up to currentLineStart is the real statement
          String s = stmt.substring(0, currentLineStart).stripTrailing();
          if (!s.isBlank() && !isCommentOnly(s)) {
            result.add(s);
          }
          stmt.setLength(0);
          currentLineStart = 0;
          i++;
          continue;
        }

        stmt.append(c);
        i++;
        currentLineStart = stmt.length();
        continue;
      }

      // ---- semicolon at top level ----
      if (c == ';') {
        // Don't append the semicolon; emit stmt as-is
        String s = stmt.toString().stripTrailing();
        if (!s.isBlank() && !isCommentOnly(s)) {
          result.add(s);
        }
        stmt.setLength(0);
        currentLineStart = 0;
        i++;
        continue;
      }

      stmt.append(c);
      i++;
    }

    // Handle remainder (no trailing delimiter)
    // Check if current line is a standalone GO or /
    String lineContent = stmt.substring(currentLineStart).trim();
    if (lineContent.equalsIgnoreCase("GO") || lineContent.equals("/")) {
      String s = stmt.substring(0, currentLineStart).stripTrailing();
      if (!s.isBlank() && !isCommentOnly(s)) {
        result.add(s);
      }
    } else {
      String s = stmt.toString().strip();
      if (!s.isBlank() && !isCommentOnly(s)) {
        result.add(s);
      }
    }

    return result;
  }

  /**
   * Returns true if the string consists entirely of whitespace and/or SQL comments (-- or /* * /).
   * Used to discard comment-only chunks that are not real statements.
   */
  private boolean isCommentOnly(String s) {
    int i = 0;
    int len = s.length();
    while (i < len) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '-' && i + 1 < len && s.charAt(i + 1) == '-') {
        // skip to end of line
        i += 2;
        while (i < len && s.charAt(i) != '\n') i++;
        continue;
      }
      if (c == '/' && i + 1 < len && s.charAt(i + 1) == '*') {
        i += 2;
        while (i + 1 < len && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
        i += 2;
        continue;
      }
      return false; // found real content
    }
    return true;
  }
}
