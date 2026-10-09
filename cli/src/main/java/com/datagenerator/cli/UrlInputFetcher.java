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

package com.datagenerator.cli;

import com.datagenerator.inspector.InspectorException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * Downloads an {@code http(s)://} schema document (OpenAPI / JSON Schema) to a local temp file so
 * the existing {@code Path}-based inspectors and format detection work unchanged.
 *
 * <p>Security boundary — the URL is user-supplied and the fetch is server-side. Hardening (SSRF
 * guard, TLS by default, size cap, timeout) lives here, once, for every caller. Mirrors the HTTP +
 * auth pattern of {@code com.datagenerator.core.seed.SeedResolver}.
 *
 * <p>STUB: behaviour is specified by {@code UrlInputFetcherTest}; the body is implemented in phase
 * 2.
 */
public class UrlInputFetcher {

  /** 10 MiB default download cap — a hostile/huge URL must not exhaust memory. */
  static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;

  /** Auth to attach to the request. {@link #NONE} adds no header. */
  public record AuthSpec(
      String type,
      String token,
      String username,
      String password,
      String headerName,
      String headerValue) {
    public static final AuthSpec NONE = new AuthSpec(null, null, null, null, null, null);
  }

  private final HttpClient injectedClient;
  private final long maxBytes;

  public UrlInputFetcher() {
    this(null, DEFAULT_MAX_BYTES);
  }

  /** Test seam: inject an HttpClient and a small size cap. */
  UrlInputFetcher(HttpClient client, long maxBytes) {
    this.injectedClient = client;
    this.maxBytes = maxBytes;
  }

  /**
   * Fetches {@code url} to a temp file and returns its path. Throws {@code InspectorException} on a
   * non-http(s) scheme, a blocked (private/loopback/link-local) host when {@code allowPrivate} is
   * false, a non-2xx response, a redirect, or a body over the size cap.
   */
  public Path fetch(String url, AuthSpec auth, boolean allowPrivate) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new InspectorException("Invalid URL: " + url, e);
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new InspectorException("Unsupported URL scheme (only http/https): " + url);
    }
    if (!allowPrivate) {
      checkNotPrivate(uri.getHost());
    }
    HttpClient client =
        injectedClient != null
            ? injectedClient
            : HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET();
    applyAuth(builder, auth == null ? AuthSpec.NONE : auth);

    try {
      HttpResponse<InputStream> response =
          client.send(builder.build(), BodyHandlers.ofInputStream());
      try (InputStream in = response.body()) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
          throw new InspectorException("Fetching " + url + " failed with HTTP status " + status);
        }
        Path tmp = Files.createTempFile("inspect-", suffixOf(uri.getPath()));
        tmp.toFile().deleteOnExit();
        try {
          copyCapped(in, tmp);
        } catch (IOException | RuntimeException e) {
          Files.deleteIfExists(tmp);
          throw e;
        }
        return tmp;
      }
    } catch (IOException e) {
      throw new InspectorException("Failed to fetch " + url + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InspectorException("Interrupted while fetching " + url, e);
    }
  }

  private void copyCapped(InputStream in, Path target) throws IOException {
    long total = 0;
    byte[] buf = new byte[8192];
    try (OutputStream out = Files.newOutputStream(target)) {
      int n;
      while ((n = in.read(buf)) != -1) {
        total += n;
        if (total > maxBytes) {
          throw new InspectorException("Response exceeds size limit of " + maxBytes + " bytes");
        }
        out.write(buf, 0, n);
      }
    }
  }

  private static void checkNotPrivate(String host) {
    if (host == null) {
      throw new InspectorException("URL has no host");
    }
    try {
      for (InetAddress a : InetAddress.getAllByName(host)) {
        if (a.isLoopbackAddress()
            || a.isLinkLocalAddress()
            || a.isSiteLocalAddress()
            || a.isAnyLocalAddress()
            || a.isMulticastAddress()) {
          throw new InspectorException(
              "Refusing to fetch private/loopback/local address "
                  + a.getHostAddress()
                  + " for host '"
                  + host
                  + "'; pass --allow-private-urls to permit");
        }
      }
    } catch (UnknownHostException e) {
      throw new InspectorException("Cannot resolve host '" + host + "'", e);
    }
  }

  private static String suffixOf(String path) {
    String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
    for (String ext : new String[] {".yaml", ".yml", ".json"}) {
      if (p.endsWith(ext)) {
        return ext;
      }
    }
    return ".tmp";
  }

  private static void applyAuth(HttpRequest.Builder builder, AuthSpec auth) {
    if (auth.type() == null) {
      return;
    }
    switch (auth.type().toLowerCase(Locale.ROOT)) {
      case "bearer" -> {
        require(auth.token(), "--token is required for bearer auth");
        builder.header("Authorization", "Bearer " + auth.token());
      }
      case "basic" -> {
        require(auth.username(), "--username and --password are required for basic auth");
        require(auth.password(), "--username and --password are required for basic auth");
        String enc =
            Base64.getEncoder()
                .encodeToString(
                    (auth.username() + ":" + auth.password()).getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + enc);
      }
      case "api_key" -> {
        require(
            auth.headerName(), "--header-name and --header-value are required for api_key auth");
        require(
            auth.headerValue(), "--header-name and --header-value are required for api_key auth");
        builder.header(auth.headerName(), auth.headerValue());
      }
      default -> throw new InspectorException("Unsupported --auth type: " + auth.type());
    }
  }

  private static void require(String v, String msg) {
    if (v == null) {
      throw new InspectorException(msg);
    }
  }
}
