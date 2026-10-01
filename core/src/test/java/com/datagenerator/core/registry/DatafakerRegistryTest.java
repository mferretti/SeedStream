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

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import net.datafaker.Faker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatafakerRegistryTest {

  /** The Datafaker registry is JVM-global: undo this class's custom registrations. */
  @AfterEach
  void resetDatafakerRegistry() {
    DatafakerRegistry.resetToBuiltIns();
  }

  private static final Faker FAKER = new Faker(Locale.US, new Random(42));
  private static final Random RANDOM = new Random(42);
  private static final String STYPE_EMAIL = "email";

  // ── Built-in presence ──────────────────────────────────────────────────────

  @Test
  void shouldHave48BuiltInTypes() {
    assertThat(DatafakerRegistry.listTypes()).hasSizeGreaterThanOrEqualTo(48);
  }

  @Test
  void shouldBeRegisteredForCoreBuiltInTypes() {
    assertThat(DatafakerRegistry.isRegistered("name")).isTrue();
    assertThat(DatafakerRegistry.isRegistered(STYPE_EMAIL)).isTrue();
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
    String value = DatafakerRegistry.generate(STYPE_EMAIL, FAKER, RANDOM);
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
  void shouldResolveBuiltInAliasLatToLatitude() {
    assertThat(DatafakerRegistry.getCanonicalName("lat")).isEqualTo("latitude");
  }

  @Test
  void shouldResolveBuiltInAliasLonToLongitude() {
    assertThat(DatafakerRegistry.getCanonicalName("lon")).isEqualTo("longitude");
  }

  @Test
  void shouldReturnSameNameWhenNoAlias() {
    assertThat(DatafakerRegistry.getCanonicalName(STYPE_EMAIL)).isEqualTo(STYPE_EMAIL);
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

    assertThat(DatafakerRegistry.isRegistered(typeName)).isTrue();
    assertThat(DatafakerRegistry.generate(typeName, FAKER, RANDOM)).isEqualTo("custom-value-42");
  }

  // ── resetToBuiltIns ────────────────────────────────────────────────────────

  @Test
  void shouldRemoveCustomTypesAndAliasesWhenResetToBuiltIns() {
    DatafakerRegistry.register("reset_custom_type", (faker, random) -> "x");
    DatafakerRegistry.registerAlias("reset_custom_alias", "reset_custom_type");

    DatafakerRegistry.resetToBuiltIns();

    assertThat(DatafakerRegistry.isRegistered("reset_custom_type")).isFalse();
    assertThat(DatafakerRegistry.isRegistered("reset_custom_alias")).isFalse();
  }

  @Test
  void shouldRestoreOverwrittenBuiltInsWhenResetToBuiltIns() {
    String original = DatafakerRegistry.generate("name", new Faker(new Random(3)), new Random(3));
    DatafakerRegistry.register("name", (faker, random) -> "overwritten");
    DatafakerRegistry.registerAlias("phone", "name");

    DatafakerRegistry.resetToBuiltIns();

    assertThat(DatafakerRegistry.generate("name", new Faker(new Random(3)), new Random(3)))
        .isEqualTo(original);
    assertThat(DatafakerRegistry.getCanonicalName("phone")).isEqualTo("phone_number");
  }

  @Test
  void shouldKeepEveryBuiltInRegisteredAfterReset() {
    Set<String> before = DatafakerRegistry.listTypes();

    DatafakerRegistry.resetToBuiltIns();

    assertThat(DatafakerRegistry.listTypes())
        .containsAll(before)
        .allMatch(DatafakerRegistry::isBuiltIn);
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
    // Built-in STYPE_EMAIL is registered lowercase; querying uppercase should resolve
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

  // ── Registration collisions (global static state: unique names, no unregister API) ─────

  private static String unique(String prefix) {
    return prefix + "_" + System.nanoTime();
  }

  @Test
  void shouldTreatTypeNamesCaseAndWhitespaceInsensitivelyWhenReRegistering() {
    String name = unique("test_case_collision");
    DatafakerRegistry.register(name.toUpperCase(Locale.ROOT), (f, r) -> "first");
    DatafakerRegistry.register("  " + name + "  ", (f, r) -> "second");

    assertThat(DatafakerRegistry.generate(name, FAKER, RANDOM)).isEqualTo("second");
    assertThat(DatafakerRegistry.listTypes()).containsOnlyOnce(name);
  }

  @Test
  void shouldNormalizeAliasAndCanonicalNamesWhenRegisteringAlias() {
    String canonical = unique("test_norm_canon");
    String alias = unique("test_norm_alias");
    DatafakerRegistry.register(canonical, (f, r) -> "v");
    DatafakerRegistry.registerAlias(
        " " + alias.toUpperCase(Locale.ROOT) + " ", canonical.toUpperCase(Locale.ROOT));

    assertThat(DatafakerRegistry.getCanonicalName(alias)).isEqualTo(canonical);
    assertThat(DatafakerRegistry.generate(alias, FAKER, RANDOM)).isEqualTo("v");
  }

  @Test
  void shouldRepointAliasWhenRegisteredTwice() {
    String first = unique("test_repoint_a");
    String second = unique("test_repoint_b");
    String alias = unique("test_repoint_alias");
    DatafakerRegistry.register(first, (f, r) -> "from-first");
    DatafakerRegistry.register(second, (f, r) -> "from-second");
    DatafakerRegistry.registerAlias(alias, first);
    DatafakerRegistry.registerAlias(alias, second);

    assertThat(DatafakerRegistry.generate(alias, FAKER, RANDOM)).isEqualTo("from-second");
  }

  @Test
  void shouldNotBeRegisteredWhenAliasPointsToUnregisteredCanonical() {
    String alias = unique("test_dangling_alias");
    String canonical = unique("test_dangling_canon");
    DatafakerRegistry.registerAlias(alias, canonical);

    assertThat(DatafakerRegistry.isRegistered(alias)).isFalse();
    assertThatThrownBy(() -> DatafakerRegistry.generate(alias, FAKER, RANDOM))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Type not registered: " + alias);
  }

  @Test
  void shouldNotResolveAliasChainsTransitively() {
    String target = unique("test_chain_target");
    String middle = unique("test_chain_middle");
    String head = unique("test_chain_head");
    DatafakerRegistry.register(target, (f, r) -> "v");
    DatafakerRegistry.registerAlias(middle, target);
    DatafakerRegistry.registerAlias(head, middle);

    assertThat(DatafakerRegistry.getCanonicalName(head)).isEqualTo(middle);
    assertThat(DatafakerRegistry.isRegistered(head)).isFalse();
  }

  @Test
  void shouldOverwriteFunctionRegisteredViaRegexWhenReRegisteredViaRegister() {
    String name = unique("test_kind_overwrite");
    DatafakerRegistry.registerRegex(name, "[A-Z]{5}");
    DatafakerRegistry.register(name, (f, r) -> "plain");

    assertThat(DatafakerRegistry.generate(name, FAKER, RANDOM)).isEqualTo("plain");
  }

  @Test
  void shouldKeepPreviousRegistrationWhenReRegistrationWithInvalidRegexFails() {
    String name = unique("test_failed_reregister");
    DatafakerRegistry.register(name, (f, r) -> "original");

    assertThatThrownBy(() -> DatafakerRegistry.registerRegex(name, "[unterminated"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(DatafakerRegistry.generate(name, FAKER, RANDOM)).isEqualTo("original");
  }

  @Test
  void shouldResolveAliasFirstWhenATypeIsRegisteredUnderAnAliasName() {
    // Documented precedence: aliases resolve before types, so a type registered under an existing
    // alias name is shadowed. User config is protected by CustomTypeConfigLoader, which rejects
    // names colliding with built-ins (isBuiltIn) instead of letting them be silently ignored.
    String canonical = unique("test_shadow_canon");
    String shadowed = unique("test_shadow_alias");
    DatafakerRegistry.register(canonical, (f, r) -> "via-alias");
    DatafakerRegistry.registerAlias(shadowed, canonical);

    DatafakerRegistry.register(shadowed, (f, r) -> "direct");

    assertThat(DatafakerRegistry.generate(shadowed, FAKER, RANDOM)).isEqualTo("via-alias");
  }

  @ParameterizedTest
  @ValueSource(strings = {"phone", "phone_number", "zip", " Phone ", "FIRST_NAME"})
  void shouldReportBuiltInWhenNameIsABuiltInTypeOrAlias(String name) {
    assertThat(DatafakerRegistry.isBuiltIn(name)).isTrue();
  }

  @Test
  void shouldNotReportBuiltInWhenNameWasRegisteredAfterStartup() {
    String custom = unique("test_custom_not_builtin");
    DatafakerRegistry.register(custom, (f, r) -> "x");

    assertThat(DatafakerRegistry.isRegistered(custom)).isTrue();
    assertThat(DatafakerRegistry.isBuiltIn(custom)).isFalse();
    assertThat(DatafakerRegistry.isBuiltIn(null)).isFalse();
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

  // ── Generate all built-in types ───────────────────────────────────────────

  @Test
  void shouldGenerateAllPersonTypes() {
    assertThat(DatafakerRegistry.generate("first_name", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("last_name", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("full_name", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("username", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("title", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("occupation", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("prefix", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("suffix", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("password", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("ssn", FAKER, RANDOM)).isNotBlank();
  }

  @Test
  void shouldGenerateAllAddressTypes() {
    assertThat(DatafakerRegistry.generate("address", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("street_name", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("street_number", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("city", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("state", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("postal_code", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("country", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("longitude", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("country_code", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("time_zone", FAKER, RANDOM)).isNotBlank();
  }

  @Test
  void shouldGenerateAllContactTypes() {
    assertThat(DatafakerRegistry.generate("phone_number", FAKER, RANDOM)).isNotBlank();
  }

  @Test
  void shouldGenerateAllFinanceTypes() {
    assertThat(DatafakerRegistry.generate("company", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("credit_card", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("iban", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("random_iban", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("sepa_iban", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("currency", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("locale_currency", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("price", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("bic", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("random_bic", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("cvv", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("credit_card_type", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("stock_market", FAKER, RANDOM)).isNotBlank();
  }

  // ── Regex patterns (regexify) ──────────────────────────────────────────────

  @Test
  void shouldRegisterRegexTypeAndGenerateMatchingValue() {
    String typeName = "test_regex_" + System.nanoTime();
    DatafakerRegistry.registerRegex(typeName, "[A-Z0-9]{10,35}");
    assertThat(DatafakerRegistry.generate(typeName, FAKER, RANDOM)).matches("[A-Z0-9]{10,35}");
  }

  @Test
  void shouldGenerateRegexValueDeterministicallyForSameSeed() {
    String typeName = "test_regex_det_" + System.nanoTime();
    DatafakerRegistry.registerRegex(typeName, "[A-Z]{5}-\\d{4}");
    String first = DatafakerRegistry.generate(typeName, FAKER, new Random(7));
    String second = DatafakerRegistry.generate(typeName, FAKER, new Random(7));
    assertThat(second).isEqualTo(first);
  }

  @Test
  void shouldThrowWhenRegexPatternIsBlank() {
    assertThatThrownBy(() -> DatafakerRegistry.registerRegex("test_blank_regex", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be null or empty");
  }

  @Test
  void shouldThrowWhenRegexPatternIsInvalid() {
    assertThatThrownBy(() -> DatafakerRegistry.registerRegex("test_bad_regex", "[unterminated"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid regex pattern");
  }

  @Test
  void shouldGenerateSepaIbanFromProvidedCountries() {
    String iban = DatafakerRegistry.sepaIban(FAKER, RANDOM, List.of("IT", "DE", "FR"));
    assertThat(iban).matches("^[A-Z]{2}\\d{2}[A-Z0-9]+$");
    assertThat(iban.substring(0, 2)).isIn("IT", "DE", "FR");
  }

  @Test
  void shouldFallBackToRandomIbanWhenNoSepaCountries() {
    // Defensive branch: empty SEPA∩supported set → random-country IBAN, not an exception.
    String iban = DatafakerRegistry.sepaIban(FAKER, RANDOM, List.of());
    assertThat(iban).matches("^[A-Z]{2}\\d{2}[A-Z0-9]+$");
  }

  @Test
  void shouldGenerateAllInternetTypes() {
    assertThat(DatafakerRegistry.generate("domain", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("url", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("ipv4", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("ipv6", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("mac_address", FAKER, RANDOM)).isNotBlank();
  }

  @Test
  void shouldGenerateAllCommerceTypes() {
    assertThat(DatafakerRegistry.generate("product_name", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("department", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("color", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("material", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("promotion_code", FAKER, RANDOM)).isNotBlank();
  }

  @Test
  void shouldGenerateAllLoremTypes() {
    assertThat(DatafakerRegistry.generate("lorem_word", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("lorem_sentence", FAKER, RANDOM)).isNotBlank();
    assertThat(DatafakerRegistry.generate("lorem_paragraph", FAKER, RANDOM)).isNotBlank();
  }

  @Test
  void shouldGenerateIsbnType() {
    assertThat(DatafakerRegistry.generate("isbn", FAKER, RANDOM)).isNotBlank();
  }

  // ── Instantiation guard ───────────────────────────────────────────────────

  @Test
  @SuppressWarnings({
    "java:S3011",
    "PMD.AvoidAccessibilityAlteration"
  }) // setAccessible is the standard pattern for testing utility-class instantiation guards
  void shouldThrowWhenInstantiatedViaReflection() throws Exception {
    var constructor = DatafakerRegistry.class.getDeclaredConstructor();
    constructor.setAccessible(true); // nosemgrep
    assertThatThrownBy(constructor::newInstance)
        .cause()
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
