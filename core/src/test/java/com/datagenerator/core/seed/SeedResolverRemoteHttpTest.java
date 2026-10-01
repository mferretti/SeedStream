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

package com.datagenerator.core.seed;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datagenerator.core.exception.SeedResolutionException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Remote-seed resolution against a real loopback HTTP server (no Docker, no mocks of HTTP). */
class SeedResolverRemoteHttpTest {

  private static final String TYPE_REMOTE = "remote";
  private static final String SEED_PATH = "/seed";

  private HttpServer server;
  private final List<Map<String, List<String>>> seenHeaders = new CopyOnWriteArrayList<>();
  private final AtomicInteger hits = new AtomicInteger();
  private final AtomicInteger redirectTargetHits = new AtomicInteger();
  private volatile int status = 200;
  private volatile String body = "42";

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(SEED_PATH, this::handleSeed);
    server.createContext("/redirect", this::handleRedirect);
    server.createContext("/redirect-target", this::handleRedirectTarget);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private void handleSeed(HttpExchange exchange) throws IOException {
    hits.incrementAndGet();
    seenHeaders.add(Map.copyOf(exchange.getRequestHeaders()));
    reply(exchange, status, body);
  }

  private void handleRedirect(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().add("Location", url("/redirect-target"));
    reply(exchange, 302, "moved");
  }

  private void handleRedirectTarget(HttpExchange exchange) throws IOException {
    redirectTargetHits.incrementAndGet();
    reply(exchange, 200, "999");
  }

  private static void reply(HttpExchange exchange, int code, String payload) throws IOException {
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    // length -1 means "no body" (required for 204)
    exchange.sendResponseHeaders(code, code == 204 ? -1 : bytes.length);
    if (code != 204) {
      exchange.getResponseBody().write(bytes);
    }
    exchange.close();
  }

  private String url(String path) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + path;
  }

  private long resolve(SeedConfig.RemoteSeed.AuthConfig auth) {
    return new SeedResolver().resolve(new SeedConfig.RemoteSeed(TYPE_REMOTE, url(SEED_PATH), auth));
  }

  private String headerSeenByServer(String name) {
    // HttpServer normalizes header names to "Xxx-yyy" capitalisation; match case-insensitively.
    return seenHeaders.getLast().entrySet().stream()
        .filter(e -> e.getKey().equalsIgnoreCase(name))
        .map(e -> e.getValue().getFirst())
        .findFirst()
        .orElse(null);
  }

  // ── body formats ─────────────────────────────────────────────────────────

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "42|42",
        "  42  |42",
        "-7|-7",
        "0|0",
        "9223372036854775807|9223372036854775807",
        "-9223372036854775808|-9223372036854775808"
      })
  void shouldParsePlainIntegerBodyWithSurroundingWhitespace(String payload, long expected) {
    body = payload;

    assertThat(resolve(null)).isEqualTo(expected);
  }

  @Test
  void shouldParseIntegerBodyWithTrailingNewline() {
    body = "123456789\n";

    assertThat(resolve(null)).isEqualTo(123456789L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"seed\": 42}",
        "\"42\"",
        "[42]",
        "42.0",
        "1e3",
        "0x2A",
        "forty-two",
        "",
        "   ",
        "9223372036854775808",
        "42 43"
      })
  void shouldRejectNonPlainIntegerBodyWithInvalidSeedMessage(String payload) {
    // Issue #294: only a bare integer is supported (a JSON {"seed": n} envelope is NOT parsed).
    body = payload;

    assertThatThrownBy(() -> resolve(null))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Invalid seed value from remote API")
        .hasMessageContaining(url(SEED_PATH))
        .hasCauseInstanceOf(NumberFormatException.class);
  }

  // ── status codes ─────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(ints = {201, 204, 400, 401, 403, 404, 500, 503})
  void shouldRejectEveryNon200StatusEvenWhenBodyIsAValidSeed(int code) {
    status = code;
    body = "42";

    assertThatThrownBy(() -> resolve(null))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Remote seed API returned status " + code);
  }

  @Test
  void shouldIncludeServerBodyInNon200Message() {
    status = 500;
    body = "database exploded";

    assertThatThrownBy(() -> resolve(null))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("status 500: database exploded");
  }

  @Test
  void shouldNotFollowRedirectToAnotherEndpoint() {
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, url("/redirect"), null);

    assertThatThrownBy(() -> new SeedResolver().resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("status 302");

    assertThat(redirectTargetHits).hasValue(0);
  }

  // ── auth headers over the wire ───────────────────────────────────────────

  @Test
  void shouldSendBearerAuthorizationHeader() {
    resolve(new SeedConfig.RemoteSeed.AuthConfig("bearer", "tok-123", null, null, null, null));

    assertThat(headerSeenByServer("Authorization")).isEqualTo("Bearer tok-123");
  }

  @Test
  void shouldSendBasicAuthorizationHeaderWithUtf8Credentials() {
    resolve(new SeedConfig.RemoteSeed.AuthConfig("basic", null, "ünï", "p:ss", null, null));

    String expected =
        "Basic " + Base64.getEncoder().encodeToString("ünï:p:ss".getBytes(StandardCharsets.UTF_8));
    assertThat(headerSeenByServer("Authorization")).isEqualTo(expected);
  }

  @Test
  void shouldSendApiKeyUnderCustomHeaderNameAndNoAuthorizationHeader() {
    resolve(new SeedConfig.RemoteSeed.AuthConfig("api_key", null, null, null, "X-Api-Key", "k-1"));

    assertThat(headerSeenByServer("X-Api-Key")).isEqualTo("k-1");
    assertThat(headerSeenByServer("Authorization")).isNull();
  }

  @Test
  void shouldSendNoAuthorizationHeaderWhenAuthIsNull() {
    resolve(null);

    assertThat(headerSeenByServer("Authorization")).isNull();
  }

  @Test
  void shouldNotContactServerWhenAuthConfigIsInvalid() {
    SeedConfig.RemoteSeed.AuthConfig bad =
        new SeedConfig.RemoteSeed.AuthConfig("bearer", null, null, null, null, null);

    assertThatThrownBy(() -> resolve(bad))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Bearer token is required");
    assertThat(hits).hasValue(0);
  }

  // ── transport failures ───────────────────────────────────────────────────

  @Test
  void shouldWrapConnectionRefusedInSeedResolutionException() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      closedPort = socket.getLocalPort();
    }
    String deadUrl = "http://127.0.0.1:" + closedPort + SEED_PATH;
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, deadUrl, null);

    assertThatThrownBy(() -> new SeedResolver().resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Failed to fetch seed from remote API: " + deadUrl)
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldWrapRequestTimeoutInSeedResolutionException() throws Exception {
    // The 30s hard timeout is a private constant; the seam is the HttpClient, which signals an
    // expired request timeout with HttpTimeoutException (an IOException).
    HttpClient client = mock(HttpClient.class);
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new HttpTimeoutException("request timed out"));
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, url(SEED_PATH), null);

    assertThatThrownBy(() -> new SeedResolver(client).resolve(config))
        .isInstanceOf(SeedResolutionException.class)
        .hasMessageContaining("Failed to fetch seed")
        .hasCauseInstanceOf(HttpTimeoutException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldRestoreInterruptFlagWhenInterruptedWhileFetching() throws Exception {
    HttpClient client = mock(HttpClient.class);
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("stop"));
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, url(SEED_PATH), null);

    try {
      assertThatThrownBy(() -> new SeedResolver(client).resolve(config))
          .isInstanceOf(SeedResolutionException.class)
          .hasMessageContaining("Failed to fetch seed");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted(); // clear so the flag cannot leak into other tests on this thread
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://example.com/seed", "file:///etc/passwd", "not a url", ""})
  void shouldRejectNonHttpUrlsBeforeAnyRequest(String badUrl) {
    SeedConfig.RemoteSeed config = new SeedConfig.RemoteSeed(TYPE_REMOTE, badUrl, null);

    assertThatThrownBy(() -> new SeedResolver().resolve(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("remote seed URL");
  }
}
