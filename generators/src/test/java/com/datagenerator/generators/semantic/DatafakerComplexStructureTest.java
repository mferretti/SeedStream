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
  private DataGeneratorFactory factory;
  private StructureRegistry registry;

  @BeforeEach
  void setUp() {
    FakerCache.clear(); // Clear cache to ensure clean state
    // Create registry with mock structure loader
    registry = new StructureRegistry(this::loadMockStructure);
    factory = new DataGeneratorFactory(registry, Paths.get("test"));
  }

  /**
   * Mock structure loader that defines structures inline for testing. In real usage, these would be
   * loaded from YAML files.
   */
  private Map<String, DataType> loadMockStructure(
      String name, Path basePath, StructureRegistry registry) {
    Map<String, DataType> fields = new HashMap<>();

    switch (name) {
      case "passport" -> {
        fields.put("passport_number", new PrimitiveType(PrimitiveType.Kind.CHAR, "8", "9"));
        fields.put("first_name", new PrimitiveType(PrimitiveType.Kind.FIRST_NAME, null, null));
        fields.put("last_name", new PrimitiveType(PrimitiveType.Kind.LAST_NAME, null, null));
        fields.put("full_name", new PrimitiveType(PrimitiveType.Kind.FULL_NAME, null, null));
        fields.put(
            "date_of_birth",
            new PrimitiveType(PrimitiveType.Kind.DATE, "1950-01-01", "2006-12-31"));
        fields.put("nationality", new PrimitiveType(PrimitiveType.Kind.COUNTRY, null, null));
        fields.put("place_of_birth", new PrimitiveType(PrimitiveType.Kind.CITY, null, null));
        fields.put(
            "issue_date", new PrimitiveType(PrimitiveType.Kind.DATE, "2015-01-01", "2024-12-31"));
        fields.put(
            "expiry_date", new PrimitiveType(PrimitiveType.Kind.DATE, "2025-01-01", "2034-12-31"));
        fields.put("issuing_authority", new PrimitiveType(PrimitiveType.Kind.COMPANY, null, null));
        fields.put("sex", new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "1"));
      }
      case "customer" -> {
        fields.put("customer_id", new PrimitiveType(PrimitiveType.Kind.UUID, null, null));
        fields.put("first_name", new PrimitiveType(PrimitiveType.Kind.FIRST_NAME, null, null));
        fields.put("last_name", new PrimitiveType(PrimitiveType.Kind.LAST_NAME, null, null));
        fields.put("email", new PrimitiveType(PrimitiveType.Kind.EMAIL, null, null));
        fields.put("phone", new PrimitiveType(PrimitiveType.Kind.PHONE_NUMBER, null, null));
        fields.put("billing_address", new PrimitiveType(PrimitiveType.Kind.ADDRESS, null, null));
        fields.put("city", new PrimitiveType(PrimitiveType.Kind.CITY, null, null));
        fields.put("state", new PrimitiveType(PrimitiveType.Kind.STATE, null, null));
        fields.put("postal_code", new PrimitiveType(PrimitiveType.Kind.POSTAL_CODE, null, null));
        fields.put("country", new PrimitiveType(PrimitiveType.Kind.COUNTRY, null, null));
      }
      case "transaction_item" -> {
        fields.put("item_id", new PrimitiveType(PrimitiveType.Kind.UUID, null, null));
        fields.put("product_name", new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "50"));
        fields.put("sku", new PrimitiveType(PrimitiveType.Kind.CHAR, "8", "12"));
        fields.put("quantity", new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"));
        fields.put("unit_price", new PrimitiveType(PrimitiveType.Kind.PRICE, null, null));
        fields.put("line_total", new PrimitiveType(PrimitiveType.Kind.PRICE, null, null));
      }
      case "shop_transaction" -> {
        fields.put("transaction_id", new PrimitiveType(PrimitiveType.Kind.UUID, null, null));
        fields.put(
            "timestamp",
            new PrimitiveType(
                PrimitiveType.Kind.TIMESTAMP, "2024-01-01T00:00:00", "2024-12-31T23:59:59"));
        fields.put("customer", new ObjectType("customer"));
        fields.put(
            "items",
            new ArrayType(new ObjectType("transaction_item"), 1, 15)); // Min 1 item, max 15 items
        fields.put("subtotal", new PrimitiveType(PrimitiveType.Kind.PRICE, null, null));
        fields.put("tax", new PrimitiveType(PrimitiveType.Kind.PRICE, null, null));
        fields.put("total", new PrimitiveType(PrimitiveType.Kind.PRICE, null, null));
        fields.put("payment_method", new PrimitiveType(PrimitiveType.Kind.CREDIT_CARD, null, null));
        fields.put("currency", new PrimitiveType(PrimitiveType.Kind.CURRENCY, null, null));
      }
      case "product" -> {
        fields.put("product_id", new PrimitiveType(PrimitiveType.Kind.UUID, null, null));
        fields.put("sku", new PrimitiveType(PrimitiveType.Kind.CHAR, "8", "12"));
        fields.put("product_name", new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "50"));
        fields.put("manufacturer", new PrimitiveType(PrimitiveType.Kind.COMPANY, null, null));
        fields.put("category", new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "30"));
        fields.put("unit_price", new PrimitiveType(PrimitiveType.Kind.PRICE, null, null));
        fields.put("barcode", new PrimitiveType(PrimitiveType.Kind.ISBN, null, null));
      }
      case "store_movement" -> {
        fields.put("movement_id", new PrimitiveType(PrimitiveType.Kind.UUID, null, null));
        fields.put(
            "timestamp",
            new PrimitiveType(
                PrimitiveType.Kind.TIMESTAMP, "2024-01-01T00:00:00", "2024-12-31T23:59:59"));
        fields.put("product", new ObjectType("product"));
        fields.put("movement_type", new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "10"));
        fields.put("quantity", new PrimitiveType(PrimitiveType.Kind.INT, "1", "1000"));
        fields.put("warehouse_location", new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "10"));
        fields.put("from_warehouse", new PrimitiveType(PrimitiveType.Kind.CITY, null, null));
        fields.put("to_warehouse", new PrimitiveType(PrimitiveType.Kind.CITY, null, null));
        fields.put("operator", new PrimitiveType(PrimitiveType.Kind.FULL_NAME, null, null));
        fields.put("notes", new PrimitiveType(PrimitiveType.Kind.CHAR, "0", "100"));
      }
      default -> throw new RuntimeException("Unknown structure: " + name);
    }

    return fields;
  }

  // ==================================================================================
  // PASSPORT DATA GENERATION - Complex structure with dates and semantic types
  // ==================================================================================

  @Test
  @SuppressWarnings("unchecked")
  void shouldGeneratePassportWithAllFields() {
    ObjectType passportType = new ObjectType("passport");
    DataGenerator generator = factory.create(passportType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);
      Map<String, Object> passport = (Map<String, Object>) generator.generate(random, passportType);

      // Verify all passport fields are present
      assertThat(passport).containsKeys("passport_number", "first_name", "last_name", "full_name");
      assertThat(passport).containsKeys("date_of_birth", "nationality", "place_of_birth");
      assertThat(passport).containsKeys("issue_date", "expiry_date", "issuing_authority", "sex");

      // Verify semantic types
      assertThat(passport.get("first_name")).isInstanceOf(String.class);
      assertThat(passport.get("last_name")).isInstanceOf(String.class);
      assertThat(passport.get("full_name")).asString().isNotEmpty();
      assertThat(passport.get("nationality")).asString().isNotEmpty();
      assertThat(passport.get("place_of_birth")).asString().isNotEmpty();

      // Verify dates are present
      assertThat(passport.get("date_of_birth")).isNotNull();
      assertThat(passport.get("issue_date")).isNotNull();
      assertThat(passport.get("expiry_date")).isNotNull();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGeneratePassportsWithDifferentNationalities() {
    ObjectType passportType = new ObjectType("passport");
    DataGenerator generator = factory.create(passportType);

    // Generate passports for different countries
    String[] countries = {"italy", "germany", "france", "japan", "brazil"};

    for (String country : countries) {
      try (var ctx = GeneratorContext.enter(factory, country)) {
        Random random = new Random(12345L);
        Map<String, Object> passport =
            (Map<String, Object>) generator.generate(random, passportType);

        assertThat(passport).as("Passport for " + country).isNotNull();
        assertThat(passport.get("first_name")).as("First name for " + country).isNotNull();
        assertThat(passport.get("nationality")).as("Nationality for " + country).isNotNull();
      }
    }
  }

  // ==================================================================================
  // SHOP TRANSACTION - Nested customer and transaction items array
  // ==================================================================================

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateShopTransactionWithCustomerAndItems() {
    ObjectType transactionType = new ObjectType("shop_transaction");
    DataGenerator generator = factory.create(transactionType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);
      Map<String, Object> transaction =
          (Map<String, Object>) generator.generate(random, transactionType);

      // Verify transaction fields
      assertThat(transaction).containsKeys("transaction_id", "timestamp", "customer", "items");
      assertThat(transaction)
          .containsKeys("subtotal", "tax", "total", "payment_method", "currency");

      // Verify transaction ID is UUID
      assertThat(transaction.get("transaction_id"))
          .asString()
          .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

      // Verify customer object
      Map<String, Object> customer = (Map<String, Object>) transaction.get("customer");
      assertThat(customer).isNotNull();
      assertThat(customer).containsKeys("customer_id", "first_name", "last_name", "email", "phone");
      assertThat(customer.get("email")).asString().contains("@");

      // Verify items array
      List<Map<String, Object>> items = (List<Map<String, Object>>) transaction.get("items");
      assertThat(items).isNotNull();
      assertThat(items).hasSizeGreaterThanOrEqualTo(1).hasSizeLessThanOrEqualTo(15);

      // Verify each item has correct structure
      for (Map<String, Object> item : items) {
        assertThat(item).containsKeys("item_id", "product_name", "sku", "quantity", "unit_price");
        assertThat(item.get("item_id")).asString().matches("^[0-9a-f-]+$");
        assertThat(item.get("quantity")).isInstanceOf(Integer.class);
        assertThat((Integer) item.get("quantity")).isBetween(1, 10);
      }

      // Verify payment method (credit card)
      assertThat(transaction.get("payment_method")).asString().isNotEmpty();

      // Verify currency code
      assertThat(transaction.get("currency")).asString().matches("^[A-Z]{3}$");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateMultipleTransactionsWithDifferentCustomers() {
    ObjectType transactionType = new ObjectType("shop_transaction");
    DataGenerator generator = factory.create(transactionType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      // Generate 5 transactions with different seeds
      for (int i = 0; i < 5; i++) {
        Random random = new Random(i);
        Map<String, Object> transaction =
            (Map<String, Object>) generator.generate(random, transactionType);

        Map<String, Object> customer = (Map<String, Object>) transaction.get("customer");
        assertThat(customer.get("first_name")).isNotNull();
        assertThat(customer.get("email")).asString().contains("@");

        List<Map<String, Object>> items = (List<Map<String, Object>>) transaction.get("items");
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
    ObjectType movementType = new ObjectType("store_movement");
    DataGenerator generator = factory.create(movementType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      Random random = new Random(12345L);
      Map<String, Object> movement = (Map<String, Object>) generator.generate(random, movementType);

      // Verify movement fields
      assertThat(movement).containsKeys("movement_id", "timestamp", "product", "movement_type");
      assertThat(movement)
          .containsKeys("quantity", "warehouse_location", "from_warehouse", "to_warehouse");
      assertThat(movement).containsKeys("operator", "notes");

      // Verify movement ID
      assertThat(movement.get("movement_id")).asString().matches("^[0-9a-f-]+$");

      // Verify product object
      Map<String, Object> product = (Map<String, Object>) movement.get("product");
      assertThat(product).isNotNull();
      assertThat(product).containsKeys("product_id", "sku", "product_name", "manufacturer");
      assertThat(product).containsKeys("category", "unit_price", "barcode");

      // Verify product details
      assertThat(product.get("product_id")).asString().matches("^[0-9a-f-]+$");
      assertThat(product.get("manufacturer")).asString().isNotEmpty();
      assertThat(product.get("barcode")).asString().matches("^\\d{13}$"); // ISBN-13 format as EAN

      // Verify quantity
      assertThat(movement.get("quantity")).isInstanceOf(Integer.class);
      assertThat((Integer) movement.get("quantity")).isBetween(1, 1000);

      // Verify warehouse locations
      assertThat(movement.get("from_warehouse")).asString().isNotEmpty();
      assertThat(movement.get("to_warehouse")).asString().isNotEmpty();

      // Verify operator name
      assertThat(movement.get("operator")).asString().isNotEmpty();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateStoreMovementsWithDifferentProducts() {
    ObjectType movementType = new ObjectType("store_movement");
    DataGenerator generator = factory.create(movementType);

    try (var ctx = GeneratorContext.enter(factory, "usa")) {
      // Generate 5 movements with different seeds
      for (int i = 0; i < 5; i++) {
        Random random = new Random(i * 1000);
        Map<String, Object> movement =
            (Map<String, Object>) generator.generate(random, movementType);

        Map<String, Object> product = (Map<String, Object>) movement.get("product");
        assertThat(product.get("product_name")).isNotNull();
        assertThat(product.get("sku")).asString().hasSizeBetween(8, 12);
        assertThat(product.get("manufacturer")).asString().isNotEmpty();

        assertThat(movement.get("from_warehouse")).isNotNull();
        assertThat(movement.get("to_warehouse")).isNotNull();
      }
    }
  }

  // ==================================================================================
  // DETERMINISM TESTS - Ensure complex structures are deterministic with same seed
  // ==================================================================================

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateDeterministicComplexStructures() {
    ObjectType transactionType = new ObjectType("shop_transaction");
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
      assertThat(transaction1.get("transaction_id")).isEqualTo(transaction2.get("transaction_id"));

      // Verify same customer details
      Map<String, Object> customer1 = (Map<String, Object>) transaction1.get("customer");
      Map<String, Object> customer2 = (Map<String, Object>) transaction2.get("customer");
      assertThat(customer1.get("customer_id")).isEqualTo(customer2.get("customer_id"));
      assertThat(customer1.get("email")).isEqualTo(customer2.get("email"));

      // Verify same number of items
      List<Map<String, Object>> items1 = (List<Map<String, Object>>) transaction1.get("items");
      List<Map<String, Object>> items2 = (List<Map<String, Object>>) transaction2.get("items");
      assertThat(items1).hasSameSizeAs(items2);
    }
  }

  // ==================================================================================
  // CROSS-LOCALE COMPLEX STRUCTURE TESTS
  // ==================================================================================

  @Test
  @SuppressWarnings("unchecked")
  void shouldGenerateTransactionsInMultipleLocales() {
    ObjectType transactionType = new ObjectType("shop_transaction");
    DataGenerator generator = factory.create(transactionType);

    String[] locales = {"usa", "italy", "germany", "france", "japan"};

    for (String locale : locales) {
      try (var ctx = GeneratorContext.enter(factory, locale)) {
        Random random = new Random(12345L);
        Map<String, Object> transaction =
            (Map<String, Object>) generator.generate(random, transactionType);

        Map<String, Object> customer = (Map<String, Object>) transaction.get("customer");

        // Verify customer has locale-appropriate name
        assertThat(customer.get("first_name")).as("Customer first name in " + locale).isNotNull();
        assertThat(customer.get("email"))
            .as("Customer email in " + locale)
            .asString()
            .contains("@");

        // All locales should generate valid transactions
        assertThat(transaction.get("transaction_id")).as("Transaction ID in " + locale).isNotNull();
      }
    }
  }
}
