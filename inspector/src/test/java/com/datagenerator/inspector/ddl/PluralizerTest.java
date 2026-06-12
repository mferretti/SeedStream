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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PluralizerTest {

  @Test
  void inflectsLastTokenOnly() {
    assertThat(Pluralizer.pluralize("invoice_item")).isEqualTo("invoice_items");
  }

  @Test
  void simpleWordAddsS() {
    assertThat(Pluralizer.pluralize("invoice")).isEqualTo("invoices");
  }

  @Test
  void sibilantAddsEs() {
    assertThat(Pluralizer.pluralize("address")).isEqualTo("addresses");
    assertThat(Pluralizer.pluralize("box")).isEqualTo("boxes");
    assertThat(Pluralizer.pluralize("dish")).isEqualTo("dishes");
    assertThat(Pluralizer.pluralize("batch")).isEqualTo("batches");
  }

  @Test
  void consonantYBecomesIes() {
    assertThat(Pluralizer.pluralize("category")).isEqualTo("categories");
    assertThat(Pluralizer.pluralize("order_category")).isEqualTo("order_categories");
  }

  @Test
  void vowelYJustAddsS() {
    assertThat(Pluralizer.pluralize("day")).isEqualTo("days");
  }

  @Test
  void blankInputPassesThrough() {
    assertThat(Pluralizer.pluralize("")).isEmpty();
    assertThat(Pluralizer.pluralize(null)).isNull();
  }
}
