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
    return new Scanner(sql).scan();
  }

  /**
   * Single-pass cursor over the SQL text. Each {@code consume*} handler inspects the character at
   * the cursor and, if it recognises the construct it owns, appends it to the in-progress
   * statement, advances the cursor past it, and returns {@code true}. Returning {@code false} means
   * "not mine" and the driver loop appends one ordinary character.
   */
  private static final class Scanner {
    private final String sql;
    private final int len;
    private final List<String> result = new ArrayList<>();
    private final StringBuilder stmt = new StringBuilder();

    /**
     * Position in {@code stmt} where the current source line started; used to detect and strip a
     * {@code GO}/{@code /} batch-terminator line.
     */
    private int currentLineStart;

    private int i;

    Scanner(String source) {
      this.sql = source;
      this.len = source.length();
    }

    List<String> scan() {
      while (i < len) {
        if (!consumeToken()) {
          stmt.append(sql.charAt(i));
          i++;
        }
      }
      flushRemainder();
      return result;
    }

    /**
     * Dispatches the character at the cursor to its handler; returns true if one consumed input.
     */
    private boolean consumeToken() {
      switch (sql.charAt(i)) {
        // Constructs that may decline (the char is not actually their opener) report it.
        case '/' -> {
          return consumeBlockComment();
        }
        case '-' -> {
          return consumeLineComment();
        }
        case '$' -> {
          return consumeDollarQuote();
        }
        // Constructs that always own their opening character.
        case '\'' -> consumeQuoted('\'', true);
        case '"' -> consumeQuoted('"', false);
        case '`' -> consumeQuoted('`', false);
        case '[' -> consumeBracket();
        case '\r' -> swallowCarriageReturn();
        case '\n' -> consumeNewline();
        case ';' -> consumeSemicolon();
        default -> {
          return false;
        }
      }
      return true;
    }

    /**
     * {@code /* ... * /} — only when the next char is {@code *}; tracks newlines for line start.
     */
    private boolean consumeBlockComment() {
      if (i + 1 >= len || sql.charAt(i + 1) != '*') {
        return false;
      }
      stmt.append('/').append('*');
      i += 2;
      while (i < len) {
        char c = sql.charAt(i);
        if (c == '*' && i + 1 < len && sql.charAt(i + 1) == '/') {
          stmt.append('*').append('/');
          i += 2;
          return true;
        }
        stmt.append(c);
        i++;
        if (c == '\n') {
          currentLineStart = stmt.length();
        }
      }
      return true;
    }

    /** {@code -- ... <eol>} — only when the next char is {@code -}; the EOL ends the comment. */
    private boolean consumeLineComment() {
      if (i + 1 >= len || sql.charAt(i + 1) != '-') {
        return false;
      }
      stmt.append('-').append('-');
      i += 2;
      while (i < len) {
        char c = sql.charAt(i);
        if (c == '\r') {
          i++;
        } else if (c == '\n') {
          stmt.append(c);
          i++;
          currentLineStart = stmt.length();
          return true;
        } else {
          stmt.append(c);
          i++;
        }
      }
      return true;
    }

    /**
     * A quoted region opened and closed by {@code quote}. When {@code doubledIsEscape} is set, a
     * doubled quote ({@code ''}) is an escaped quote rather than the end of the region. The opening
     * and closing quote characters are both appended verbatim. {@code \r} is dropped; {@code \n}
     * resets the line-start marker.
     */
    private void consumeQuoted(char quote, boolean doubledIsEscape) {
      stmt.append(sql.charAt(i)); // opening quote
      i++;
      while (i < len) {
        char c = sql.charAt(i);
        if (c == '\r') {
          i++;
        } else if (c == '\n') {
          stmt.append(c);
          i++;
          currentLineStart = stmt.length();
        } else if (c == quote) {
          stmt.append(c);
          i++;
          if (doubledIsEscape && i < len && sql.charAt(i) == quote) {
            stmt.append(quote);
            i++;
          } else {
            return;
          }
        } else {
          stmt.append(c);
          i++;
        }
      }
    }

    /** Bracket identifier {@code [...]} with {@code ]]} escape (MSSQL). */
    private void consumeBracket() {
      stmt.append('[');
      i++;
      while (i < len) {
        char c = sql.charAt(i);
        if (c == '\r') {
          i++;
        } else if (c == '\n') {
          stmt.append(c);
          i++;
          currentLineStart = stmt.length();
        } else if (c == ']') {
          stmt.append(c);
          i++;
          if (i < len && sql.charAt(i) == ']') {
            stmt.append(']');
            i++;
          } else {
            return;
          }
        } else {
          stmt.append(c);
          i++;
        }
      }
    }

    /**
     * Dollar-quoted string {@code $tag$ ... $tag$} (Postgres). Returns false (not a dollar quote)
     * if the opening token is not a valid {@code $[identifier]$} so the driver treats {@code $} as
     * an ordinary character.
     */
    private boolean consumeDollarQuote() {
      int tagEnd = i + 1;
      while (tagEnd < len && isTagChar(sql.charAt(tagEnd))) {
        tagEnd++;
      }
      if (tagEnd >= len || sql.charAt(tagEnd) != '$') {
        return false;
      }
      String openTag = sql.substring(i, tagEnd + 1); // e.g. "$$" or "$tag$"
      stmt.append(openTag);
      i = tagEnd + 1;
      scanToDollarClose(openTag);
      return true;
    }

    /** Consumes the body of a dollar-quoted string up to and including the matching close tag. */
    private void scanToDollarClose(String openTag) {
      while (i < len) {
        if (sql.startsWith(openTag, i)) {
          stmt.append(openTag);
          i += openTag.length();
          return;
        }
        char c = sql.charAt(i);
        if (c == '\r') {
          i++;
        } else {
          stmt.append(c);
          i++;
          if (c == '\n') {
            currentLineStart = stmt.length();
          }
        }
      }
    }

    /** Drops a lone {@code \r} so CRLF line endings collapse to {@code \n}. */
    private void swallowCarriageReturn() {
      i++;
    }

    /**
     * End of a source line: emit the statement if the line was a {@code GO}/{@code /} terminator.
     */
    private void consumeNewline() {
      if (isBatchTerminator(currentLine())) {
        emit(stmt.substring(0, currentLineStart).stripTrailing());
        stmt.setLength(0);
        currentLineStart = 0;
      } else {
        stmt.append('\n');
        currentLineStart = stmt.length();
      }
      i++;
    }

    /** Top-level {@code ;} statement boundary; the semicolon itself is not kept. */
    private void consumeSemicolon() {
      emit(stmt.toString().stripTrailing());
      stmt.setLength(0);
      currentLineStart = 0;
      i++;
    }

    /**
     * Emits whatever is left after the last delimiter (a statement with no trailing terminator).
     */
    private void flushRemainder() {
      if (isBatchTerminator(currentLine())) {
        emit(stmt.substring(0, currentLineStart).stripTrailing());
      } else {
        emit(stmt.toString().strip());
      }
    }

    private String currentLine() {
      return stmt.substring(currentLineStart).trim();
    }

    private void emit(String s) {
      if (!s.isBlank() && !isCommentOnly(s)) {
        result.add(s);
      }
    }

    private static boolean isTagChar(char c) {
      return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isBatchTerminator(String line) {
      return "GO".equalsIgnoreCase(line) || "/".equals(line);
    }

    /**
     * Returns true if the string consists entirely of whitespace and/or SQL comments (-- or block).
     * Used to discard comment-only chunks that are not real statements.
     */
    private static boolean isCommentOnly(String s) {
      int i = 0;
      int len = s.length();
      while (i < len) {
        char c = s.charAt(i);
        if (Character.isWhitespace(c)) {
          i++;
        } else if (SqlScan.isLineCommentStart(s, i)) {
          i = SqlScan.skipLineComment(s, i);
        } else if (SqlScan.isBlockCommentStart(s, i)) {
          i = SqlScan.skipBlockComment(s, i);
        } else {
          return false; // found real content
        }
      }
      return true;
    }
  }
}
