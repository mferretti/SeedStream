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

import static com.datagenerator.inspector.InspectionTestSupport.datatypesOf;
import static com.datagenerator.inspector.ddl.DdlTestSupport.inspectDdl;
import static com.datagenerator.inspector.ddl.DdlTestSupport.loadAll;
import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * #440 / #441 phase A: a generated key states the largest value its SQL type holds, and a text key
 * is generated as numeric strings so it is enforceable like an integer key.
 */
class DdlInspectorKeyCeilingTest {

  private static final String TEXT_KEY = "text key generated as numeric strings";
  private static final String NOT_ENFORCED = "not enforced";

  private static String comment(Inspection in, String table, String field) {
    return in.comments().getOrDefault(table, Map.of()).get(field);
  }

  private static String pkType(Path dir, String sqlType) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (id " + sqlType + " PRIMARY KEY, n INT);");
    loadAll(in, dir);
    return datatypesOf(in, "t").get("id");
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "TINYINT | serial[1..127]",
        "SMALLINT | serial[1..32767]",
        "INT2 | serial[1..32767]",
        "MEDIUMINT | serial[1..8388607]",
        "INT | serial[1..2147483647]",
        "INTEGER | serial[1..2147483647]",
        "INT4 | serial[1..2147483647]",
        "BIGINT | serial",
        "INT8 | serial",
        "NUMBER(5) | serial[1..99999]",
        "NUMBER(5,0) | serial[1..99999]",
        "DECIMAL(9) | serial[1..999999999]",
        "NUMERIC(18) | serial[1..999999999999999999]",
        "NUMERIC(19) | serial",
        "VARCHAR2(5 BYTE) | serial[1..99999]",
        "VARCHAR2(5 CHAR) | serial[1..99999]",
        "char(2) | serial[1..99]",
        "nchar(5) | serial[1..99999]",
        "CHARACTER VARYING(5) | serial[1..99999]",
        "varchar(18) | serial[1..999999999999999999]",
        "varchar(19) | serial",
        "varchar | serial",
        "text | serial"
      })
  void shouldStateCeilingOfSinglePrimaryKeyFromSqlType(
      String sqlType, String expected, @TempDir Path dir) {
    assertThat(pkType(dir, sqlType)).isEqualTo(expected);
  }

  @Test
  void shouldStateCeilingOfUnclaimedSmallSerialAndLeaveBigSerialBare(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (seq SMALLSERIAL, big BIGSERIAL, n INT);");
    loadAll(in, dir);

    assertThat(datatypesOf(in, "t"))
        .containsEntry("seq", "serial[1..32767]")
        .containsEntry("big", "serial");
  }

  @Test
  void shouldStateCeilingOfUnclaimedSerial(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (seq SERIAL, n INT);");

    assertThat(datatypesOf(in, "t")).containsEntry("seq", "serial[1..2147483647]");
  }

  @Test
  void shouldMakeTextPrimaryKeyEnforcedAndCommentedWhenReferenced(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE customers (customer_id varchar(5) PRIMARY KEY, name VARCHAR(30));
            CREATE TABLE orders (
              id BIGINT PRIMARY KEY,
              customer_id varchar(5) REFERENCES customers
            );
            """);
    Map<String, DataStructure> loaded = loadAll(in, dir);

    assertThat(datatypesOf(in, "customers")).containsEntry("customer_id", "serial[1..99999]");
    assertThat(comment(in, "customers", "customer_id")).isEqualTo(TEXT_KEY + " — max 99999 rows");
    assertThat(datatypesOf(in, "orders"))
        .containsEntry("customer_id", "ref[customers.customer_id]");
    assertThat(in.warnings()).noneMatch(w -> w.contains(NOT_ENFORCED));
    assertThat(loaded).containsKeys("customers", "orders");
  }

  @Test
  void shouldNotCommentCeilingOnUnboundedTextKeyButStillExplainNumericStrings(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (id text PRIMARY KEY, n INT);");

    assertThat(datatypesOf(in, "t")).containsEntry("id", "serial");
    assertThat(comment(in, "t", "id")).isEqualTo(TEXT_KEY);
  }

  @Test
  void shouldMakeSingleTextUniqueAnEnforcedUniqueRange(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE t (id BIGINT PRIMARY KEY, sku VARCHAR(6) UNIQUE);");
    loadAll(in, dir);

    assertThat(datatypesOf(in, "t")).containsEntry("sku", "unique[1..count]");
    assertThat(comment(in, "t", "sku")).isEqualTo(TEXT_KEY + " — max 999999 rows");
    assertThat(in.warnings()).noneMatch(w -> w.contains(NOT_ENFORCED));
  }

  @Test
  void shouldMakeCompositePrimaryKeyWithTextPartAnEnforcedGroup(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE t (code VARCHAR(4), slot INT, PRIMARY KEY (code, slot));");
    loadAll(in, dir);

    assertThat(datatypesOf(in, "t"))
        .containsEntry("code", "unique[pk, 1..count]")
        .containsEntry("slot", "unique[pk, 1..count]");
    assertThat(comment(in, "t", "code")).isEqualTo(TEXT_KEY + " — max 9999 rows");
    assertThat(comment(in, "t", "slot")).isNull();
    assertThat(in.warnings()).noneMatch(w -> w.contains(NOT_ENFORCED));
  }

  @Test
  void shouldStillNotEnforceCompositeKeyWithDatePart(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE t (code VARCHAR(4), d DATE, PRIMARY KEY (code, d));");

    assertThat(datatypesOf(in, "t").get("code"))
        .doesNotStartWith("unique[")
        .doesNotStartWith("serial");
    assertThat(datatypesOf(in, "t").get("d")).startsWith("date[");
    assertThat(in.warnings()).anyMatch(w -> w.contains(NOT_ENFORCED));
  }

  @Test
  void shouldReplaceNameHintOnTextKeyWithNumericStringKey(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (country varchar(15) PRIMARY KEY, n INT);");
    loadAll(in, dir);

    assertThat(datatypesOf(in, "t")).containsEntry("country", "serial[1..999999999999999]");
    assertThat(comment(in, "t", "country")).isEqualTo(TEXT_KEY + " — max 999999999999999 rows");
  }

  /** Name-hinted UNIQUE text (email, username) keeps its semantic type: #455 makes it distinct. */
  @Test
  void shouldKeepNameHintedTextUniqueSemanticAndNotEnforced(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE users (id BIGINT PRIMARY KEY, email VARCHAR(100) UNIQUE);");
    loadAll(in, dir);

    assertThat(datatypesOf(in, "users")).containsEntry("email", "email");
    assertThat(comment(in, "users", "email")).contains(NOT_ENFORCED).doesNotContain(TEXT_KEY);
    assertThat(in.warnings()).anyMatch(w -> w.contains("UNIQUE(email) " + NOT_ENFORCED));
  }

  @Test
  void shouldCommentCeilingOnSmallIntUniqueButNotOnIntegerUnique(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir, "CREATE TABLE t (id BIGINT PRIMARY KEY, a SMALLINT UNIQUE, b INTEGER UNIQUE);");

    assertThat(datatypesOf(in, "t"))
        .containsEntry("a", "unique[1..count]")
        .containsEntry("b", "unique[1..count]");
    assertThat(comment(in, "t", "a")).isEqualTo("max 32767 rows");
    assertThat(comment(in, "t", "b")).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"id > 0", "id >= 1"})
  void shouldAddNoNoteWhenCheckHoldsFromOneOnBoundedKey(String check, @TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE t (id smallint PRIMARY KEY, CHECK (" + check + "));");

    assertThat(datatypesOf(in, "t")).containsEntry("id", "serial[1..32767]");
    assertThat(comment(in, "t", "id")).isNull();
    assertThat(in.warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"id > 100", "id >= 1000", "id BETWEEN 1 AND 50"})
  void shouldNoteCheckThatBoundedKeyCanViolate(String check, @TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE t (id smallint PRIMARY KEY, CHECK (" + check + "));");

    assertThat(datatypesOf(in, "t")).containsEntry("id", "serial[1..32767]");
    assertThat(comment(in, "t", "id")).isNotNull().containsIgnoringCase("check");
  }

  @Test
  void shouldKeepMySqlEnumKeyAsCycle(@TempDir Path dir) {
    Inspection in = inspectDdl(dir, "CREATE TABLE t (code ENUM('A','B') PRIMARY KEY, n INT);");

    assertThat(datatypesOf(in, "t")).containsEntry("code", "cycle[A,B]");
  }

  @Test
  void shouldKeepCheckInListTextKeyAsCycle(@TempDir Path dir) {
    Inspection in =
        inspectDdl(dir, "CREATE TABLE t (code VARCHAR(5) PRIMARY KEY, CHECK (code IN ('A','B')));");

    assertThat(datatypesOf(in, "t")).containsEntry("code", "cycle[A,B]");
  }

  @Test
  void shouldKeepReferencedSmallIntIdentityKeptAsSerialWarning(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            """
            CREATE TABLE account (
              id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
              name VARCHAR(100)
            );
            CREATE TABLE txn (id BIGINT PRIMARY KEY, account_id SMALLINT REFERENCES account(id));
            """);

    assertThat(datatypesOf(in, "account")).containsEntry("id", "serial[1..32767]");
    assertThat(in.warnings())
        .anyMatch(
            w -> w.contains("account.id") && w.contains("kept as serial") && w.contains("#400"));
  }
}
