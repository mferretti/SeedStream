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

package com.datagenerator.destinations.database;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datagenerator.destinations.DestinationException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Identifier handling of {@link DatabaseDestination} (#446): quoting when needed, {@code
 * quote_identifiers}, schema-qualified tables, and the SQL-injection allowlist.
 *
 * <p>Two observation routes: real H2 (case folding proves quoted vs unquoted, rows are read back)
 * and a mocked {@link DataSource} that captures the exact SQL text and the DatabaseMetaData reads.
 *
 * <p>H2 2.5.252 reports {@code getIdentifierQuoteString() = "} and {@code getSQLKeywords() =
 * CURRENT_CATALOG,CURRENT_SCHEMA,GROUPS,IF,ILIKE,KEY,LIMIT,MINUS,OFFSET,QUALIFY,REGEXP,ROWNUM,TOP,_ROWID_}
 * (the words beyond SQL:2003, so {@code ORDER}/{@code USER}/{@code GROUP} are NOT in it).
 */
@SuppressWarnings("java:S2068") // H2 in-memory test DB — empty password is the default
@SuppressFBWarnings(
    value = {
      "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
      "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING"
    },
    justification = "test code: DDL and read-back SQL built from test constants")
class DatabaseDestinationIdentifierTest {

  private static final String H2_URL = "jdbc:h2:mem:identtest;DB_CLOSE_DELAY=-1";
  private static final String PG_URL = "jdbc:postgresql://irrelevant/db";
  private static final String H2_KEYWORDS =
      "CURRENT_CATALOG,CURRENT_SCHEMA,GROUPS,IF,ILIKE,KEY,LIMIT,MINUS,OFFSET,QUALIFY,REGEXP,"
          + "ROWNUM,TOP,_ROWID_";
  private static final String DQ = "\"";
  private static final String BT = "`";
  private static final String ILLEGAL = "illegal characters";
  private static final String ID = "id";

  private Connection observer;

  @BeforeEach
  @SuppressWarnings("java:S2115")
  void setUp() throws SQLException {
    observer = DriverManager.getConnection(H2_URL, "sa", ""); // nosemgrep
    observer.setAutoCommit(true);
    h2("DROP ALL OBJECTS");
  }

  @AfterEach
  void tearDown() throws SQLException {
    h2("DROP ALL OBJECTS");
    observer.close();
  }

  // ---------------------------------------------------------------- helpers

  private void h2(String... statements) throws SQLException {
    try (Statement st = observer.createStatement()) {
      for (String sql : statements) {
        st.execute(sql);
      }
    }
  }

  private String scalar(String sql) throws SQLException {
    try (Statement st = observer.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      assertThat(rs.next()).isTrue();
      return rs.getString(1);
    }
  }

  private static String q(String name) {
    return DQ + name + DQ;
  }

  private static DatabaseDestinationConfig config(String url, String table, boolean quoteAll) {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(url)
        .username("sa")
        .password("")
        .tableName(table)
        .transactionStrategy("auto_commit")
        .quoteIdentifiers(quoteAll)
        .build();
  }

  private static DatabaseDestinationConfig truncateConfig(
      String table, boolean quoteAll, boolean restart) {
    return DatabaseDestinationConfig.builder()
        .jdbcUrl(PG_URL)
        .username("u")
        .password("p")
        .tableName(table)
        .transactionStrategy("auto_commit")
        .quoteIdentifiers(quoteAll)
        .truncateBeforeInsert(true)
        .restartIdentity(restart)
        .build();
  }

  /** Alternating key, value pairs; keys may be null (used by the injection matrix). */
  private static Map<String, Object> row(Object... keyValues) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      m.put((String) keyValues[i], keyValues[i + 1]);
    }
    return m;
  }

  @SafeVarargs
  private static void writeAll(
      DatabaseDestinationConfig cfg, DataSource ds, Map<String, Object>... rows) {
    try (DatabaseDestination dest = new DatabaseDestination(cfg, ds)) {
      dest.open();
      for (Map<String, Object> r : rows) {
        dest.write(r);
      }
      dest.flush();
    }
  }

  @SafeVarargs
  private static void writeH2(DatabaseDestinationConfig cfg, Map<String, Object>... rows) {
    try (DatabaseDestination dest = new DatabaseDestination(cfg)) {
      dest.open();
      for (Map<String, Object> r : rows) {
        dest.write(r);
      }
      dest.flush();
    }
  }

  /** Mocked JDBC stack that records every SQL text handed to the driver. */
  private static final class Jdbc {
    final List<String> prepared = new ArrayList<>();
    final List<String> executed = new ArrayList<>();
    final DatabaseMetaData meta = mock(DatabaseMetaData.class);
    final DataSource ds = mock(DataSource.class);

    Jdbc(String quote, String keywords) throws SQLException {
      Connection conn = mock(Connection.class);
      Statement st = mock(Statement.class);
      when(ds.getConnection()).thenReturn(conn);
      when(conn.getMetaData()).thenReturn(meta);
      when(meta.getIdentifierQuoteString()).thenReturn(quote);
      when(meta.getSQLKeywords()).thenReturn(keywords);
      when(conn.createStatement()).thenReturn(st);
      when(conn.prepareStatement(anyString()))
          .thenAnswer(
              inv -> {
                prepared.add(inv.getArgument(0));
                return mock(PreparedStatement.class);
              });
      when(st.executeUpdate(anyString()))
          .thenAnswer(
              inv -> {
                executed.add(inv.getArgument(0));
                return 0;
              });
      when(st.execute(anyString()))
          .thenAnswer(
              inv -> {
                executed.add(inv.getArgument(0));
                return false;
              });
    }

    static Jdbc h2Like() throws SQLException {
      return new Jdbc(DQ, H2_KEYWORDS);
    }
  }

  // ======================================================= Rule 1: byte-identical SQL (guards)

  @ParameterizedTest
  @ValueSource(strings = {"orders", "Orders", "ORDERS", "_t", "t1$", "order_items"})
  void guardShouldEmitPlainFlatInsertVerbatimWhenNameIsPlain(String table) throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(config(PG_URL, table, false), jdbc.ds, row(ID, 1, "user_id", 2, "Name", "n"));

    assertThat(jdbc.prepared)
        .containsExactly("INSERT INTO " + table + " (id, user_id, Name) VALUES (?,?,?)");
    assertThat(jdbc.executed).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {DQ, BT, " ", ""})
  void guardShouldEmitNoQuoteCharacterForPlainNamesWhateverTheVendorQuote(String quote)
      throws Exception {
    Jdbc jdbc = new Jdbc(quote, "");

    writeAll(config(PG_URL, "orders", false), jdbc.ds, row(ID, 1, "total", 2));

    assertThat(jdbc.prepared).containsExactly("INSERT INTO orders (id, total) VALUES (?,?)");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"orders", "ordering", "user_id", "usernames", "keys", "selection", "tablet"})
  void guardShouldNotQuoteNamesThatMerelyContainAReservedWord(String name) throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(config(PG_URL, name, false), jdbc.ds, row(name, 1, ID, 2));

    assertThat(jdbc.prepared)
        .containsExactly("INSERT INTO " + name + " (" + name + ", id) VALUES (?,?)");
  }

  @Test
  void guardShouldEmitPlainTruncateVerbatimWhenNameIsPlain() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(truncateConfig("Orders", false, false), jdbc.ds, row(ID, 1));

    assertThat(jdbc.executed).containsExactly("TRUNCATE TABLE Orders CASCADE");
    assertThat(jdbc.prepared).containsExactly("INSERT INTO Orders (id) VALUES (?)");
  }

  @Test
  void guardShouldEmitPlainTruncateRestartIdentityVerbatimWhenNameIsPlain() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(truncateConfig("orders", false, true), jdbc.ds, row(ID, 1));

    assertThat(jdbc.executed).containsExactly("TRUNCATE TABLE orders RESTART IDENTITY CASCADE");
  }

  @Test
  void guardShouldEmitPlainNestedInsertsVerbatimWhenNamesArePlain() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(
        config(PG_URL, "orders", false), jdbc.ds, row(ID, 1, "lines", List.of(row("sku", "a"))));

    assertThat(jdbc.prepared)
        .containsExactly(
            "INSERT INTO orders (id) VALUES (?)",
            "INSERT INTO lines (sku, orders_id) VALUES (?,?)");
  }

  @Test
  void guardShouldPrepareOneStatementPerTableNotPerRecord() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(config(PG_URL, "orders", false), jdbc.ds, row(ID, 1), row(ID, 2), row(ID, 3));

    assertThat(jdbc.prepared).hasSize(1);
  }

  @Test
  void guardShouldFindUpperCaseTableWhenMixedCaseNameIsUnquoted() throws Exception {
    h2("CREATE TABLE ORDERS (ID INT, USER_ID INT)");

    writeH2(config(H2_URL, "Orders", false), row(ID, 1, "User_Id", 2));

    assertThat(scalar("SELECT USER_ID FROM ORDERS")).isEqualTo("2");
  }

  @Test
  void guardShouldNotFindMixedCaseTableWhenQuoteIdentifiersIsOffByDefault() throws Exception {
    h2("CREATE TABLE " + q("Orders") + " (ID INT)");

    assertThatThrownBy(() -> writeH2(config(H2_URL, "Orders", false), row(ID, 1)))
        .isInstanceOf(DestinationException.class);
    assertThat(scalar("SELECT COUNT(*) FROM " + q("Orders"))).isEqualTo("0");
  }

  // ======================================================= Rule 1: reserved words

  @ParameterizedTest
  @ValueSource(
      strings = {"order", "ORDER", "Order", "group", "user", "select", "table", "key", "limit"})
  void shouldQuoteReservedWordTableAndColumnVerbatimWhenWritingToH2(String word) throws Exception {
    h2("CREATE TABLE " + q(word) + " (id INT, " + q(word) + " INT)");

    writeH2(config(H2_URL, word, false), row(ID, 1, word, 2));

    assertThat(scalar("SELECT " + q(word) + " FROM " + q(word))).isEqualTo("2");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"order", "ORDER", "Order", "group", "user", "select", "table", "from", "where"})
  void shouldQuoteFixedReservedWordsEvenWhenVendorListIsEmpty(String word) throws Exception {
    Jdbc jdbc = new Jdbc(DQ, "");

    writeAll(config(PG_URL, word, false), jdbc.ds, row(word, 1, ID, 2));

    assertThat(jdbc.prepared)
        .containsExactly("INSERT INTO " + q(word) + " (" + q(word) + ", id) VALUES (?,?)");
  }

  @Test
  void shouldQuoteWordsFromTheVendorKeywordListCaseInsensitively() throws Exception {
    Jdbc jdbc = new Jdbc(DQ, "FOO,BAR");

    writeAll(config(PG_URL, "foo", false), jdbc.ds, row("Bar", 1, "baz", 2));

    assertThat(jdbc.prepared).containsExactly("INSERT INTO \"foo\" (\"Bar\", baz) VALUES (?,?)");
  }

  @Test
  void guardShouldNotQuoteVendorKeywordFromAnotherVendor() throws Exception {
    // Reserved-word check uses THIS driver's list: KEY is plain for a driver that does not list it.
    Jdbc jdbc = new Jdbc(DQ, "FOO");

    writeAll(config(PG_URL, "items", false), jdbc.ds, row("keyspace", 1));

    assertThat(jdbc.prepared).containsExactly("INSERT INTO items (keyspace) VALUES (?)");
  }

  @Test
  void shouldUseBackticksForReservedAndSpacedNamesWhenVendorQuoteIsBacktick() throws Exception {
    Jdbc jdbc = new Jdbc(BT, "");

    writeAll(
        config("jdbc:mysql://irrelevant/db", "order", false),
        jdbc.ds,
        row("group", 1, "Unit Price", 2, ID, 3));

    assertThat(jdbc.prepared)
        .containsExactly("INSERT INTO `order` (`group`, `Unit Price`, id) VALUES (?,?,?)");
  }

  // ======================================================= Rule 1: allowed non-plain characters

  @ParameterizedTest
  @ValueSource(strings = {"Order Details", "2fa", "città", "a-b", "a#b", "a@b", "x٣", "日本語", "Ünï"})
  void shouldQuoteTableAndColumnVerbatimWhenNameHasAllowedSpecialCharacters(String name)
      throws Exception {
    h2("CREATE TABLE " + q(name) + " (id INT, " + q(name) + " INT)");

    writeH2(config(H2_URL, name, false), row(ID, 1, name, 7));

    assertThat(scalar("SELECT " + q(name) + " FROM " + q(name))).isEqualTo("7");
  }

  @Test
  void shouldEmitQuotedSpacedTableAndColumnExactly() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(
        config(PG_URL, "Order Details", false),
        jdbc.ds,
        row("Unit Price", 1, "2fa", 2, "città", 3));

    assertThat(jdbc.prepared)
        .containsExactly(
            "INSERT INTO \"Order Details\" (\"Unit Price\", \"2fa\", \"città\") VALUES (?,?,?)");
  }

  @ParameterizedTest
  @ValueSource(strings = {"my--table", "a -- b", "t--"})
  void shouldQuoteNameContainingDoubleDashSoTheStatementIsNotCut(String name) throws Exception {
    h2("CREATE TABLE " + q(name) + " (id INT, " + q(name + "c") + " INT)");

    writeH2(config(H2_URL, name, false), row(ID, 1, name + "c", 5));

    assertThat(scalar("SELECT " + q(name + "c") + " FROM " + q(name))).isEqualTo("5");
  }

  @Test
  void shouldEmitDoubleDashNameInsideQuotesExactly() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(config(PG_URL, "a--b", false), jdbc.ds, row("c--d", 1));

    assertThat(jdbc.prepared).containsExactly("INSERT INTO \"a--b\" (\"c--d\") VALUES (?)");
  }

  // ======================================================= quote_identifiers

  @Test
  void shouldQuoteEveryIdentifierWhenQuoteIdentifiersIsTrueOnH2() throws Exception {
    h2("CREATE TABLE " + q("Orders") + " (" + q(ID) + " INT, " + q("Name") + " VARCHAR(20))");

    writeH2(config(H2_URL, "Orders", true), row(ID, 1, "Name", "n"));

    assertThat(scalar("SELECT " + q("Name") + " FROM " + q("Orders"))).isEqualTo("n");
  }

  @Test
  void shouldEmitEveryIdentifierQuotedWhenQuoteIdentifiersIsTrue() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(config(PG_URL, "orders", true), jdbc.ds, row(ID, 1, "Name", 2));

    assertThat(jdbc.prepared)
        .containsExactly("INSERT INTO \"orders\" (\"id\", \"Name\") VALUES (?,?)");
  }

  @Test
  void shouldQuoteWithBacktickWhenQuoteIdentifiersIsTrueOnMySql() throws Exception {
    Jdbc jdbc = new Jdbc(BT, "");

    writeAll(config("jdbc:mysql://irrelevant/db", "orders", true), jdbc.ds, row(ID, 1));

    assertThat(jdbc.prepared).containsExactly("INSERT INTO `orders` (`id`) VALUES (?)");
  }

  @Test
  void shouldQuoteEachPartOfQualifiedNameWhenQuoteIdentifiersIsTrueOnH2() throws Exception {
    h2(
        "CREATE SCHEMA " + q("NORTHWIND"),
        "CREATE TABLE " + q("NORTHWIND") + "." + q("ORDERS") + " (" + q(ID) + " INT)");

    writeH2(config(H2_URL, "NORTHWIND.ORDERS", true), row(ID, 9));

    assertThat(scalar("SELECT " + q(ID) + " FROM " + q("NORTHWIND") + "." + q("ORDERS")))
        .isEqualTo("9");
  }

  @Test
  void shouldEmitQuotedTruncateWhenQuoteIdentifiersIsTrue() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(truncateConfig("NORTHWIND.ORDERS", true, true), jdbc.ds, row(ID, 1));

    assertThat(jdbc.executed)
        .containsExactly("TRUNCATE TABLE \"NORTHWIND\".\"ORDERS\" RESTART IDENTITY CASCADE");
    assertThat(jdbc.prepared)
        .containsExactly("INSERT INTO \"NORTHWIND\".\"ORDERS\" (\"id\") VALUES (?)");
  }

  @Test
  void shouldQuoteNestedChildTablesAndColumnsWhenQuoteIdentifiersIsTrue() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(
        config(PG_URL, "orders", true), jdbc.ds, row(ID, 1, "lines", List.of(row("Sku", "a"))));

    assertThat(jdbc.prepared)
        .containsExactly(
            "INSERT INTO \"orders\" (\"id\") VALUES (?)",
            "INSERT INTO \"lines\" (\"Sku\", \"orders_id\") VALUES (?,?)");
  }

  // ======================================================= Rule 3: schema qualification

  static Stream<Arguments> qualifiedNames() {
    return Stream.of(
        Arguments.of("public.users", "public.users"),
        Arguments.of("dbo.Order Details", "dbo.\"Order Details\""),
        Arguments.of("public.order", "public.\"order\""),
        Arguments.of("Sales.Order", "Sales.\"Order\""),
        Arguments.of("a.b.c", "a.b.c"),
        Arguments.of("my schema.t", "\"my schema\".t"),
        Arguments.of("db.group.t", "db.\"group\".t"),
        Arguments.of("cat.dbo.Order Details", "cat.dbo.\"Order Details\""));
  }

  @ParameterizedTest
  @MethodSource("qualifiedNames")
  void shouldQuoteEachPartOfQualifiedTableNameOnlyWhenNeeded(String table, String expected)
      throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(config(PG_URL, table, false), jdbc.ds, row(ID, 1));

    assertThat(jdbc.prepared).containsExactly("INSERT INTO " + expected + " (id) VALUES (?)");
  }

  @Test
  void shouldQuoteEachPartOfQualifiedNameInTruncate() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(truncateConfig("dbo.Order Details", false, false), jdbc.ds, row(ID, 1));

    assertThat(jdbc.executed).containsExactly("TRUNCATE TABLE dbo.\"Order Details\" CASCADE");
  }

  @Test
  void shouldWriteToSchemaQualifiedReservedTableOnH2() throws Exception {
    h2("CREATE TABLE PUBLIC." + q("order") + " (id INT)");

    writeH2(config(H2_URL, "public.order", false), row(ID, 4));

    assertThat(scalar("SELECT id FROM PUBLIC." + q("order"))).isEqualTo("4");
  }

  @Test
  void shouldWriteToSchemaQualifiedPlainTableOnH2() throws Exception {
    h2("CREATE TABLE users (id INT)");

    writeH2(config(H2_URL, "public.users", false), row(ID, 4));

    assertThat(scalar("SELECT id FROM users")).isEqualTo("4");
  }

  @Test
  void shouldWriteToCatalogSchemaTableOnH2() throws Exception {
    h2("CREATE TABLE users (id INT)");

    writeH2(config(H2_URL, "identtest.public.users", false), row(ID, 5));

    assertThat(scalar("SELECT id FROM users")).isEqualTo("5");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a..b", ".a", "a.", "a.b.c.d", "a.b.c.d.e", ".", "..", "a.b."})
  void guardShouldRejectMalformedQualifiedNames(String table) throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    assertThatThrownBy(() -> writeAll(config(PG_URL, table, false), jdbc.ds, row(ID, 1)))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining(table);
    assertThat(jdbc.prepared).isEmpty();
    assertThat(jdbc.executed).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void guardShouldRejectDottedColumnName(boolean quoteAll) throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    assertThatThrownBy(() -> writeAll(config(PG_URL, "orders", quoteAll), jdbc.ds, row("a.b", 1)))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining(ILLEGAL)
        .hasMessageContaining("a.b");
    assertThat(jdbc.prepared).isEmpty();
  }

  // ======================================================= nested + truncate paths

  @Test
  void shouldQuoteReservedAndSpacedChildTablesAndColumnsInNestedInserts() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(
        config(PG_URL, "user", false),
        jdbc.ds,
        row(
            ID,
            1,
            "order",
            List.of(row("group", 3)),
            "Order Details",
            List.of(row("Unit Price", 4))));

    assertThat(jdbc.prepared)
        .containsExactly(
            "INSERT INTO \"user\" (id) VALUES (?)",
            "INSERT INTO \"order\" (\"group\", user_id) VALUES (?,?)",
            "INSERT INTO \"Order Details\" (\"Unit Price\", user_id) VALUES (?,?)");
  }

  @Test
  void shouldWriteNestedChildWithReservedTableAndColumnToH2() throws Exception {
    h2(
        "CREATE TABLE orders (id INT)",
        "CREATE TABLE " + q("order") + " (n INT, " + q("group") + " INT, orders_id INT)");

    writeH2(config(H2_URL, "orders", false), row(ID, 1, "order", List.of(row("n", 5, "group", 7))));

    assertThat(scalar("SELECT " + q("group") + " FROM " + q("order"))).isEqualTo("7");
    assertThat(scalar("SELECT orders_id FROM " + q("order"))).isEqualTo("1");
  }

  @Test
  void shouldQuoteReservedTableInTruncateAndRestartIdentity() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(truncateConfig("order", false, true), jdbc.ds, row(ID, 1));

    assertThat(jdbc.executed).containsExactly("TRUNCATE TABLE \"order\" RESTART IDENTITY CASCADE");
  }

  @Test
  void shouldQuoteSpacedTableInTruncate() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(truncateConfig("Order Details", false, false), jdbc.ds, row(ID, 1));

    assertThat(jdbc.executed).containsExactly("TRUNCATE TABLE \"Order Details\" CASCADE");
  }

  @Test
  void shouldQuoteReservedNestedTablesInTruncateBeforeTheirFirstInsert() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(
        truncateConfig("user", false, false), jdbc.ds, row(ID, 1, "order", List.of(row("n", 1))));

    assertThat(jdbc.executed)
        .containsExactly("TRUNCATE TABLE \"user\" CASCADE", "TRUNCATE TABLE \"order\" CASCADE");
  }

  // ======================================================= quote string handling

  @Test
  void shouldReadTheVendorQuoteStringOnceAtOpen() throws Exception {
    Jdbc jdbc = Jdbc.h2Like();

    writeAll(
        config(PG_URL, "orders", false),
        jdbc.ds,
        row(ID, 1, "a", List.of(row("n", 1)), "b", List.of(row("n", 2))),
        row(ID, 2, "a", List.of(row("n", 3)), "b", List.of(row("n", 4))));

    verify(jdbc.meta, times(1)).getIdentifierQuoteString();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void shouldFailClearlyWhenDriverReportsNoQuoteAndNameNeedsQuoting(String quote) throws Exception {
    Jdbc jdbc = new Jdbc(quote, "");

    assertThatThrownBy(() -> writeAll(config(PG_URL, "Order Details", false), jdbc.ds, row(ID, 1)))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("Order Details")
        .hasMessageMatching("(?is).*quot.*");
    assertThat(jdbc.prepared).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void shouldFailClearlyWhenDriverReportsNoQuoteAndNameIsReserved(String quote) throws Exception {
    Jdbc jdbc = new Jdbc(quote, "");

    assertThatThrownBy(() -> writeAll(config(PG_URL, "orders", false), jdbc.ds, row("order", 1)))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("order")
        .hasMessageMatching("(?is).*quot.*");
    assertThat(jdbc.prepared).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void shouldFailClearlyWhenDriverReportsNoQuoteAndQuoteIdentifiersIsTrue(String quote)
      throws Exception {
    Jdbc jdbc = new Jdbc(quote, "");

    assertThatThrownBy(() -> writeAll(config(PG_URL, "orders", true), jdbc.ds, row(ID, 1)))
        .isInstanceOf(DestinationException.class)
        .hasMessageMatching("(?is).*quot.*");
    assertThat(jdbc.prepared).isEmpty();
  }

  // ======================================================= Rule 2: injection allowlist

  /** Label + value; labels keep control characters out of test report names. */
  private static final Object[][] BAD_NAMES = { {"semicolon-drop", "x; DROP TABLE t"}, {"quote-semicolon-comment", "x\"; DROP TABLE t; --"}, {"quote-or-quote", "x\" OR \"1\"=\"1"}, {"backtick", "x`y"}, {"apostrophe", "x'y"}, {"right-bracket", "x]y"}, {"brackets", "[x]"}, {"backslash", "x\\y"}, {"newline", "x\ny"}, {"carriage-return", "x\ry"}, {"tab", "x\ty"}, {"nul", "x\0y"}, {"leading-nul", "\0x"}, {"next-line-0085", "x\u0085y"}, {"comment-open", "x/*y"}, {"parens", "x(y)"}, {"comma", "x,y"}, {"equals", "x=y"}, {"star", "x*y"}, {"leading-space", " x"}, {"trailing-space", "x "}, {"only-space", " "}, {"fullwidth-quote", "x＂y"}, {"fullwidth-semicolon", "x；y"}, {"colon", "x:y"}, {"bang", "x!y"}, {"question", "x?y"}, {"percent", "x%y"}, {"less-than", "x<y"}, {"greater-than", "x>y"}, {"pipe", "x|y"}, {"plus", "x+y"}, {"caret", "x^y"}, {"tilde", "x~y"}, {"brace", "x{y"}, {"ampersand", "x&y"}, {"empty", ""}, {"null", null},
  };

  private enum Pos {
    TABLE,
    COLUMN,
    QUAL2_FIRST,
    QUAL2_LAST,
    QUAL3_FIRST,
    QUAL3_MIDDLE,
    QUAL3_LAST,
    TRUNCATE_TABLE,
    NESTED_CHILD_TABLE,
    NESTED_CHILD_COLUMN
  }

  static Stream<Arguments> injectionMatrix() {
    List<Arguments> all = new ArrayList<>();
    for (Pos pos : Pos.values()) {
      for (boolean quoteAll : new boolean[] {false, true}) {
        for (Object[] bad : BAD_NAMES) {
          boolean isNull = bad[1] == null;
          boolean qualified = pos.name().startsWith("QUAL");
          if (isNull && qualified) {
            continue; // a null part cannot occur inside a string
          }
          all.add(Arguments.of(pos, quoteAll, bad[0], bad[1]));
        }
      }
    }
    return all.stream();
  }

  @ParameterizedTest(name = "{0} quoteIdentifiers={1} {2}")
  @MethodSource("injectionMatrix")
  void guardShouldRejectIllegalIdentifierBeforeAnySqlRuns(
      Pos pos, boolean quoteAll, String label, String bad) throws Exception {
    Jdbc jdbc = Jdbc.h2Like();
    // The valid parent may already be prepared (plain today, quoted once quote_identifiers works).
    List<String> parentInserts =
        List.of("INSERT INTO orders (id) VALUES (?)", "INSERT INTO \"orders\" (\"id\") VALUES (?)");
    boolean nested = pos == Pos.NESTED_CHILD_TABLE || pos == Pos.NESTED_CHILD_COLUMN;

    Throwable thrown = catchThrowable(() -> runWithBadName(jdbc, pos, quoteAll, bad));

    assertThat(thrown).isInstanceOf(DestinationException.class);
    if (bad != null && !bad.isEmpty()) {
      assertThat(thrown.getMessage()).contains(ILLEGAL).contains(bad);
    }
    assertThat(jdbc.executed).isEmpty();
    if (nested) {
      // the (valid) parent may already be prepared; nothing mentioning the bad child may be
      assertThat(jdbc.prepared).hasSizeLessThanOrEqualTo(1).allMatch(parentInserts::contains);
    } else {
      assertThat(jdbc.prepared).isEmpty();
    }
  }

  private static void runWithBadName(Jdbc jdbc, Pos pos, boolean quoteAll, String bad) {
    switch (pos) {
      case TABLE -> writeAll(config(PG_URL, bad, quoteAll), jdbc.ds, row(ID, 1));
      case COLUMN -> writeAll(config(PG_URL, "orders", quoteAll), jdbc.ds, row(bad, 1));
      case QUAL2_FIRST -> writeAll(config(PG_URL, bad + ".t", quoteAll), jdbc.ds, row(ID, 1));
      case QUAL2_LAST -> writeAll(config(PG_URL, "s." + bad, quoteAll), jdbc.ds, row(ID, 1));
      case QUAL3_FIRST -> writeAll(config(PG_URL, bad + ".s.t", quoteAll), jdbc.ds, row(ID, 1));
      case QUAL3_MIDDLE ->
          writeAll(config(PG_URL, "c." + bad + ".t", quoteAll), jdbc.ds, row(ID, 1));
      case QUAL3_LAST -> writeAll(config(PG_URL, "c.s." + bad, quoteAll), jdbc.ds, row(ID, 1));
      case TRUNCATE_TABLE -> writeAll(truncateConfig(bad, quoteAll, false), jdbc.ds, row(ID, 1));
      case NESTED_CHILD_TABLE ->
          writeAll(
              config(PG_URL, "orders", quoteAll), jdbc.ds, row(ID, 1, bad, List.of(row("n", 1))));
      case NESTED_CHILD_COLUMN ->
          writeAll(
              config(PG_URL, "orders", quoteAll),
              jdbc.ds,
              row(ID, 1, "lines", List.of(row(bad, 1))));
    }
  }

  static Stream<Arguments> injectionOnH2() {
    List<Arguments> all = new ArrayList<>();
    for (boolean quoteAll : new boolean[] {false, true}) {
      for (Object[] bad : BAD_NAMES) {
        all.add(Arguments.of(quoteAll, bad[0], bad[1]));
      }
    }
    return all.stream();
  }

  @ParameterizedTest(name = "quoteIdentifiers={0} {1}")
  @MethodSource("injectionOnH2")
  void guardShouldLeaveTheTableIntactWhenTableNameIsAnInjectionAttempt(
      boolean quoteAll, String label, String bad) throws Exception {
    h2("CREATE TABLE victim (id INT)", "INSERT INTO victim VALUES (1)");

    assertThatThrownBy(() -> writeH2(config(H2_URL, bad, quoteAll), row(ID, 2)))
        .isInstanceOf(DestinationException.class);

    assertThat(scalar("SELECT COUNT(*) FROM victim")).isEqualTo("1");
  }

  @ParameterizedTest(name = "quoteIdentifiers={0} {1}")
  @MethodSource("injectionOnH2")
  void guardShouldLeaveTheTableIntactWhenColumnNameIsAnInjectionAttempt(
      boolean quoteAll, String label, String bad) throws Exception {
    h2("CREATE TABLE victim (id INT)", "INSERT INTO victim VALUES (1)");

    assertThatThrownBy(() -> writeH2(config(H2_URL, "victim", quoteAll), row(bad, 2)))
        .isInstanceOf(DestinationException.class);

    assertThat(scalar("SELECT COUNT(*) FROM victim")).isEqualTo("1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"x`y", "x\"y", "`x`", "\"x\""})
  void guardShouldRejectBothQuoteCharactersWhateverTheVendorQuoteIs(String bad) throws Exception {
    for (String quote : new String[] {DQ, BT}) {
      Jdbc jdbc = new Jdbc(quote, "");

      assertThatThrownBy(() -> writeAll(config(PG_URL, bad, true), jdbc.ds, row(ID, 1)))
          .isInstanceOf(DestinationException.class)
          .hasMessageContaining(ILLEGAL);
      assertThatThrownBy(() -> writeAll(config(PG_URL, "orders", true), jdbc.ds, row(bad, 1)))
          .isInstanceOf(DestinationException.class)
          .hasMessageContaining(ILLEGAL);
      assertThat(jdbc.prepared).isEmpty();
    }
  }
}
