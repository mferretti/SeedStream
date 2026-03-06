package com.datagenerator.core;

import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for core module integration tests.
 *
 * <p>Integration tests are tagged with "integration" and run separately from unit tests via the
 * `integrationTest` Gradle task.
 *
 * <p><b>Running integration tests:</b>
 *
 * <pre>
 * ./gradlew integrationTest           # Run all integration tests
 * ./gradlew :core:integrationTest     # Run core integration tests only
 * </pre>
 */
@Tag("integration")
@Testcontainers
public abstract class IntegrationTest {
  // Container declarations and shared setup methods go in subclasses
}
