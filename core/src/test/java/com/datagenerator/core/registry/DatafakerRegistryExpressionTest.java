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

package com.datagenerator.core.registry;

import static org.assertj.core.api.Assertions.*;

import java.util.Random;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;

class DatafakerRegistryExpressionTest {

  @Test
  void shouldRegisterAndGenerateFromMethodPathExpression() {
    DatafakerRegistry.registerExpression("test_full_name", "name.fullName");

    assertThat(DatafakerRegistry.isRegistered("test_full_name")).isTrue();
    String value = DatafakerRegistry.generate("test_full_name", new Faker(), new Random(1));
    assertThat(value).isNotBlank();
  }

  @Test
  void shouldFailFastOnUnknownMethod() {
    assertThatThrownBy(() -> DatafakerRegistry.registerExpression("bad", "name.thereIsNoSuchThing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown Datafaker method");
  }

  @Test
  void shouldRejectBlankExpression() {
    assertThatThrownBy(() -> DatafakerRegistry.registerExpression("bad", "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
