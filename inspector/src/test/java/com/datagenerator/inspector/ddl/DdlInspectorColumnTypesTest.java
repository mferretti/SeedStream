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
import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.Inspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Per-dialect column type coverage: one wide table per dialect, every column's exact datatype is
 * asserted (spec {@code docs/INSPECT-V1-SPEC.md} §4, §7c, §8).
 */
class DdlInspectorColumnTypesTest {

  // Defaults capped to the column's capacity (#377).
  private static final String INT_SMALLINT = "int[1..32767]";
  private static final String INT_TINYINT = "int[1..127]";
  private static final String DEC_5_2 = "decimal[0.00..999.99]";

  private static final String INT = "int[1..999999]";
  private static final String DEC = "decimal[0.0..9999.99]";
  private static final String DATE = "date[2020-01-01..2030-12-31]";
  private static final String TS = "timestamp[now-365d..now]";
  private static final String STR = "char[1..50]";

  private final DdlInspector inspector = new DdlInspector();

  @Test
  void shouldMapEveryMysqlColumnType(@TempDir Path dir) throws IOException {
    String ddl =
        """
        CREATE TABLE IF NOT EXISTS `all_types` (
          `id` INT NOT NULL AUTO_INCREMENT,
          `c_tiny` TINYINT,
          `c_flag` TINYINT(1) DEFAULT 1,
          `c_small` SMALLINT,
          `c_medium` MEDIUMINT,
          `c_int` INT(11) NOT NULL DEFAULT 0,
          `c_big` BIGINT,
          `c_dec` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
          `c_num` NUMERIC(8,3),
          `c_float` FLOAT,
          `c_double` DOUBLE,
          `c_char` CHAR(3),
          `c_varchar` VARCHAR(100) NOT NULL DEFAULT 'x',
          `c_text` TEXT,
          `c_longtext` LONGTEXT,
          `c_bool` BOOLEAN,
          `c_date` DATE,
          `c_ts` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          `c_dt` DATETIME,
          `c_json` JSON,
          PRIMARY KEY (`id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        """;

    Map<String, String> types = datatypesOf(inspect(dir, ddl), "all_types");

    assertThat(types)
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry("id", "serial"),
                Map.entry("c_tiny", INT_TINYINT),
                Map.entry("c_flag", INT_TINYINT),
                Map.entry("c_small", INT_SMALLINT),
                Map.entry("c_medium", INT),
                Map.entry("c_int", INT),
                Map.entry("c_big", INT),
                Map.entry("c_dec", DEC),
                Map.entry("c_num", DEC),
                Map.entry("c_float", DEC),
                Map.entry("c_double", DEC),
                Map.entry("c_char", "char[1..3]"),
                Map.entry("c_varchar", "char[1..100]"),
                Map.entry("c_text", "char[1..500]"),
                Map.entry("c_longtext", "char[1..500]"),
                Map.entry("c_bool", "boolean"),
                Map.entry("c_date", DATE),
                Map.entry("c_ts", TS),
                Map.entry("c_dt", TS),
                Map.entry("c_json", STR)));
  }

  @Test
  void shouldFlagMysqlJsonAsUnrecognizedType(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, "CREATE TABLE t (`doc` JSON, n INT);");

    assertThat(inspection.comments().get("t"))
        .containsEntry("doc", "unrecognized source type, defaulted — verify")
        .doesNotContainKey("n");
  }

  @Test
  void shouldMapMysqlEnumColumnToEnumOrFlaggedFallback(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, "CREATE TABLE t (`status` ENUM('NEW','DONE'), n INT);");

    // spec §6 Q2: an unrecognized type must fall back to char[1..50] with a review flag; a
    // recognized ENUM would be enum[NEW,DONE]. Either is acceptable, silently mis-typing is not.
    String datatype = datatypesOf(inspection, "t").get("status");
    if (STR.equals(datatype)) {
      assertThat(inspection.comments().get("t")).containsKey("status");
    } else {
      assertThat(datatype).isEqualTo("enum[NEW,DONE]");
    }
  }

  @Test
  void shouldMapEveryPostgresColumnType(@TempDir Path dir) throws IOException {
    String ddl =
        """
        CREATE TABLE IF NOT EXISTS all_types (
          id            SERIAL PRIMARY KEY,
          c_seq         BIGSERIAL,
          c_small       SMALLINT,
          c_int         INTEGER NOT NULL DEFAULT 0,
          c_big         BIGINT,
          c_int8        INT8,
          c_dec         DECIMAL(12,4),
          c_num         NUMERIC(10,2) NOT NULL,
          c_real        REAL,
          c_double      DOUBLE PRECISION,
          c_char        CHAR(8),
          c_varchar     VARCHAR(64),
          c_charvar     CHARACTER VARYING(32),
          c_text        TEXT,
          c_bool        BOOLEAN DEFAULT FALSE,
          c_date        DATE,
          c_ts          TIMESTAMP,
          c_tstz        TIMESTAMPTZ DEFAULT now(),
          c_tstz_long   TIMESTAMP WITH TIME ZONE,
          c_ts_no_tz    TIMESTAMP WITHOUT TIME ZONE,
          c_uuid        UUID DEFAULT gen_random_uuid(),
          c_json        JSON,
          c_jsonb       JSONB,
          c_bytea       BYTEA
        );
        """;

    Map<String, String> types = datatypesOf(inspect(dir, ddl), "all_types");

    assertThat(types)
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry("id", "serial"),
                Map.entry("c_seq", "serial"),
                Map.entry("c_small", INT_SMALLINT),
                Map.entry("c_int", INT),
                Map.entry("c_big", INT),
                Map.entry("c_int8", INT),
                Map.entry("c_dec", DEC),
                Map.entry("c_num", DEC),
                Map.entry("c_real", DEC),
                Map.entry("c_double", DEC),
                Map.entry("c_char", "char[1..8]"),
                Map.entry("c_varchar", "char[1..64]"),
                Map.entry("c_charvar", "char[1..32]"),
                Map.entry("c_text", "char[1..500]"),
                Map.entry("c_bool", "boolean"),
                Map.entry("c_date", DATE),
                Map.entry("c_ts", TS),
                Map.entry("c_tstz", TS),
                Map.entry("c_tstz_long", TS),
                Map.entry("c_ts_no_tz", TS),
                Map.entry("c_uuid", "uuid"),
                Map.entry("c_json", STR),
                Map.entry("c_jsonb", STR),
                Map.entry("c_bytea", STR)));
  }

  @Test
  void shouldMapEveryOracleColumnType(@TempDir Path dir) throws IOException {
    String ddl =
        """
        CREATE TABLE all_types (
          id          NUMBER(10) NOT NULL,
          c_num       NUMBER,
          c_dec       NUMBER(12,2),
          c_int       INTEGER,
          c_float     FLOAT,
          c_bdouble   BINARY_DOUBLE,
          c_char      CHAR(5),
          c_varchar2  VARCHAR2(255) DEFAULT 'x' NOT NULL,
          c_nvarchar2 NVARCHAR2(40),
          c_clob      CLOB,
          c_date      DATE DEFAULT SYSDATE,
          c_ts        TIMESTAMP,
          c_ts6       TIMESTAMP(6),
          CONSTRAINT pk_all PRIMARY KEY (id)
        );
        """;

    Map<String, String> types = datatypesOf(inspect(dir, ddl), "all_types");

    assertThat(types)
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                // NUMBER(10) is a decimal in the spec's synonym table; so the PK is not integer
                // and stays unenforced rather than serial
                Map.entry("id", DEC),
                Map.entry("c_num", DEC),
                Map.entry("c_dec", DEC),
                Map.entry("c_int", INT),
                Map.entry("c_float", DEC),
                Map.entry("c_bdouble", DEC),
                Map.entry("c_char", "char[1..5]"),
                Map.entry("c_varchar2", "char[1..255]"),
                Map.entry("c_nvarchar2", "char[1..40]"),
                Map.entry("c_clob", "char[1..500]"),
                Map.entry("c_date", DATE),
                Map.entry("c_ts", TS),
                Map.entry("c_ts6", TS)));
  }

  @Test
  void shouldMapEverySqlServerColumnType(@TempDir Path dir) throws IOException {
    String ddl =
        """
        CREATE TABLE [dbo].[all_types] (
          [id]        INT IDENTITY(1,1) NOT NULL,
          [c_tiny]    TINYINT NULL,
          [c_small]   SMALLINT NULL,
          [c_big]     BIGINT NULL,
          [c_dec]     DECIMAL(18,2) NULL,
          [c_num]     NUMERIC(9,4) NULL,
          [c_float]   FLOAT NULL,
          [c_real]    REAL NULL,
          [c_money]   MONEY NULL,
          [c_char]    CHAR(10) NULL,
          [c_varchar] VARCHAR(50) NOT NULL DEFAULT ('x'),
          [c_nvar]    NVARCHAR(255) NULL,
          [c_ntext]   NTEXT NULL,
          [c_text]    TEXT NULL,
          [c_date]    DATE NULL,
          [c_dt]      DATETIME NULL DEFAULT (getdate()),
          [c_sdt]     SMALLDATETIME NULL,
          [c_guid]    UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
          CONSTRAINT [pk_all] PRIMARY KEY ([id])
        );
        """;

    Map<String, String> types = datatypesOf(inspect(dir, ddl), "all_types");

    assertThat(types)
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry("id", "serial"),
                Map.entry("c_tiny", INT_TINYINT),
                Map.entry("c_small", INT_SMALLINT),
                Map.entry("c_big", INT),
                Map.entry("c_dec", DEC),
                Map.entry("c_num", DEC),
                Map.entry("c_float", DEC),
                Map.entry("c_real", DEC),
                Map.entry("c_money", DEC),
                Map.entry("c_char", "char[1..10]"),
                Map.entry("c_varchar", "char[1..50]"),
                Map.entry("c_nvar", "char[1..255]"),
                Map.entry("c_ntext", "char[1..500]"),
                Map.entry("c_text", "char[1..500]"),
                Map.entry("c_date", DATE),
                Map.entry("c_dt", TS),
                Map.entry("c_sdt", TS),
                Map.entry("c_guid", "uuid")));
  }

  @Test
  void shouldMapSqlServerDatetime2ToTimestamp(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(dir, "CREATE TABLE [t] ([a] DATETIME2 NULL, [b] DATETIME2(3) NULL, [n] INT);");

    assertThat(datatypesOf(inspection, "t"))
        .containsExactlyInAnyOrderEntriesOf(Map.of("a", TS, "b", TS, "n", INT));
  }

  @Test
  void shouldMapSqlServerClusteredPrimaryKeyWithSortDirectionToSerial(@TempDir Path dir)
      throws IOException {
    // The SSMS-scripted form: PRIMARY KEY CLUSTERED ([id] ASC). Spec §8: single-column integer PK
    // -> serial.
    Inspection inspection =
        inspect(
            dir,
            """
            CREATE TABLE [dbo].[t] (
              [id] INT IDENTITY(1,1) NOT NULL,
              [n] INT NULL,
              CONSTRAINT [pk_t] PRIMARY KEY CLUSTERED ([id] ASC)
            );
            """);

    Map<String, String> types = datatypesOf(inspection, "t");
    assertThat(types).containsEntry("id", "serial").containsEntry("n", INT);
    assertThat(inspection.comments().getOrDefault("t", Map.of()).keySet())
        .as("review comments must be keyed by real field names")
        .isSubsetOf(types.keySet());
  }

  @Test
  void shouldMapPrimaryKeyColumnWithSortDirectionToSerial(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, "CREATE TABLE t (id INT, n INT, PRIMARY KEY (id ASC));");

    assertThat(datatypesOf(inspection, "t")).containsEntry("id", "serial");
  }

  @Test
  void shouldParseOracleRawColumnAsFlaggedFallback(@TempDir Path dir) throws IOException {
    // RAW(16) is Oracle's idiom for a GUID; strict mode must not abort the whole script on it.
    Inspection inspection = inspect(dir, "CREATE TABLE t (id NUMBER(10), guid RAW(16));");

    assertThat(datatypesOf(inspection, "t")).containsEntry("guid", STR);
    assertThat(inspection.comments().get("t")).containsKey("guid");
  }

  @Test
  void shouldMapOracleIdentityColumnWithoutAbortingTheScript(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(dir, "CREATE TABLE t (id NUMBER(10) GENERATED ALWAYS AS IDENTITY, n INT);");

    assertThat(datatypesOf(inspection, "t"))
        .containsExactlyInAnyOrderEntriesOf(Map.of("id", DEC, "n", INT));
  }

  @Test
  void shouldMapSqlServerBitToBoolean(@TempDir Path dir) throws IOException {
    // BIT is SQL Server's boolean (0/1). Spec §6 Q2 allows an unrecognized-type fallback but it
    // must then be flagged; generating char[1..50] into a BIT column would not insert.
    Inspection inspection = inspect(dir, "CREATE TABLE [t] ([flag] BIT NOT NULL, [n] INT);");

    String datatype = datatypesOf(inspection, "t").get("flag");
    assertThat(datatype).isEqualTo("boolean");
  }

  @Test
  void shouldMapSqlServerNvarcharMaxToBoundedChar(@TempDir Path dir) throws IOException {
    Inspection inspection = inspect(dir, "CREATE TABLE [t] ([body] NVARCHAR(MAX), [n] INT);");

    // MAX is not a length: the column must not be mapped to a nonsensical bound
    assertThat(datatypesOf(inspection, "t").get("body")).isIn("char[1..255]", "char[1..500]");
  }

  @Test
  void shouldKeepDecimalAndSmallIntRangesWithinColumnCapacity(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
            CREATE TABLE t (
              small_col  SMALLINT,
              tiny_col   TINYINT,
              narrow_dec DECIMAL(4,2),
              n          INT
            );
            """);
    Map<String, String> types = datatypesOf(inspection, "t");

    // SMALLINT max 32767, TINYINT (unsigned) max 255, DECIMAL(4,2) max 99.99: spec §4 default
    // ranges (int 1..999999, decimal 0.0..9999.99) would overflow these columns on insert.
    assertThat(maxOf(types.get("small_col"))).isLessThanOrEqualTo(32767d);
    assertThat(maxOf(types.get("tiny_col"))).isLessThanOrEqualTo(255d);
    assertThat(maxOf(types.get("narrow_dec"))).isLessThanOrEqualTo(99.99d);
  }

  @Test
  void shouldStripQuotesFromMysqlBacktickIdentifiers(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(dir, "CREATE TABLE `Order Items` (`line no` INT, `Qty` DECIMAL(5,2));");

    assertThat(datatypesOf(inspection, "order_items"))
        .containsExactlyInAnyOrderEntriesOf(Map.of("line no", INT, "Qty", DEC_5_2));
  }

  @Test
  void shouldStripQuotesFromPostgresDoubleQuotedIdentifiers(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(dir, "CREATE TABLE \"Customer\" (\"firstCol\" INT, \"Qty\" NUMERIC(5,2));");

    assertThat(datatypesOf(inspection, "customer"))
        .containsExactlyInAnyOrderEntriesOf(Map.of("firstCol", INT, "Qty", DEC_5_2));
  }

  @Test
  void shouldStripBracketsFromSqlServerIdentifiers(@TempDir Path dir) throws IOException {
    Inspection inspection =
        inspect(dir, "CREATE TABLE [Sales].[OrderLine] ([LineNo] INT, [Qty] DECIMAL(5,2));");

    assertThat(datatypesOf(inspection, "order_line"))
        .containsExactlyInAnyOrderEntriesOf(Map.of("LineNo", INT, "Qty", DEC_5_2));
  }

  @Test
  void shouldUseTableNameWithoutSchemaForSchemaQualifiedPostgresTable(@TempDir Path dir)
      throws IOException {
    Inspection inspection = inspect(dir, "CREATE TABLE public.accounts (acc_no INT, bal NUMERIC);");

    assertThat(inspection.structures()).extracting(s -> s.getName()).containsExactly("accounts");
    assertThat(datatypesOf(inspection, "accounts"))
        .containsExactlyInAnyOrderEntriesOf(Map.of("acc_no", INT, "bal", DEC));
  }

  @Test
  void shouldResolveSchemaQualifiedForeignKeyTargetToBareTableName(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
            CREATE TABLE sales.customers (id INT PRIMARY KEY);
            CREATE TABLE sales.orders (
              id INT PRIMARY KEY,
              customer_id INT,
              CONSTRAINT fk_c FOREIGN KEY (customer_id) REFERENCES sales.customers (id)
            );
            """);

    assertThat(datatypesOf(inspection, "orders"))
        .containsEntry("customer_id", "ref[customers.id, 1..count]");
  }

  @Test
  void shouldParseCreateTableIfNotExistsWithQuotedAndPlainNames(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir,
            """
            CREATE TABLE IF NOT EXISTS a (x INT);
            CREATE TABLE IF NOT EXISTS `b` (y INT);
            CREATE TABLE IF NOT EXISTS "c" (z INT);
            """);

    assertThat(inspection.structures()).extracting(s -> s.getName()).containsExactly("a", "b", "c");
  }

  private static double maxOf(String datatype) {
    String range = datatype.substring(datatype.indexOf('[') + 1, datatype.indexOf(']'));
    return Double.parseDouble(range.substring(range.indexOf("..") + 2));
  }

  private Inspection inspect(Path dir, String ddl) throws IOException {
    Path sql = dir.resolve("schema.sql");
    Files.writeString(sql, ddl);
    return inspector.inspect(sql);
  }

  @Test
  void shouldStripSortDirectionFromCompositeAndUniqueKeyColumns(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir,
            "CREATE TABLE t (a INT, b INT, code INT, c INT,"
                + " PRIMARY KEY (a DESC, b ASC), UNIQUE (code DESC));");

    Map<String, String> types = datatypesOf(inspection, "t");
    assertThat(types.get("a")).startsWith("unique[pk,");
    assertThat(types.get("b")).startsWith("unique[pk,");
    assertThat(types.get("code")).startsWith("unique[");
    assertThat(inspection.warnings()).noneMatch(w -> w.contains(" ASC") || w.contains(" DESC"));
  }

  @Test
  void shouldParseOracleRawAlongsideOtherColumnsAndKeepTheirTypes(@TempDir Path dir)
      throws IOException {
    Inspection inspection =
        inspect(
            dir,
            "CREATE TABLE doc (id NUMBER(10) PRIMARY KEY, guid RAW(16), raw_note VARCHAR2(20));");

    Map<String, String> types = datatypesOf(inspection, "doc");
    assertThat(types).containsEntry("guid", STR).containsEntry("raw_note", "char[1..20]");
  }
}
