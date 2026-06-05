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

package com.datagenerator.formats.avro;

/**
 * Client for interacting with a Confluent-compatible Schema Registry.
 *
 * <p>Implementations must be thread-safe.
 */
@FunctionalInterface
public interface SchemaRegistryClient {

  /**
   * Register an Avro schema under the given subject, returning the schema ID assigned by the
   * registry. If the schema is already registered, the existing ID is returned.
   *
   * @param subject the subject name (e.g. {@code my-topic-value})
   * @param avroSchemaJson the Avro schema as a JSON string
   * @return the schema ID
   * @throws SchemaRegistryException if the registration request fails
   */
  int registerSchema(String subject, String avroSchemaJson);
}
