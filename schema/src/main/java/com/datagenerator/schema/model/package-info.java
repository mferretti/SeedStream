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

/**
 * Model classes representing parsed YAML schemas.
 *
 * <p>This package contains immutable data classes (Lombok @Value) that represent the parsed
 * structure of YAML configuration files. These models are validated using Jakarta Bean Validation.
 *
 * <p><b>Core Models:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.schema.model.DataStructure} - Parsed data structure definition
 *   <li>{@link com.datagenerator.schema.model.FieldDefinition} - Individual field configuration
 *   <li>{@link com.datagenerator.schema.model.JobConfig} - Complete job configuration
 * </ul>
 *
 * <p><b>Design Principles:</b>
 *
 * <ul>
 *   <li><b>Immutability:</b> All models use Lombok @Value for immutable value objects
 *   <li><b>Validation:</b> Jakarta Bean Validation annotations (@NotNull, @Valid, @NotEmpty)
 *   <li><b>Jackson Deserialization:</b> @JsonCreator constructors for YAML parsing
 *   <li><b>Fail Fast:</b> Constraints checked during parsing, not during generation
 * </ul>
 *
 * <p><b>Thread Safety:</b> All model classes are immutable and thread-safe.
 */
package com.datagenerator.schema.model;
