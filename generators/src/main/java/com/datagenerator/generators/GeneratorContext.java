package com.datagenerator.generators;

/**
 * ThreadLocal context for accessing DataGeneratorFactory during nested generation.
 *
 * <p><b>Design Rationale:</b> Composite generators (ArrayGenerator, ObjectGenerator) need access to
 * the factory for recursive generation. Passing factory through all method signatures would pollute
 * the API. ThreadLocal provides clean access without parameter pollution.
 *
 * <p><b>Usage Pattern:</b>
 *
 * <pre>
 * DataGeneratorFactory factory = new DataGeneratorFactory(registry, structuresPath);
 * try (var ctx = GeneratorContext.enter(factory)) {
 *   DataGenerator gen = factory.create(dataType);
 *   Object value = gen.generate(random, dataType); // Nested generators access factory via context
 * }
 * </pre>
 *
 * <p><b>Thread Safety:</b> Each thread has its own context. Automatically cleaned up on close().
 */
public class GeneratorContext implements AutoCloseable {
  private static final ThreadLocal<DataGeneratorFactory> FACTORY = new ThreadLocal<>();

  private GeneratorContext() {}

  /**
   * Enter a new generator context with the given factory.
   *
   * @param factory the factory to use for nested generation
   * @return AutoCloseable context (use with try-with-resources)
   */
  public static GeneratorContext enter(DataGeneratorFactory factory) {
    if (FACTORY.get() != null) {
      throw new IllegalStateException("GeneratorContext already active in this thread");
    }
    FACTORY.set(factory);
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

  /** Check if a context is currently active. */
  public static boolean isActive() {
    return FACTORY.get() != null;
  }

  @Override
  public void close() {
    FACTORY.remove();
  }
}
