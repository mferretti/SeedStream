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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** #213: a single-column self-FK with a resolvable key is the bare {@code ref[t.c]} form. */
class DdlInspectorSelfRefTest {

  private static Double nullable(Inspection in, String table, String field) {
    DataStructure s =
        in.structures().stream()
            .filter(x -> x.getName().equals(table))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no structure named " + table));
    return s.getData().get(field).getNullable();
  }

  @Test
  void shouldEmitBareRefAndNullableForNullableSelfForeignKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE emp (id BIGINT PRIMARY KEY, boss BIGINT REFERENCES emp(id), n INT);");

    assertThat(datatypesOf(in, "emp")).containsEntry("boss", "ref[emp.id]");
    assertThat(nullable(in, "emp", "boss")).isEqualTo(0.05);
    assertThat(nullable(in, "emp", "id")).isNull();
    loadAll(in, dir);
  }

  @Test
  void shouldEmitBareRefWithoutNullableForNotNullSelfForeignKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE emp (id BIGINT PRIMARY KEY, boss BIGINT NOT NULL REFERENCES emp(id));");

    assertThat(datatypesOf(in, "emp")).containsEntry("boss", "ref[emp.id]");
    assertThat(nullable(in, "emp", "boss")).isNull();
    loadAll(in, dir);
  }

  @Test
  void shouldEmitBareRefForTableLevelSelfForeignKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE emp (id INT, boss INT, PRIMARY KEY (id),"
                + " FOREIGN KEY (boss) REFERENCES emp (id));");

    assertThat(datatypesOf(in, "emp")).containsEntry("boss", "ref[emp.id]");
    loadAll(in, dir);
  }

  @Test
  void shouldKeepExplicitPoolForCompositeSelfForeignKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (a INT NOT NULL, b INT NOT NULL, pa INT, pb INT, PRIMARY KEY (a, b),"
                + " FOREIGN KEY (pa, pb) REFERENCES t (a, b));");

    assertThat(datatypesOf(in, "t"))
        .containsEntry("pa", "ref[t.a, 1..count]")
        .containsEntry("pb", "ref[t.b, 1..count]");
  }

  @Test
  void shouldKeepExplicitPoolForSelfForeignKeyWithUnresolvableKey(@TempDir Path dir) {
    Inspection in =
        inspectDdl(
            dir,
            "CREATE TABLE t (id UUID PRIMARY KEY, p UUID REFERENCES t(id));",
            NestingOptions.none(),
            true);

    assertThat(datatypesOf(in, "t")).containsEntry("p", "ref[t.id, 1..count]");
  }
}
