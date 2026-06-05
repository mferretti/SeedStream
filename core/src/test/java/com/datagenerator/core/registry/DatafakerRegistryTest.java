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

import java.util.Locale;
import java.util.Random;
import java.util.Set;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;

class DatafakerRegistryTest {

  private static final Faker FAKER = new Faker(Locale.US, new Random(42));
  private static final Random RANDOM = new Random(42);

  // ── Built-in presence ──────────────────────────────────────────────────────

  @Test
  void shouldHave48BuiltInTypes() {
    assertThat(DatafakerRegistry.listTypes()).hasSizeGreaterThanOrEqualTo(48);
  }

  @Test
  void shouldBeRegisteredForCoreBuiltInTypes() {
    assertThat(DatafakerRegistry.isRegistered("name")).isTrue();
    assertThat(DatafakerRegistry.isRegistered("email")).isTrue();
    assertThat(DatafakerRegistry.isRegistered("city")).isTrue();
    assertThat(DatafakerRegistry.isRegistered("uuid")).isTrue();
    assertThat(DatafakerRegistry.isRegistered("iban")).isTrue();
  }

  @Test
  void shouldNotBeRegisteredForUnknownType() {
    assertThat(DatafakerRegistry.isRegistered("completely_unknown_xyz_123")).isFalse();
  }

  @Test
  void shouldReturnFalseIsRegisteredForNull() {
    assertThat(DatafakerRegistry.isRegistered(null)).isFalse();
  }

  @Test
  void shouldReturnFalseIsRegisteredForBlank() {
    assertThat(DatafakerRegistry.isRegistered("   ")).isFalse();
    assertThat(DatafakerRegistry.isRegistered("")).isFalse();
  }

  // ── Generate built-in types ────────────────────────────────────────────────

  @Test
  void shouldGenerateNonNullValueForBuiltInName() {
    String value = DatafakerRegistry.generate("name", FAKER, RANDOM);
    assertThat(value).isNotBlank();
  }

  @Test
  void shouldGenerateNonNullValueForBuiltInEmail() {
    String value = DatafakerRegistry.generate("email", FAKER, RANDOM);
    assertThat(value).isNotBlank().contains("@");
  }

  @Test
  void shouldGenerateNonNullValueForBuiltInUuid() {
    String value = DatafakerRegistry.generate("uuid", FAKER, RANDOM);
    assertThat(value).isNotBlank().matches("[0-9a-f-]{36}");
  }

  @Test
  void shouldThrowForUnregisteredType() {
    assertThatThrownBy(() -> DatafakerRegistry.generate("pokemon_xyz_unknown", FAKER, RANDOM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pokemon_xyz_unknown");
  }

  // ── Alias resolution ───────────────────────────────────────────────────────

  @Test
  void shouldResolveBuiltInAlias_latToLatitude() {
    assertThat(DatafakerRegistry.getCanonicalName("lat")).isEqualTo("latitude");
  }

  @Test
  void shouldResolveBuiltInAlias_lonToLongitude() {
    assertThat(DatafakerRegistry.getCanonicalName("lon")).isEqualTo("longitude");
  }

  @Test
  void shouldReturnSameNameWhenNoAlias() {
    assertThat(DatafakerRegistry.getCanonicalName("email")).isEqualTo("email");
  }

  @Test
  void shouldGenerateViaAlias() {
    // "lat" → "latitude" → should produce a numeric string
    String value = DatafakerRegistry.generate("lat", FAKER, RANDOM);
    assertThat(value).isNotBlank();
  }

  @Test
  void shouldIsRegisteredReturnTrueForAlias() {
    assertThat(DatafakerRegistry.isRegistered("lat")).isTrue();
    assertThat(DatafakerRegistry.isRegistered("swift")).isTrue();
    assertThat(DatafakerRegistry.isRegistered("zipcode")).isTrue();
  }

  // ── Custom type registration ───────────────────────────────────────────────

  @Test
  void shouldRegisterCustomTypeAndGenerate() {
    String typeName = "test_custom_type_" + System.nanoTime();
    DatafakerRegistry.register(typeName, (faker, random) -> "custom-value-42");

    try {
      assertThat(DatafakerRegistry.isRegistered(typeName)).isTrue();
      assertThat(DatafakerRegistry.generate(typeName, FAKER, RANDOM)).isEqualTo("custom-value-42");
    } finally {
      // Cannot unregister; re-register with a sentinel to mark stale
      DatafakerRegistry.register(typeName, (faker, random) -> "__removed__");
    }
  }

  @Test
  void shouldOverwriteCustomTypeWhenReRegistered() {
    String typeName = "test_overwrite_" + System.nanoTime();
    DatafakerRegistry.register(typeName, (faker, random) -> "first");
    DatafakerRegistry.register(typeName, (faker, random) -> "second");

    assertThat(DatafakerRegistry.generate(typeName, FAKER, RANDOM)).isEqualTo("second");
  }

  @Test
  void shouldRegisterCustomAliasAndResolve() {
    String canonical = "test_canonical_" + System.nanoTime();
    String alias = "test_alias_" + System.nanoTime();
    DatafakerRegistry.register(canonical, (faker, random) -> "aliased-value");
    DatafakerRegistry.registerAlias(alias, canonical);

    assertThat(DatafakerRegistry.getCanonicalName(alias)).isEqualTo(canonical);
    assertThat(DatafakerRegistry.generate(alias, FAKER, RANDOM)).isEqualTo("aliased-value");
  }

  @Test
  void shouldNormalizeTypeNamesToLowercase() {
    // Built-in "email" is registered lowercase; querying uppercase should resolve
    assertThat(DatafakerRegistry.isRegistered("EMAIL")).isTrue();
    assertThat(DatafakerRegistry.isRegistered("Name")).isTrue();
  }

  @Test
  void shouldListTypesReturnImmutableSet() {
    Set<String> types = DatafakerRegistry.listTypes();
    assertThat(types).isNotEmpty();
    assertThatThrownBy(() -> types.add("should_fail"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ── Validation ────────────────────────────────────────────────────────────

  @Test
  void shouldThrowWhenRegisteringNullName() {
    assertThatThrownBy(() -> DatafakerRegistry.register(null, (f, r) -> "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Type name cannot be null");
  }

  @Test
  void shouldThrowWhenRegisteringBlankName() {
    assertThatThrownBy(() -> DatafakerRegistry.register("   ", (f, r) -> "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowWhenRegisteringNullFunction() {
    assertThatThrownBy(() -> DatafakerRegistry.register("valid_name", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Function cannot be null");
  }

  @Test
  void shouldThrowWhenRegisteringAliasWithNullAlias() {
    assertThatThrownBy(() -> DatafakerRegistry.registerAlias(null, "target"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Alias cannot be null");
  }

  @Test
  void shouldThrowWhenRegisteringAliasWithNullCanonical() {
    assertThatThrownBy(() -> DatafakerRegistry.registerAlias("alias", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Canonical name cannot be null");
  }

  // ── Instantiation guard ───────────────────────────────────────────────────

  @Test
  void shouldThrowWhenInstantiatedViaReflection() throws Exception {
    var constructor = DatafakerRegistry.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    assertThatThrownBy(constructor::newInstance)
        .cause()
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
