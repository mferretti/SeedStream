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

package com.datagenerator.generators;

/**
 * ThreadLocal context for accessing DataGeneratorFactory and geolocation during nested generation.
 *
 * <p><b>Design Rationale:</b> Composite generators (ArrayGenerator, ObjectGenerator) need access to
 * the factory for recursive generation. Semantic type generators (DatafakerGenerator) need
 * geolocation for locale-specific data. ThreadLocal provides clean access without parameter
 * pollution.
 *
 * <p><b>Usage Pattern:</b>
 *
 * <pre>
 * DataGeneratorFactory factory = new DataGeneratorFactory(registry, structuresPath);
 * try (var ctx = GeneratorContext.enter(factory, "italy")) {
 *   DataGenerator gen = factory.create(dataType);
 *   Object value = gen.generate(random, dataType); // Nested generators access factory/geolocation via context
 * }
 * </pre>
 *
 * <p><b>Thread Safety:</b> Each thread has its own context. Automatically cleaned up on close().
 */
public class GeneratorContext implements AutoCloseable {
  private static final ThreadLocal<DataGeneratorFactory> FACTORY = new ThreadLocal<>();
  private static final ThreadLocal<String> GEOLOCATION = new ThreadLocal<>();

  private GeneratorContext() {}

  /**
   * Enter a new generator context with the given factory and geolocation.
   *
   * @param factory the factory to use for nested generation
   * @param geolocation the geolocation for locale-specific data (can be null)
   * @return AutoCloseable context (use with try-with-resources)
   */
  public static GeneratorContext enter(DataGeneratorFactory factory, String geolocation) {
    if (FACTORY.get() != null) {
      throw new IllegalStateException("GeneratorContext already active in this thread");
    }
    FACTORY.set(factory);
    GEOLOCATION.set(geolocation);
    return new GeneratorContext();
  }

  /**
   * Get the current factory from context.
   *
   * @return the factory for this thread
   * @throws IllegalStateException if no context is active
   */
  public static DataGeneratorFactory getFactory() {
    DataGeneratorFactory factory = FACTORY.get();
    if (factory == null) {
      throw new IllegalStateException(
          "No GeneratorContext active. Call GeneratorContext.enter() before generating.");
    }
    return factory;
  }

  /**
   * Get the current geolocation from context.
   *
   * @return the geolocation for this thread (may be null)
   */
  public static String getGeolocation() {
    return GEOLOCATION.get();
  }

  /** Check if a context is currently active. */
  public static boolean isActive() {
    return FACTORY.get() != null;
  }

  @Override
  public void close() {
    FACTORY.remove();
    GEOLOCATION.remove();
  }
}
