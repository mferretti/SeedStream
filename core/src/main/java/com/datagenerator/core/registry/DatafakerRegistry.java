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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import net.datafaker.providers.base.Finance;

/**
 * Thread-safe registry for custom Datafaker types. Allows runtime registration of new semantic data
 * types without code changes.
 *
 * <p><b>Built-in Types:</b> 48 pre-registered semantic types (person, address, finance, internet,
 * commerce, etc.)
 *
 * <p><b>Custom Types:</b> Register new types at runtime or via YAML configuration
 *
 * <p><b>Thread Safety:</b> Uses ConcurrentHashMap for lock-free concurrent access
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * // Register custom type
 * DatafakerRegistry.register("pokemon", (faker, random) -> faker.pokemon().name());
 *
 * // Generate value
 * String value = DatafakerRegistry.generate("pokemon", faker, random);
 * </pre>
 */
@Slf4j
public class DatafakerRegistry {
  private static final ConcurrentHashMap<String, DatafakerFunction> registry =
      new ConcurrentHashMap<>();

  // Map aliases to canonical type names
  private static final ConcurrentHashMap<String, String> aliasMap = new ConcurrentHashMap<>();

  private static final String TYPE_POSTAL_CODE = "postal_code";
  private static final String TYPE_LONGITUDE = "longitude";
  private static final String TYPE_PHONE_NUMBER = "phone_number";
  private static final String TYPE_STOCK_MARKET = "stock_market";
  private static final String TYPE_PRODUCT_NAME = "product_name";
  private static final String TYPE_PROMOTION_CODE = "promotion_code";

  /** SEPA-zone countries (ISO 3166-1 alpha-2) that Datafaker can emit an IBAN for. */
  private static final List<String> SEPA_SUPPORTED = sepaSupported();

  /** Functional interface for Datafaker type generators. */
  @FunctionalInterface
  public interface DatafakerFunction {
    /**
     * Generate a value using Datafaker.
     *
     * @param faker Datafaker instance with locale
     * @param random Random instance for deterministic generation
     * @return Generated value
     */
    String generate(Faker faker, Random random);
  }

  // Static initialization - pre-register all built-in semantic types
  static {
    registerBuiltIns();
  }

  /**
   * Register all built-in Datafaker semantic types.
   *
   * <p>This runs once at class load time, making all standard semantic types available immediately.
   */
  private static void registerBuiltIns() {
    // Person types
    register("name", (faker, random) -> faker.name().name());
    register("first_name", (faker, random) -> faker.name().firstName());
    registerAlias("firstname", "first_name");
    register("last_name", (faker, random) -> faker.name().lastName());
    registerAlias("lastname", "last_name");
    register("full_name", (faker, random) -> faker.name().fullName());
    registerAlias("fullname", "full_name");
    register("username", (faker, random) -> faker.credentials().username());
    register("title", (faker, random) -> faker.name().title());
    register("occupation", (faker, random) -> faker.job().title());
    register("prefix", (faker, random) -> faker.name().prefix());
    register("suffix", (faker, random) -> faker.name().suffix());
    register("password", (faker, random) -> faker.credentials().password());
    register("ssn", (faker, random) -> faker.idNumber().valid());

    // Address types
    register("address", (faker, random) -> faker.address().fullAddress());
    register("street_name", (faker, random) -> faker.address().streetName());
    registerAlias("streetname", "street_name");
    register("street_number", (faker, random) -> faker.address().streetAddressNumber());
    registerAlias("streetnumber", "street_number");
    register("city", (faker, random) -> faker.address().city());
    register("state", (faker, random) -> faker.address().state());
    register(TYPE_POSTAL_CODE, (faker, random) -> faker.address().zipCode());
    registerAlias("postalcode", TYPE_POSTAL_CODE);
    registerAlias("zipcode", TYPE_POSTAL_CODE);
    registerAlias("zip", TYPE_POSTAL_CODE);
    register("country", (faker, random) -> faker.address().country());
    register("latitude", (faker, random) -> faker.address().latitude());
    registerAlias("lat", "latitude");
    register(TYPE_LONGITUDE, (faker, random) -> faker.address().longitude());
    registerAlias("lon", TYPE_LONGITUDE);
    registerAlias("lng", TYPE_LONGITUDE);
    registerAlias("long", TYPE_LONGITUDE);
    register("country_code", (faker, random) -> faker.address().countryCode());
    registerAlias("countrycode", "country_code");
    register("time_zone", (faker, random) -> faker.address().timeZone());
    registerAlias("timezone", "time_zone");

    // Contact types
    register("email", (faker, random) -> faker.internet().emailAddress());
    register(TYPE_PHONE_NUMBER, (faker, random) -> faker.phoneNumber().phoneNumber());
    registerAlias("phonenumber", TYPE_PHONE_NUMBER);
    registerAlias("phone", TYPE_PHONE_NUMBER);

    // Finance types
    register("company", (faker, random) -> faker.company().name());
    register("credit_card", (faker, random) -> faker.finance().creditCard());
    registerAlias("creditcard", "credit_card");
    register("iban", (faker, random) -> localeAwareIban(faker));
    register("random_iban", (faker, random) -> faker.finance().iban());
    registerAlias("random_locale_iban", "random_iban");
    register("sepa_iban", DatafakerRegistry::sepaIban);
    register("currency", (faker, random) -> faker.money().currencyCode());
    register("price", (faker, random) -> faker.commerce().price());
    register("bic", (faker, random) -> conformantBic(faker));
    registerAlias("swift", "bic");
    register("cvv", (faker, random) -> String.valueOf(faker.number().numberBetween(100, 999)));
    registerAlias("cvc", "cvv");
    register("credit_card_type", (faker, random) -> faker.finance().creditCard().split(" ")[0]);
    registerAlias("creditcardtype", "credit_card_type");
    register(TYPE_STOCK_MARKET, (faker, random) -> faker.stock().nsdqSymbol());
    registerAlias("stockmarket", TYPE_STOCK_MARKET);
    registerAlias("stock", TYPE_STOCK_MARKET);
    registerAlias("ticker", TYPE_STOCK_MARKET);

    // Internet types
    register("domain", (faker, random) -> faker.internet().domainName());
    register("url", (faker, random) -> faker.internet().url());
    register("ipv4", (faker, random) -> faker.internet().ipV4Address());
    register("ipv6", (faker, random) -> faker.internet().ipV6Address());
    register("mac_address", (faker, random) -> faker.internet().macAddress());
    registerAlias("macaddress", "mac_address");

    // Commerce types
    register(TYPE_PRODUCT_NAME, (faker, random) -> faker.commerce().productName());
    registerAlias("productname", TYPE_PRODUCT_NAME);
    registerAlias("product", TYPE_PRODUCT_NAME);
    register("department", (faker, random) -> faker.commerce().department());
    register("color", (faker, random) -> faker.color().name());
    register("material", (faker, random) -> faker.commerce().material());
    register(TYPE_PROMOTION_CODE, (faker, random) -> faker.commerce().promotionCode());
    registerAlias("promotioncode", TYPE_PROMOTION_CODE);
    registerAlias("promo", TYPE_PROMOTION_CODE);
    registerAlias("coupon", TYPE_PROMOTION_CODE);

    // Text/Lorem types
    register("lorem_word", (faker, random) -> faker.lorem().word());
    registerAlias("loremword", "lorem_word");
    register("lorem_sentence", (faker, random) -> faker.lorem().sentence());
    registerAlias("loremsentence", "lorem_sentence");
    register("lorem_paragraph", (faker, random) -> faker.lorem().paragraph());
    registerAlias("loremparagraph", "lorem_paragraph");

    // Code types
    register("isbn", (faker, random) -> faker.code().isbn13());
    register("uuid", (faker, random) -> faker.internet().uuid());

    log.debug("DatafakerRegistry initialized with {} built-in types", registry.size());
  }

  /**
   * Generates an ISO 9362-conformant BIC. A BIC must be uppercase letters and digits only.
   *
   * <p>Datafaker's {@code Finance.bic()} interpolates the ISO 3166-1 alpha-2 country code
   * (positions 5-6) from locale YAML, where it is stored lowercase, producing non-conformant BICs
   * such as {@code "UFMNmzAVSDD"}. We normalize to uppercase here. This is the correct
   * normalization per ISO 9362 regardless of upstream behavior and is idempotent, so it remains
   * safe if Datafaker fixes the casing.
   *
   * <p>Upstream:
   * https://github.com/datafaker-net/datafaker/commit/d4267942d61314017cba303f98cd607d071409f4
   */
  private static String conformantBic(Faker faker) {
    return faker.finance().bic().toUpperCase(Locale.ROOT);
  }

  /**
   * Generate an IBAN for the Faker's configured locale.
   *
   * <p>Datafaker's no-arg {@code finance().iban()} returns an IBAN from a random country, ignoring
   * the Faker locale. We derive the ISO country from the locale and use the country-scoped overload
   * when Datafaker supports it, so {@code geolocation: italy} yields an {@code IT...} IBAN
   * consistent with the locale-aware name/address/BIC. Falls back to the random-country form when
   * the locale carries no country (language-only) or Datafaker has no IBAN format for it. The
   * {@code random_iban} type preserves the deliberate random-country behavior.
   */
  private static String localeAwareIban(Faker faker) {
    String country = faker.getContext().getLocale().getCountry().toUpperCase(Locale.ROOT);
    if (!country.isBlank() && Finance.ibanSupportedCountries().contains(country)) {
      return faker.finance().iban(country);
    }
    return faker.finance().iban();
  }

  /**
   * Generate an IBAN from a random SEPA-zone country, independent of the Faker locale.
   *
   * <p>Unlike {@code iban} (locale country) and {@code random_iban} (any country worldwide), this
   * restricts the country to the SEPA zone — the correct scope for a SEPA credit-transfer
   * destination account. The country is picked from {@link #SEPA_SUPPORTED} via the seeded {@code
   * random}, keeping generation deterministic. Falls back to a random-country IBAN only in the
   * unlikely case Datafaker supports none of the SEPA countries.
   */
  private static String sepaIban(Faker faker, Random random) {
    if (SEPA_SUPPORTED.isEmpty()) {
      return faker.finance().iban();
    }
    String country = SEPA_SUPPORTED.get(random.nextInt(SEPA_SUPPORTED.size()));
    return faker.finance().iban(country);
  }

  /**
   * SEPA member countries (ISO 3166-1 alpha-2) intersected with the countries Datafaker can emit an
   * IBAN for, so {@link #sepaIban} never calls an unsupported country.
   */
  private static List<String> sepaSupported() {
    Set<String> sepa =
        Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
            "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", // EU27
            "IS", "LI", "NO", // EEA
            "CH", "MC", "SM", "GB", "GI", "AD", "VA"); // other SEPA participants
    return sepa.stream().filter(Finance.ibanSupportedCountries()::contains).sorted().toList();
  }

  /**
   * Register a custom Datafaker type. If the type already exists, it will be overwritten.
   *
   * @param typeName Type name (e.g., "pokemon", "beer_style")
   * @param function Function to generate values
   */
  public static void register(String typeName, DatafakerFunction function) {
    if (typeName == null || typeName.isBlank()) {
      throw new IllegalArgumentException("Type name cannot be null or empty");
    }
    if (function == null) {
      throw new IllegalArgumentException("Function cannot be null");
    }

    String normalized = normalizeTypeName(typeName);
    DatafakerFunction previous = registry.put(normalized, function);

    if (previous != null) {
      log.debug("Overwritten existing type: {}", normalized);
    } else {
      log.debug("Registered new type: {}", normalized);
    }
  }

  /**
   * Register a custom type from a Datafaker method-path expression (e.g. {@code "beer.style"} maps
   * to {@code faker.beer().style()}). Each dot-separated segment is a no-arg method: the first is
   * invoked on the {@link Faker} instance, each subsequent on the previous result, and the final
   * value is rendered via {@code String.valueOf}. The method chain is resolved and validated once
   * at registration so a malformed expression fails fast.
   *
   * @param typeName the SeedStream type key (e.g. {@code beer_style})
   * @param expression dot-separated Datafaker method path (e.g. {@code beer.style})
   * @throws IllegalArgumentException if the expression is blank or names an unknown no-arg method
   */
  public static void registerExpression(String typeName, String expression) {
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException("Datafaker expression cannot be null or empty");
    }
    List<Method> chain = resolveChain(expression.trim());
    register(typeName, (faker, random) -> invokeChain(chain, faker, expression));
  }

  private static List<Method> resolveChain(String expression) {
    List<Method> chain = new ArrayList<>();
    Class<?> current = Faker.class;
    for (String segment : expression.split("\\.")) {
      String methodName = segment.trim();
      if (methodName.isEmpty()) {
        throw new IllegalArgumentException("Invalid Datafaker expression: " + expression);
      }
      try {
        Method method = current.getMethod(methodName);
        chain.add(method);
        current = method.getReturnType();
      } catch (NoSuchMethodException e) {
        throw new IllegalArgumentException(
            "Unknown Datafaker method '" + methodName + "' in expression '" + expression + "'", e);
      }
    }
    return chain;
  }

  private static String invokeChain(List<Method> chain, Faker faker, String expression) {
    Object target = faker;
    try {
      for (Method method : chain) {
        target = method.invoke(target);
      }
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Failed to evaluate Datafaker expression: " + expression, e);
    }
    return String.valueOf(target);
  }

  /**
   * Register an alias for an existing type.
   *
   * @param alias Alias name (e.g., "lat" for "latitude")
   * @param canonicalName Canonical type name (e.g., "latitude")
   */
  public static void registerAlias(String alias, String canonicalName) {
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("Alias cannot be null or empty");
    }
    if (canonicalName == null || canonicalName.isBlank()) {
      throw new IllegalArgumentException("Canonical name cannot be null or empty");
    }

    String normalizedAlias = normalizeTypeName(alias);
    String normalizedCanonical = normalizeTypeName(canonicalName);

    aliasMap.put(normalizedAlias, normalizedCanonical);
    log.debug("Registered alias: {} -> {}", normalizedAlias, normalizedCanonical);
  }

  /**
   * Get the canonical name for a type (resolving aliases).
   *
   * @param typeName Type name or alias
   * @return Canonical type name
   */
  public static String getCanonicalName(String typeName) {
    String normalized = normalizeTypeName(typeName);
    return aliasMap.getOrDefault(normalized, normalized);
  }

  /**
   * Check if a type is registered.
   *
   * @param typeName Type name to check
   * @return true if registered
   */
  public static boolean isRegistered(String typeName) {
    if (typeName == null || typeName.isBlank()) {
      return false;
    }
    String canonical = getCanonicalName(typeName);
    return registry.containsKey(canonical);
  }

  /**
   * Generate a value for a registered type.
   *
   * @param typeName Type name
   * @param faker Datafaker instance
   * @param random Random instance for determinism
   * @return Generated value
   * @throws IllegalArgumentException if type not registered
   */
  public static String generate(String typeName, Faker faker, Random random) {
    String canonical = getCanonicalName(typeName);
    DatafakerFunction function = registry.get(canonical);

    if (function == null) {
      throw new IllegalArgumentException("Type not registered: " + typeName);
    }

    return function.generate(faker, random);
  }

  /**
   * Get all registered type names.
   *
   * @return Set of registered type names
   */
  public static Set<String> listTypes() {
    return Collections.unmodifiableSet(registry.keySet());
  }

  /**
   * Clear all registered types (for testing only).
   *
   * <p><b>WARNING:</b> This removes all types including built-ins. Call {@code registerBuiltIns()}
   * after clearing to restore defaults.
   */
  public static void clear() {
    registry.clear();
    log.debug("DatafakerRegistry cleared");
  }

  /**
   * Normalize type name for consistent lookups (lowercase, trim).
   *
   * @param typeName Type name to normalize
   * @return Normalized type name
   */
  private static String normalizeTypeName(String typeName) {
    return typeName.trim().toLowerCase(Locale.ROOT);
  }

  /** Private constructor to prevent instantiation. */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification = "Utility class pattern; not Serializable, not subject to finalizer attacks")
  private DatafakerRegistry() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }
}
