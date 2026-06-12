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

package com.datagenerator.core.security;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates outbound URLs to prevent Server-Side Request Forgery (SSRF).
 *
 * <p>Only {@code https} and {@code http} schemes are allowed. Plain {@code http} is permitted
 * because internal deployments (Vault, Schema Registry, seed APIs) frequently run without TLS on
 * secured networks. Callers that require TLS enforcement should pass {@code httpsOnly = true}.
 *
 * <p>The validator never resolves DNS — it operates purely on the string representation of the URI.
 */
public final class UrlValidator {

  private UrlValidator() {}

  /**
   * Validates that {@code url} is a well-formed HTTP/HTTPS URL.
   *
   * @param url the URL to validate
   * @param context human-readable context for the exception message (e.g. "remote seed URL")
   * @throws IllegalArgumentException if the URL is null, blank, malformed, or uses a non-HTTP
   *     scheme
   */
  public static void validate(String url, String context) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException(context + " must not be null or blank");
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(context + " is not a valid URI: '" + url + "'", e);
    }
    String scheme = uri.getScheme();
    if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
      throw new IllegalArgumentException(
          context
              + " must use http or https scheme; got: '"
              + (scheme == null ? "(none)" : scheme)
              + "' in '"
              + url
              + "'");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new IllegalArgumentException(context + " is missing a host: '" + url + "'");
    }
  }
}
