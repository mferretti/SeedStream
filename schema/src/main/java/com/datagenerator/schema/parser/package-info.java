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
 * YAML parsers for data structures and job configurations.
 *
 * <p>This package provides parsers that read YAML configuration files and convert them into
 * validated model objects using Jackson YAML and Hibernate Validator.
 *
 * <p><b>Core Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.schema.parser.DataStructureParser} - Parses structure definitions
 *       (*.yaml)
 *   <li>{@link com.datagenerator.schema.parser.JobConfigParser} - Parses job configuration files
 * </ul>
 *
 * <p><b>Parsing Process:</b>
 *
 * <ol>
 *   <li>Read YAML file from filesystem
 *   <li>Deserialize using Jackson YAML mapper
 *   <li>Validate using Hibernate Validator (@NotNull, @Valid, etc.)
 *   <li>Fail fast with descriptive error messages if validation fails
 * </ol>
 *
 * <p><b>Example Data Structure YAML:</b>
 *
 * <pre>
 * name: user
 * geolocation: usa
 * data:
 *   user_id:
 *     datatype: uuid
 *   name:
 *     datatype: name
 *     alias: "full_name"
 *   age:
 *     datatype: int[18..65]
 *   email:
 *     datatype: email
 * </pre>
 *
 * <p><b>Example Job Config YAML:</b>
 *
 * <pre>
 * source: user.yaml
 * type: file
 * seed:
 *   type: embedded
 *   value: 12345
 * conf:
 *   path: output/users
 *   format: json
 *   compress: true
 * </pre>
 *
 * <p><b>Validation:</b> All parsed objects are validated using Jakarta Bean Validation constraints.
 * Validation errors are collected and reported as a single {@link
 * com.datagenerator.schema.exception.SchemaParseException} with all violations listed.
 *
 * <p><b>Thread Safety:</b> Parsers are stateless and thread-safe. Can be shared across threads.
 */
package com.datagenerator.schema.parser;
