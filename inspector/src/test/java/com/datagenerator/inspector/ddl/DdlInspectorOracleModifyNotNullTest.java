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

import static com.datagenerator.inspector.ddl.DdlTestSupport.inspectDdl;
import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.inspector.Inspection;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #439: Oracle {@code ALTER TABLE … MODIFY (c NOT NULL)} makes any column, not only binary ones,
 * NOT NULL.
 */
class DdlInspectorOracleModifyNotNullTest {

  private static final String BASE =
      """
      CREATE TABLE "T" ("ID" NUMBER(10), "A" VARCHAR2(20), "B" VARCHAR2(20), "C" NUMBER(10,2), "D" VARCHAR2(20));
      ALTER TABLE "T" ADD CONSTRAINT "T_PK" PRIMARY KEY ("ID");
      """;

  private static DataStructure inspect(Path dir, String modify) {
    Inspection in = inspectDdl(dir, BASE + modify);
    return in.structures().stream().filter(s -> s.getName().equals("t")).findFirst().orElseThrow();
  }

  private static Double nullable(DataStructure t, String column) {
    return t.getData().get(column).getNullable();
  }

  @Test
  void shouldNotEmitNullableForVarcharColumnMadeNotNullByModify(@TempDir Path dir) {
    DataStructure t = inspect(dir, "ALTER TABLE \"T\" MODIFY (\"A\" NOT NULL ENABLE);");

    assertThat(nullable(t, "A")).isNull();
    assertThat(nullable(t, "B")).isEqualTo(0.05);
    assertThat(nullable(t, "D")).isEqualTo(0.05);
  }

  @Test
  void shouldMakeEveryColumnOfMultiColumnModifyNotNull(@TempDir Path dir) {
    DataStructure t = inspect(dir, "ALTER TABLE \"T\" MODIFY (\"A\" NOT NULL, \"B\" NOT NULL);");

    assertThat(nullable(t, "A")).isNull();
    assertThat(nullable(t, "B")).isNull();
    assertThat(nullable(t, "D")).isEqualTo(0.05);
  }

  @Test
  void shouldMakeColumnNotNullWhenModifyAlsoRedeclaresItsType(@TempDir Path dir) {
    DataStructure t = inspect(dir, "ALTER TABLE \"T\" MODIFY (\"C\" NUMBER(10,2) NOT NULL);");

    assertThat(nullable(t, "C")).isNull();
    assertThat(nullable(t, "A")).isEqualTo(0.05);
  }
}
