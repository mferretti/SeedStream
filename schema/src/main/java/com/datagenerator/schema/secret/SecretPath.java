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

package com.datagenerator.schema.secret;

import com.datagenerator.schema.exception.SecretResolutionException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parsed representation of a secret reference path that uses the {@code id#field} syntax.
 *
 * <p>The {@code #field} suffix is optional and names a JSON key to extract from the secret payload.
 * When absent, {@link #field()} returns {@code null} and the raw payload is returned.
 */
record SecretPath(String id, String field) {

  /**
   * Parse a path of the form {@code secretId} or {@code secretId#fieldName}.
   *
   * @param path raw path string from the job config
   * @return parsed record; never {@code null}
   */
  static SecretPath parse(String path) {
    int hash = path.indexOf('#');
    if (hash < 0) return new SecretPath(path, null);
    return new SecretPath(path.substring(0, hash), path.substring(hash + 1));
  }

  /**
   * Extract a named field from a JSON object node.
   *
   * @param root parsed JSON object
   * @param field field name to extract
   * @param context human-readable secret reference for error messages (e.g. "AWS secret: myapp/db")
   * @return field value as a string
   * @throws SecretResolutionException if the field is absent or explicitly null
   */
  static String extractNodeField(JsonNode root, String field, String context) {
    JsonNode node = root.get(field);
    if (node == null || node.isNull()) {
      throw new SecretResolutionException("Field '" + field + "' not found in " + context);
    }
    return node.asText();
  }
}
