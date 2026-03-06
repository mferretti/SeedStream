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
