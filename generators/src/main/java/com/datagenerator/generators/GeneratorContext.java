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
  private static final ThreadLocal<Long> JOB_COUNT = new ThreadLocal<>();
  private static final ThreadLocal<java.util.Deque<java.util.Map<String, Object>>>
      PARENT_RECORD_STACK = new ThreadLocal<>();

  private GeneratorContext() {}

  /**
   * Enter a new generator context.
   *
   * @param factory the factory to use for nested generation
   * @param geolocation the geolocation for locale-specific data (can be null)
   * @param jobCount total record count for this job; used to resolve {@code ref[s.f, min..count]}
   * @return AutoCloseable context (use with try-with-resources)
   */
  public static GeneratorContext enter(
      DataGeneratorFactory factory, String geolocation, long jobCount) {
    if (FACTORY.get() != null) {
      throw new IllegalStateException("GeneratorContext already active in this thread");
    }
    FACTORY.set(factory);
    GEOLOCATION.set(geolocation);
    JOB_COUNT.set(jobCount);
    PARENT_RECORD_STACK.set(new java.util.ArrayDeque<>());
    return new GeneratorContext();
  }

  /**
   * Enter a new generator context without a job count (jobCount defaults to 0). Using {@code
   * ref[s.f, min..count]} in this context will throw at generation time.
   *
   * @param factory the factory to use for nested generation
   * @param geolocation the geolocation for locale-specific data (can be null)
   * @return AutoCloseable context (use with try-with-resources)
   */
  public static GeneratorContext enter(DataGeneratorFactory factory, String geolocation) {
    return enter(factory, geolocation, 0L);
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

  /**
   * Get the current job count from context. Used by {@link
   * com.datagenerator.generators.composite.ReferenceGenerator} to resolve {@code ref[s.f,
   * min..count]}.
   *
   * @return the job count for this thread (0 if not set)
   */
  public static long getJobCount() {
    Long count = JOB_COUNT.get();
    return count != null ? count : 0L;
  }

  /**
   * Push a partial record onto the parent-record stack. Called by {@link
   * com.datagenerator.generators.composite.ObjectGenerator} before generating a nested field so
   * that {@code ref[parent.*]} generators can access the enclosing record's already-generated
   * scalar fields.
   *
   * @param record the partial record being built at this nesting level
   */
  public static void pushParentRecord(java.util.Map<String, Object> record) {
    java.util.Deque<java.util.Map<String, Object>> stack = PARENT_RECORD_STACK.get();
    if (stack == null) {
      throw new IllegalStateException(
          "No GeneratorContext active. Call GeneratorContext.enter() before generating.");
    }
    stack.push(record);
  }

  /** Pop the top entry from the parent-record stack after a nested field has been generated. */
  public static void popParentRecord() {
    java.util.Deque<java.util.Map<String, Object>> stack = PARENT_RECORD_STACK.get();
    if (stack != null && !stack.isEmpty()) {
      stack.pop();
    }
  }

  /**
   * Return the top of the parent-record stack without removing it.
   *
   * @return the enclosing parent's partial record, or {@code null} if the stack is empty
   */
  public static java.util.Map<String, Object> peekParentRecord() {
    java.util.Deque<java.util.Map<String, Object>> stack = PARENT_RECORD_STACK.get();
    return (stack != null && !stack.isEmpty()) ? stack.peek() : null;
  }

  /** Check if a context is currently active. */
  public static boolean isActive() {
    return FACTORY.get() != null;
  }

  @Override
  public void close() {
    FACTORY.remove();
    GEOLOCATION.remove();
    JOB_COUNT.remove();
    PARENT_RECORD_STACK.remove();
  }
}
