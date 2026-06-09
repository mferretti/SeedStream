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

package com.datagenerator.generators.semantic;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for complex nested structures with embedded objects, simulating real-world scenarios like
 * passports, shop transactions, and store movements.
 *
 * <p>Note: These tests use mock structure definitions instead of loading real YAML files to avoid
 * dependency on the schema module.
 */
class DatafakerComplexStructureTest {
  private static final String F_FIRST_NAME = "first_name";
  private static final String F_LAST_NAME = "last_name";
  private static final String F_FULL_NAME = "full_name";
  private static final String F_EMAIL = "email";
  private static final String F_TRANSACTION_ID = "transaction_id";
  private static final String F_NATIONALITY = "nationality";
  private static final String F_COUNTRY = "country";
  private static final String F_PRICE = "price";
  private static final String F_CUSTOMER_ID = "customer_id";
  private static final String F_CUSTOMER = "customer";
  private static final String F_ITEMS = "items";
  private static final String F_PRODUCT_NAME = "product_name";
  private static final String F_PRODUCT_ID = "product_id";
  private static final String F_PRODUCT = "product";
  private static final String F_MANUFACTURER = "manufacturer";
  private static final String F_FROM_WAREHOUSE = "from_warehouse";
  private static final String F_TO_WAREHOUSE = "to_warehouse";
  private static final String F_MOVEMENT_ID = "movement_id";
  private static final String F_TIMESTAMP = "timestamp";
  private static final String F_SKU = "sku";
  private static final String F_ITEM_ID = "item_id";
  private static final String F_UNIT_PRICE = "unit_price";
  private static final String F_BARCODE = "barcode";
  private static final String F_OPERATOR = "operator";
  private static final String F_PLACE_OF_BIRTH = "place_of_birth";
  private static final String F_PAYMENT_METHOD = "payment_method";
  private static final String F_CURRENCY = "currency";
  private static final String F_QUANTITY = "quantity";
  private static final String F_SHOP_TRANSACTION = "shop_transaction";
  private static final String F_STORE_MOVEMENT = "store_movement";
  private static final String F_PASSPORT = "passport";
  private static final String F_CITY = "city";
  private static final String F_UUID = "uuid";
  private static final String F_DATE_OF_BIRTH = "date_of_birth";
  private static final String F_ISSUE_DATE = "issue_date";
  private static final String F_EXPIRY_DATE = "expiry_date";
  private static final String UUID_HEX_PATTERN = "^[0-9a-f-]+$";

  private DataGeneratorFactory factory;

  @BeforeEach
  void setUp() {
    FakerCache.clear(); // Clear cache to ensure clean state
    // Create registry with mock structure loader
    StructureRegistry registry = new StructureRegistry(this::loadMockStructure);
    factory = new DataGeneratorFactory(registry, Paths.get("test"));
  }

  /**
   * Mock structure loader that defines structures inline for testing. In real usage, these would be
   * loaded from YAML files.
   */
  private Map<String, DataType> loadMockStructure(
      String name, Path basePath, StructureRegistry reg) {
    Map<String, DataType> fields = new HashMap<>();

    switch (name) {
      case F_PASSPORT -> {
        fields.put("passport_number", new PrimitiveType(PrimitiveType.Kind.CHAR, "8", "9"));
        fields.put(F_FIRST_NAME, new CustomDatafakerType(F_FIRST_NAME));
        fields.put(F_LAST_NAME, new CustomDatafakerType(F_LAST_NAME));
        fields.put(F_FULL_NAME, new CustomDatafakerType(F_FULL_NAME));
        fields.put(
            F_DATE_OF_BIRTH,
            new PrimitiveType(PrimitiveType.Kind.DATE, "1950-01-01", "2006-12-31"));
        fields.put(F_NATIONALITY, new CustomDatafakerType(F_COUNTRY));
        fields.put(F_PLACE_OF_BIRTH, new CustomDatafakerType(F_CITY));
        fields.put(
            F_ISSUE_DATE, new PrimitiveType(PrimitiveType.Kind.DATE, "2015-01-01", "2024-12-31"));
        fields.put(
            F_EXPIRY_DATE, new PrimitiveType(PrimitiveType.Kind.DATE, "2025-01-01", "2034-12-31"));
        fields.put("issuing_authority", new CustomDatafakerType("company"));
        fields.put("sex", new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "1"));
      }
      case F_CUSTOMER -> {
        fields.put(F_CUSTOMER_ID, new CustomDatafakerType(F_UUID));
        fields.put(F_FIRST_NAME, new CustomDatafakerType(F_FIRST_NAME));
        fields.put(F_LAST_NAME, new CustomDatafakerType(F_LAST_NAME));
        fields.put(F_EMAIL, new CustomDatafakerType(F_EMAIL));
        fields.put("phone", new CustomDatafakerType("phone_number"));
        fields.put("billing_address", new CustomDatafakerType("address"));
        fields.put(F_CITY, new CustomDatafakerType(F_CITY));
        fields.put("state", new CustomDatafakerType("state"));
        fields.put("postal_code", new CustomDatafakerType("postal_code"));
        fields.put(F_COUNTRY, new CustomDatafakerType(F_COUNTRY));
      }
      case "transaction_item" -> {
        fields.put(F_ITEM_ID, new CustomDatafakerType(F_UUID));
        fields.put(F_PRODUCT_NAME, new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "50"));
        fields.put(F_SKU, new PrimitiveType(PrimitiveType.Kind.CHAR, "8", "12"));
        fields.put(F_QUANTITY, new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"));
        fields.put(F_UNIT_PRICE, new CustomDatafakerType(F_PRICE));
        fields.put("line_total", new CustomDatafakerType(F_PRICE));
      }
      case F_SHOP_TRANSACTION -> {
        fields.put(F_TRANSACTION_ID, new CustomDatafakerType(F_UUID));
        fields.put(
            F_TIMESTAMP,
            new PrimitiveType(
                PrimitiveType.Kind.TIMESTAMP, "2024-01-01T00:00:00", "2024-12-31T23:59:59"));
        fields.put(F_CUSTOMER, new ObjectType(F_CUSTOMER));
        fields.put(
            F_ITEMS,
            new ArrayType(new ObjectType("transaction_item"), 1, 15)); // Min 1 item, max 15 items
        fields.put("subtotal", new CustomDatafakerType(F_PRICE));
        fields.put("tax", new CustomDatafakerType(F_PRICE));
        fields.put("total", new CustomDatafakerType(F_PRICE));
        fields.put(F_PAYMENT_METHOD, new CustomDatafakerType("credit_card"));
        fields.put(F_CURRENCY, new CustomDatafakerType(F_CURRENCY));
      }
      case F_PRODUCT -> {
        fields.put(F_PRODUCT_ID, new CustomDatafakerType(F_UUID));
        fields.put(F_SKU, new PrimitiveType(PrimitiveType.Kind.CHAR, "8", "12"));
        fields.put(F_PRODUCT_NAME, new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "50"));
        fields.put(F_MANUFACTURER, new CustomDatafakerType("company"));
        fields.put("category", new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "30"));
        fields.put(F_UNIT_PRICE, new CustomDatafakerType(F_PRICE));
        fields.put(F_BARCODE, new CustomDatafakerType("isbn"));
      }
      case F_STORE_MOVEMENT -> {
        fields.put(F_MOVEMENT_ID, new CustomDatafakerType(F_UUID));
        fields.put(
            F_TIMESTAMP,
            new PrimitiveType(
                PrimitiveType.Kind.TIMESTAMP, "2024-01-01T00:00:00", "2024-12-31T23:59:59"));
        fields.put(F_PRODUCT, new ObjectType(F_PRODUCT));
        fields.put("movement_type", new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "10"));
        fields.put(F_QUANTITY, new PrimitiveType(PrimitiveType.Kind.INT, "1", "1000"));
        fields.put("warehouse_location", new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "10"));
        fields.put(F_FROM_WAREHOUSE, new CustomDatafakerType(F_CITY));
        fields.put(F_TO_WAREHOUSE, new CustomDatafakerType(F_CITY));
        fields.put(F_OPERATOR, new CustomDatafakerType(F_FULL_NAME));
        fields.put("notes", new PrimitiveType(PrimitiveType.Kind.CHAR, "0", "100"));
      }
      case null -> throw new IllegalArgumentException("Structure name cannot be null");
      default -> throw new IllegalArgumentException("Unknown structure: " + name);
    }

    return fields;
  }

  // ==================================================================================
  // PASSPORT DATA GENERATION - Complex structure with dates and semantic types
  // ==================================================================================

  @Test
  @SuppressWarnings("unchecked")
  void shouldGeneratePassportWithAllFields() {
    ObjectType passportType = new ObjectType(F_PASSPORT);
    DataGenerator generator = factory.create(passportType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);
      Map<String, Object> passport = (Map<String, Object>) generator.generate(random, passportType);

      // Verify all passport fields are present
      assertThat(passport).containsKeys("passport_number", F_FIRST_NAME, F_LAST_NAME, F_FULL_NAME);
      assertThat(passport).containsKeys(F_DATE_OF_BIRTH, F_NATIONALITY, F_PLACE_OF_BIRTH);
      assertThat(passport).containsKeys(F_ISSUE_DATE, F_EXPIRY_DATE, "issuing_authority", "sex");

      // Verify semantic types
      assertThat(passport.get(F_FIRST_NAME)).isInstanceOf(String.class);
      assertThat(passport.get(F_LAST_NAME)).isInstanceOf(String.class);
      assertThat(passport.get(F_FULL_NAME)).asString().isNotEmpty();
      assertThat(passport.get(F_NATIONALITY)).asString().isNotEmpty();
      assertThat(passport.get(F_PLACE_OF_BIRTH)).asString().isNotEmpty();

      // Verify dates are present
      assertThat(passport.get(F_DATE_OF_BIRTH)).isNotNull();
      assertThat(passport.get(F_ISSUE_DATE)).isNotNull();
      assertThat(passport.get(F_EXPIRY_DATE)).isNotNull();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGeneratePassportsWithDifferentNationalities() {
    ObjectType passportType = new ObjectType(F_PASSPORT);
    DataGenerator generator = factory.create(passportType);

    // Generate passports for different countries
    String[] countries = {"italy", "germany", "france", "japan", "brazil"};

    for (String country : countries) {
      try (var ctx = GeneratorContext.enter(factory, country)) {
        Random random = new Random(12345L);
        Map<String, Object> passport =
            (Map<String, Object>) generator.generate(random, passportType);

        assertThat(passport).as("Passport for " + country).isNotNull();
        assertThat(passport.get(F_FIRST_NAME)).as("First name for " + country).isNotNull();
        assertThat(passport.get(F_NATIONALITY)).as("Nationality for " + country).isNotNull();
      }
    }
  }

  // ==================================================================================
  // SHOP TRANSACTION - Nested customer and transaction items array
  // ==================================================================================

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateShopTransactionWithCustomerAndItems() {
    ObjectType transactionType = new ObjectType(F_SHOP_TRANSACTION);
    DataGenerator generator = factory.create(transactionType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);
      Map<String, Object> transaction =
          (Map<String, Object>) generator.generate(random, transactionType);

      // Verify transaction fields
      assertThat(transaction).containsKeys(F_TRANSACTION_ID, F_TIMESTAMP, F_CUSTOMER, F_ITEMS);
      assertThat(transaction)
          .containsKeys("subtotal", "tax", "total", F_PAYMENT_METHOD, F_CURRENCY);

      // Verify transaction ID is UUID
      assertThat(transaction.get(F_TRANSACTION_ID))
          .asString()
          .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

      // Verify customer object
      Map<String, Object> customer = (Map<String, Object>) transaction.get(F_CUSTOMER);
      assertThat(customer).isNotNull();
      assertThat(customer).containsKeys(F_CUSTOMER_ID, F_FIRST_NAME, F_LAST_NAME, F_EMAIL, "phone");
      assertThat(customer.get(F_EMAIL)).asString().contains("@");

      // Verify items array
      List<Map<String, Object>> items = (List<Map<String, Object>>) transaction.get(F_ITEMS);
      assertThat(items).isNotNull();
      assertThat(items).hasSizeGreaterThanOrEqualTo(1).hasSizeLessThanOrEqualTo(15);

      // Verify each item has correct structure
      for (Map<String, Object> item : items) {
        assertThat(item).containsKeys(F_ITEM_ID, F_PRODUCT_NAME, F_SKU, F_QUANTITY, F_UNIT_PRICE);
        assertThat(item.get(F_ITEM_ID)).asString().matches(UUID_HEX_PATTERN);
        assertThat(item.get(F_QUANTITY)).isInstanceOf(Integer.class);
        assertThat((Integer) item.get(F_QUANTITY)).isBetween(1, 10);
      }

      // Verify payment method (credit card)
      assertThat(transaction.get(F_PAYMENT_METHOD)).asString().isNotEmpty();

      // Verify currency code
      assertThat(transaction.get(F_CURRENCY)).asString().matches("^[A-Z]{3}$");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateMultipleTransactionsWithDifferentCustomers() {
    ObjectType transactionType = new ObjectType(F_SHOP_TRANSACTION);
    DataGenerator generator = factory.create(transactionType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      // Generate 5 transactions with different seeds
      for (int i = 0; i < 5; i++) {
        Random random = new Random(i);
        Map<String, Object> transaction =
            (Map<String, Object>) generator.generate(random, transactionType);

        Map<String, Object> customer = (Map<String, Object>) transaction.get(F_CUSTOMER);
        assertThat(customer.get(F_FIRST_NAME)).isNotNull();
        assertThat(customer.get(F_EMAIL)).asString().contains("@");

        List<Map<String, Object>> items = (List<Map<String, Object>>) transaction.get(F_ITEMS);
        assertThat(items).isNotEmpty();
      }
    }
  }

  // ==================================================================================
  // STORE MOVEMENT - Product tracking with warehouse locations
  // ==================================================================================

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateStoreMovementWithProduct() {
    ObjectType movementType = new ObjectType(F_STORE_MOVEMENT);
    DataGenerator generator = factory.create(movementType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);
      Map<String, Object> movement = (Map<String, Object>) generator.generate(random, movementType);

      // Verify movement fields
      assertThat(movement).containsKeys(F_MOVEMENT_ID, F_TIMESTAMP, F_PRODUCT, "movement_type");
      assertThat(movement)
          .containsKeys(F_QUANTITY, "warehouse_location", F_FROM_WAREHOUSE, F_TO_WAREHOUSE);
      assertThat(movement).containsKeys(F_OPERATOR, "notes");

      // Verify movement ID
      assertThat(movement.get(F_MOVEMENT_ID)).asString().matches(UUID_HEX_PATTERN);

      // Verify product object
      Map<String, Object> product = (Map<String, Object>) movement.get(F_PRODUCT);
      assertThat(product).isNotNull();
      assertThat(product).containsKeys(F_PRODUCT_ID, F_SKU, F_PRODUCT_NAME, F_MANUFACTURER);
      assertThat(product).containsKeys("category", F_UNIT_PRICE, F_BARCODE);

      // Verify product details
      assertThat(product.get(F_PRODUCT_ID)).asString().matches(UUID_HEX_PATTERN);
      assertThat(product.get(F_MANUFACTURER)).asString().isNotEmpty();
      assertThat(product.get(F_BARCODE)).asString().matches("^\\d{13}$"); // ISBN-13 format as EAN

      // Verify quantity
      assertThat(movement.get(F_QUANTITY)).isInstanceOf(Integer.class);
      assertThat((Integer) movement.get(F_QUANTITY)).isBetween(1, 1000);

      // Verify warehouse locations
      assertThat(movement.get(F_FROM_WAREHOUSE)).asString().isNotEmpty();
      assertThat(movement.get(F_TO_WAREHOUSE)).asString().isNotEmpty();

      // Verify operator name
      assertThat(movement.get(F_OPERATOR)).asString().isNotEmpty();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateStoreMovementsWithDifferentProducts() {
    ObjectType movementType = new ObjectType(F_STORE_MOVEMENT);
    DataGenerator generator = factory.create(movementType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      // Generate 5 movements with different seeds
      for (int i = 0; i < 5; i++) {
        Random random = new Random((long) i * 1000);
        Map<String, Object> movement =
            (Map<String, Object>) generator.generate(random, movementType);

        Map<String, Object> product = (Map<String, Object>) movement.get(F_PRODUCT);
        assertThat(product.get(F_PRODUCT_NAME)).isNotNull();
        assertThat(product.get(F_SKU)).asString().hasSizeBetween(8, 12);
        assertThat(product.get(F_MANUFACTURER)).asString().isNotEmpty();

        assertThat(movement.get(F_FROM_WAREHOUSE)).isNotNull();
        assertThat(movement.get(F_TO_WAREHOUSE)).isNotNull();
      }
    }
  }

  // ==================================================================================
  // DETERMINISM TESTS - Ensure complex structures are deterministic with same seed
  // ==================================================================================

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateDeterministicComplexStructures() {
    ObjectType transactionType = new ObjectType(F_SHOP_TRANSACTION);
    DataGenerator generator = factory.create(transactionType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random1 = new Random(99999L);
      Random random2 = new Random(99999L);

      Map<String, Object> transaction1 =
          (Map<String, Object>) generator.generate(random1, transactionType);

      FakerCache.clear(); // Clear cache to allow new Random instance

      Map<String, Object> transaction2 =
          (Map<String, Object>) generator.generate(random2, transactionType);

      // Verify same transaction ID
      assertThat(transaction1).containsEntry(F_TRANSACTION_ID, transaction2.get(F_TRANSACTION_ID));

      // Verify same customer details
      Map<String, Object> customer1 = (Map<String, Object>) transaction1.get(F_CUSTOMER);
      Map<String, Object> customer2 = (Map<String, Object>) transaction2.get(F_CUSTOMER);
      assertThat(customer1).containsEntry(F_CUSTOMER_ID, customer2.get(F_CUSTOMER_ID));
      assertThat(customer1).containsEntry(F_EMAIL, customer2.get(F_EMAIL));

      // Verify same number of items
      List<Map<String, Object>> items1 = (List<Map<String, Object>>) transaction1.get(F_ITEMS);
      List<Map<String, Object>> items2 = (List<Map<String, Object>>) transaction2.get(F_ITEMS);
      assertThat(items1).hasSameSizeAs(items2);
    }
  }

  // ==================================================================================
  // CROSS-LOCALE COMPLEX STRUCTURE TESTS
  // ==================================================================================

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateTransactionsInMultipleLocales() {
    ObjectType transactionType = new ObjectType(F_SHOP_TRANSACTION);
    DataGenerator generator = factory.create(transactionType);

    String[] locales = {"usa", "italy", "germany", "france", "japan"};

    for (String locale : locales) {
      try (var ctx = GeneratorContext.enter(factory, locale)) {
        Random random = new Random(12345L);
        Map<String, Object> transaction =
            (Map<String, Object>) generator.generate(random, transactionType);

        Map<String, Object> customer = (Map<String, Object>) transaction.get(F_CUSTOMER);

        // Verify customer has locale-appropriate name
        assertThat(customer.get(F_FIRST_NAME)).as("Customer first name in " + locale).isNotNull();
        assertThat(customer.get(F_EMAIL))
            .as("Customer email in " + locale)
            .asString()
            .contains("@");

        // All locales should generate valid transactions
        assertThat(transaction.get(F_TRANSACTION_ID)).as("Transaction ID in " + locale).isNotNull();
      }
    }
  }
}
