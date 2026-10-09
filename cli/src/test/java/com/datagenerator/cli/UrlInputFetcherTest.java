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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.cli.UrlInputFetcher.AuthSpec;
import com.datagenerator.inspector.InspectorException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * #198 security contract for {@link UrlInputFetcher}, exercised against a real loopback HTTP server
 * (no mocks of HTTP). The server binds loopback, so happy-path fetches pass {@code
 * allowPrivate=true}; one test asserts that same loopback host is blocked when the flag is off.
 */
class UrlInputFetcherTest {

  private static final String YAML_DOC = "openapi: 3.0.3\ninfo:\n  title: T\n  version: \"1\"\n";
  private static final String JSON_DOC = "{\"$schema\":\"x\",\"type\":\"object\"}";

  private HttpServer server;
  private final List<Map<String, List<String>>> seenHeaders = new CopyOnWriteArrayList<>();

  @BeforeEach
  void startServer() throws IOException {
    var loopback = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0); // nosemgrep
    server = HttpServer.create(loopback, 0); // nosemgrep
    server.createContext("/openapi.yaml", ex -> respond(ex, 200, YAML_DOC));
    server.createContext("/schema.json", ex -> respond(ex, 200, JSON_DOC));
    server.createContext("/spec", ex -> respond(ex, 200, YAML_DOC)); // no file extension
    server.createContext("/big", ex -> respond(ex, 200, "x".repeat(4096)));
    server.createContext("/missing", ex -> respond(ex, 404, "nope"));
    server.createContext(
        "/redirect",
        ex -> {
          ex.getResponseHeaders().add("Location", "/openapi.yaml");
          respond(ex, 302, "");
        });
    server.createContext(
        "/secure",
        ex -> {
          seenHeaders.add(Map.copyOf(ex.getRequestHeaders()));
          respond(ex, 200, YAML_DOC);
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private void respond(HttpExchange ex, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.sendResponseHeaders(status, status == 302 ? -1 : bytes.length);
    if (status != 302) {
      ex.getResponseBody().write(bytes);
    }
    ex.close();
  }

  private String url(String path) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + path;
  }

  private UrlInputFetcher fetcher() {
    return new UrlInputFetcher(null, UrlInputFetcher.DEFAULT_MAX_BYTES);
  }

  // --- download + extension preservation ---

  @Test
  void shouldDownloadDocumentPreservingYamlExtension() throws IOException {
    Path out = fetcher().fetch(url("/openapi.yaml"), AuthSpec.NONE, true);
    assertThat(out.getFileName().toString()).endsWith(".yaml");
    assertThat(Files.readString(out)).isEqualTo(YAML_DOC);
  }

  @Test
  void shouldDownloadDocumentPreservingJsonExtension() throws IOException {
    Path out = fetcher().fetch(url("/schema.json"), AuthSpec.NONE, true);
    assertThat(out.getFileName().toString()).endsWith(".json");
    assertThat(Files.readString(out)).isEqualTo(JSON_DOC);
  }

  @Test
  void shouldDownloadWhenUrlHasNoExtension() throws IOException {
    Path out = fetcher().fetch(url("/spec"), AuthSpec.NONE, true);
    assertThat(Files.readString(out)).isEqualTo(YAML_DOC);
  }

  // --- SSRF guard ---

  @Test
  void shouldRejectLoopbackHostWhenPrivateNotAllowed() {
    assertThatThrownBy(() -> fetcher().fetch(url("/openapi.yaml"), AuthSpec.NONE, false))
        .isInstanceOf(InspectorException.class)
        .satisfies(
            e -> {
              String m = e.getMessage().toLowerCase(java.util.Locale.ROOT);
              assertThat(m).contains("--allow-private-urls");
              assertThat(m).containsAnyOf("private", "loopback", "local");
            });
  }

  @Test
  void shouldRejectNonHttpScheme() {
    assertThatThrownBy(() -> fetcher().fetch("ftp://example.com/spec.yaml", AuthSpec.NONE, true))
        .isInstanceOf(InspectorException.class);
  }

  // --- resource bounds / response validation ---

  @Test
  void shouldRejectBodyExceedingSizeCap() {
    UrlInputFetcher small = new UrlInputFetcher(null, 64);
    assertThatThrownBy(() -> small.fetch(url("/big"), AuthSpec.NONE, true))
        .isInstanceOf(InspectorException.class);
  }

  @Test
  void shouldRejectNon2xxResponse() {
    assertThatThrownBy(() -> fetcher().fetch(url("/missing"), AuthSpec.NONE, true))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("404");
  }

  @Test
  void shouldNotFollowRedirects() {
    // A redirect is not followed (SSRF: a hop could target an internal host). It must fail, not
    // silently fetch the redirect target.
    assertThatThrownBy(() -> fetcher().fetch(url("/redirect"), AuthSpec.NONE, true))
        .isInstanceOf(InspectorException.class);
  }

  // --- auth passthrough (bearer / basic / api_key) ---

  @Test
  void shouldSendBearerAuthorizationHeader() throws IOException {
    fetcher().fetch(url("/secure"), new AuthSpec("bearer", "TKN", null, null, null, null), true);
    assertThat(authHeader("Authorization")).isEqualTo("Bearer TKN");
  }

  @Test
  void shouldSendBasicAuthorizationHeader() throws IOException {
    fetcher()
        .fetch(url("/secure"), new AuthSpec("basic", null, "alice", "s3cret", null, null), true);
    String expected =
        "Basic "
            + Base64.getEncoder().encodeToString("alice:s3cret".getBytes(StandardCharsets.UTF_8));
    assertThat(authHeader("Authorization")).isEqualTo(expected);
  }

  @Test
  void shouldSendApiKeyHeader() throws IOException {
    fetcher()
        .fetch(
            url("/secure"), new AuthSpec("api_key", null, null, null, "X-API-Key", "KEY123"), true);
    assertThat(authHeader("X-API-Key")).isEqualTo("KEY123");
  }

  private String authHeader(String name) {
    assertThat(seenHeaders).as("server received a request").isNotEmpty();
    Map<String, List<String>> headers = seenHeaders.get(seenHeaders.size() - 1);
    // HttpExchange header keys are case-insensitive but normalized to Title-Case; match leniently.
    return headers.entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(name))
        .map(e -> e.getValue().get(0))
        .findFirst()
        .orElse(null);
  }
}
