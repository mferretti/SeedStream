package com.datagenerator.destinations;

import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests using Testcontainers.
 *
 * <p>Integration tests are tagged with "integration" and run separately from unit tests via the
 * `integrationTest` Gradle task.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * public class KafkaDestinationIT extends IntegrationTest {
 *     &#64;Container
 *     static KafkaContainer kafka = new KafkaContainer(
 *         DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
 *     );
 *
 *     &#64;Test
 *     void shouldWriteToKafka() {
 *         // Test implementation using kafka container
 *     }
 * }
 * </pre>
 *
 * <p><b>Running integration tests:</b>
 *
 * <pre>
 * ./gradlew integrationTest           # Run all integration tests
 * ./gradlew :destinations:integrationTest  # Run destination integration tests only
 * </pre>
 *
 * <p><b>Requirements:</b> Docker must be running on the host machine.
 */
@Tag("integration")
@Testcontainers
public abstract class IntegrationTest {
  // Container declarations and shared setup methods go in subclasses
  // This base class provides consistent tagging and Testcontainers configuration
}
