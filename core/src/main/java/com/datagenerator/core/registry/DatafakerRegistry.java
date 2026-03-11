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
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

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
    register("postal_code", (faker, random) -> faker.address().zipCode());
    registerAlias("postalcode", "postal_code");
    registerAlias("zipcode", "postal_code");
    registerAlias("zip", "postal_code");
    register("country", (faker, random) -> faker.address().country());
    register("latitude", (faker, random) -> faker.address().latitude());
    registerAlias("lat", "latitude");
    register("longitude", (faker, random) -> faker.address().longitude());
    registerAlias("lon", "longitude");
    registerAlias("lng", "longitude");
    registerAlias("long", "longitude");
    register("country_code", (faker, random) -> faker.address().countryCode());
    registerAlias("countrycode", "country_code");
    register("time_zone", (faker, random) -> faker.address().timeZone());
    registerAlias("timezone", "time_zone");

    // Contact types
    register("email", (faker, random) -> faker.internet().emailAddress());
    register("phone_number", (faker, random) -> faker.phoneNumber().phoneNumber());
    registerAlias("phonenumber", "phone_number");
    registerAlias("phone", "phone_number");

    // Finance types
    register("company", (faker, random) -> faker.company().name());
    register("credit_card", (faker, random) -> faker.finance().creditCard());
    registerAlias("creditcard", "credit_card");
    register("iban", (faker, random) -> faker.finance().iban());
    register("currency", (faker, random) -> faker.money().currencyCode());
    register("price", (faker, random) -> faker.commerce().price());
    register("bic", (faker, random) -> faker.finance().bic());
    registerAlias("swift", "bic");
    register("cvv", (faker, random) -> String.valueOf(faker.number().numberBetween(100, 999)));
    registerAlias("cvc", "cvv");
    register("credit_card_type", (faker, random) -> faker.finance().creditCard().split(" ")[0]);
    registerAlias("creditcardtype", "credit_card_type");
    register("stock_market", (faker, random) -> faker.stock().nsdqSymbol());
    registerAlias("stockmarket", "stock_market");
    registerAlias("stock", "stock_market");
    registerAlias("ticker", "stock_market");

    // Internet types
    register("domain", (faker, random) -> faker.internet().domainName());
    register("url", (faker, random) -> faker.internet().url());
    register("ipv4", (faker, random) -> faker.internet().ipV4Address());
    register("ipv6", (faker, random) -> faker.internet().ipV6Address());
    register("mac_address", (faker, random) -> faker.internet().macAddress());
    registerAlias("macaddress", "mac_address");

    // Commerce types
    register("product_name", (faker, random) -> faker.commerce().productName());
    registerAlias("productname", "product_name");
    registerAlias("product", "product_name");
    register("department", (faker, random) -> faker.commerce().department());
    register("color", (faker, random) -> faker.color().name());
    register("material", (faker, random) -> faker.commerce().material());
    register("promotion_code", (faker, random) -> faker.commerce().promotionCode());
    registerAlias("promotioncode", "promotion_code");
    registerAlias("promo", "promotion_code");
    registerAlias("coupon", "promotion_code");

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
    return Set.copyOf(registry.keySet());
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
