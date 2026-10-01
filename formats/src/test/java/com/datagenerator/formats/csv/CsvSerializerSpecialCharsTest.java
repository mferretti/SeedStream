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

package com.datagenerator.formats.csv;

import static org.assertj.core.api.Assertions.*;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvSerializerSpecialCharsTest {

  private final CsvSerializer serializer = new CsvSerializer();

  private static Map<String, Object> row(Object... values) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i++) {
      m.put("c" + i, values[i]);
    }
    return m;
  }

  /** RFC 4180 reading: only '"' is special, no backslash escape. */
  private static List<String[]> parse(String csv) throws Exception {
    CSVParser parser = new CSVParserBuilder().withEscapeChar(CSVParser.NULL_CHARACTER).build();
    try (CSVReader r = new CSVReaderBuilder(new StringReader(csv)).withCSVParser(parser).build()) {
      return r.readAll();
    }
  }

  private String[] roundTrip(Object... values) throws Exception {
    List<String[]> rows = parse(serializer.serialize(row(values)));
    assertThat(rows).hasSize(1);
    return rows.get(0);
  }

  @Test
  void shouldRoundTripQuotesCommasAndNewlinesAsSingleRecord() throws Exception {
    String[] out = roundTrip("say \"hi\", ok", "line1\nline2", "\"\"", ",");

    assertThat(out).containsExactly("say \"hi\", ok", "line1\nline2", "\"\"", ",");
  }

  @Test
  void shouldKeepCrAndCrLfInsideQuotedCellByteExact() {
    // exact-string check: the reader library normalizes CR/CRLF, so don't round-trip these
    assertThat(serializer.serialize(row("x\r\ny", "p\rq", "end")))
        .isEqualTo("\"x\r\ny\",\"p\rq\",\"end\"");
  }

  @Test
  void shouldProduceExactRfc4180Quoting() {
    assertThat(serializer.serialize(row("a,b", "q\"q", "x\ny")))
        .isEqualTo("\"a,b\",\"q\"\"q\",\"x\ny\"");
  }

  @Test
  void shouldPreserveLeadingAndTrailingSpaces() throws Exception {
    assertThat(roundTrip("  lead", "trail  ", " both ", "   "))
        .containsExactly("  lead", "trail  ", " both ", "   ");
  }

  @Test
  void shouldPreserveSpacesInLastColumn() throws Exception {
    // serialize() trims the whole row string; closing quote must protect trailing space
    assertThat(roundTrip("a", "end   ")).containsExactly("a", "end   ");
  }

  @Test
  void shouldRoundTripBackslashesUnchanged() throws Exception {
    assertThat(roundTrip("C:\\temp\\x", "\\\"", "trailing\\"))
        .containsExactly("C:\\temp\\x", "\\\"", "trailing\\");
  }

  @Test
  void shouldRoundTripUnicodeAndEmoji() throws Exception {
    assertThat(roundTrip("caff\u00e8", "\u65e5\u672c\u8a9e", "\ud83d\ude80 rocket", "e\u0301"))
        .containsExactly("caff\u00e8", "\u65e5\u672c\u8a9e", "\ud83d\ude80 rocket", "e\u0301");
  }

  @Test
  void shouldWriteNullAsEmptyStringAndKeepColumnCount() throws Exception {
    String[] out = roundTrip("a", null, "c", null);

    assertThat(out).containsExactly("a", "", "c", "");
  }

  @Test
  void shouldKeepSingleEmptyColumnRowParsable() throws Exception {
    assertThat(roundTrip("")).containsExactly("");
  }

  @Test
  void shouldPreserveBigDecimalScaleAndMagnitude() throws Exception {
    String[] out =
        roundTrip(
            new BigDecimal("0.10"),
            new BigDecimal("12345678901234567890.123456789"),
            new BigDecimal("1E+3"),
            new BigDecimal("-0.000"));

    assertThat(out).containsExactly("0.10", "12345678901234567890.123456789", "1E+3", "0.000");
  }

  @Test
  void shouldNeutralizeFormulaTriggersButNotPlainValuesWithQuotes() throws Exception {
    assertThat(roundTrip("=1+1", "+1", "-1", "@x", "\tx", "ok=1", "'q"))
        .containsExactly("'=1+1", "'+1", "'-1", "'@x", "'\tx", "ok=1", "'q");
  }

  @Test
  void shouldKeepNestedJsonInOneCellWithQuotesIntact() throws Exception {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("k", "v,\"x\"");
    inner.put("n", null);

    String[] out = roundTrip(inner, Arrays.asList("a", null, 1));

    assertThat(out).containsExactly("{\"k\":\"v,\\\"x\\\"\",\"n\":null}", "[\"a\",null,1]");
  }

  @Test
  void shouldQuoteHeaderNamesWithSpecialCharacters() throws Exception {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("a,b", 1);
    m.put("say \"x\"", 2);
    m.put("multi\nline", 3);

    assertThat(parse(serializer.serializeHeader(m)).get(0))
        .containsExactly("a,b", "say \"x\"", "multi\nline");
  }
}
