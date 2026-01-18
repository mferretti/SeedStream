package com.datagenerator.generators;

import com.datagenerator.core.seed.RandomProvider;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.PrimitiveType;
import java.util.List;
import java.util.Random;

/**
 * Demo showing data generation with all primitive types.
 *
 * <p>Run this to see deterministic generation in action!
 */
public class GeneratorDemo {
  public static void main(String[] args) {
    // Create RandomProvider with seed for reproducibility
    RandomProvider randomProvider = new RandomProvider(42L);
    Random random = randomProvider.getRandom();

    System.out.println("=== Data Generator Demo (seed=42) ===\n");

    // String generation
    PrimitiveType charType = new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "15");
    DataGenerator charGen = DataGeneratorFactory.createStateless(charType);
    System.out.println("Generating 5 strings (char[5..15]):");
    for (int i = 0; i < 5; i++) {
      System.out.println("  " + (i + 1) + ". " + charGen.generate(random, charType));
    }

    // Integer generation
    PrimitiveType intType = new PrimitiveType(PrimitiveType.Kind.INT, "1", "100");
    DataGenerator intGen = DataGeneratorFactory.createStateless(intType);
    System.out.println("\nGenerating 5 integers (int[1..100]):");
    for (int i = 0; i < 5; i++) {
      System.out.println("  " + (i + 1) + ". " + intGen.generate(random, intType));
    }

    // Decimal generation
    PrimitiveType decimalType = new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "100.0");
    DataGenerator decimalGen = DataGeneratorFactory.createStateless(decimalType);
    System.out.println("\nGenerating 5 decimals (decimal[0.0..100.0]):");
    for (int i = 0; i < 5; i++) {
      System.out.println("  " + (i + 1) + ". " + decimalGen.generate(random, decimalType));
    }

    // Boolean generation
    PrimitiveType boolType = new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null);
    DataGenerator boolGen = DataGeneratorFactory.createStateless(boolType);
    System.out.println("\nGenerating 10 booleans:");
    for (int i = 0; i < 10; i++) {
      System.out.print(boolGen.generate(random, boolType) + " ");
    }
    System.out.println();

    // Date generation
    PrimitiveType dateType = new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31");
    DataGenerator dateGen = DataGeneratorFactory.createStateless(dateType);
    System.out.println("\nGenerating 5 dates (date[2020-01-01..2025-12-31]):");
    for (int i = 0; i < 5; i++) {
      System.out.println("  " + (i + 1) + ". " + dateGen.generate(random, dateType));
    }

    // Timestamp generation
    PrimitiveType timestampType =
        new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "2024-01-01T00:00:00Z", "now");
    DataGenerator timestampGen = DataGeneratorFactory.createStateless(timestampType);
    System.out.println("\nGenerating 5 timestamps (timestamp[2024-01-01T00:00:00Z..now]):");
    for (int i = 0; i < 5; i++) {
      System.out.println("  " + (i + 1) + ". " + timestampGen.generate(random, timestampType));
    }

    // Enum generation
    EnumType enumType = new EnumType(List.of("ACTIVE", "INACTIVE", "PENDING", "SUSPENDED"));
    DataGenerator enumGen = DataGeneratorFactory.createStateless(enumType);
    System.out.println("\nGenerating 10 enums (enum[ACTIVE,INACTIVE,PENDING,SUSPENDED]):");
    for (int i = 0; i < 10; i++) {
      System.out.print(enumGen.generate(random, enumType) + " ");
    }
    System.out.println();

    System.out.println("\n=== Demo complete! ===");
    System.out.println("Run again with the same seed (42) to see identical output!");
  }
}
