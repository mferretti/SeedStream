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

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the public constructor (real auth-header building) against a local stub server. */
class HttpSchemaRegistryClientAuthTest {

  private record Seen(String path, String authorization, String contentType, String body) {}

  private HttpServer server;
  private final List<Seen> seen = new CopyOnWriteArrayList<>();
  private final AtomicInteger nextId = new AtomicInteger(100);

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        ex -> {
          String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          seen.add(
              new Seen(
                  ex.getRequestURI().getRawPath(),
                  ex.getRequestHeaders().getFirst("Authorization"),
                  ex.getRequestHeaders().getFirst("Content-Type"),
                  body));
          byte[] resp =
              ("{\"id\":" + nextId.getAndIncrement() + "}").getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, resp.length);
          ex.getResponseBody().write(resp);
          ex.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @Test
  void shouldSendBasicHeaderAsBase64OfUserColonPassword() {
    var client = new HttpSchemaRegistryClient(url(), "basic", "alice:p@ss:w0rdé");

    client.registerSchema("subj", "{\"type\":\"string\"}");

    // base64("alice:p@ss:w0rd" + UTF-8 e-acute)
    assertThat(seen).hasSize(1);
    assertThat(seen.get(0).authorization()).isEqualTo("Basic YWxpY2U6cEBzczp3MHJkw6k=");
  }

  @Test
  void shouldSendBasicHeaderForPlainAsciiCredentials() {
    new HttpSchemaRegistryClient(url(), "basic", "user:pass").registerSchema("s", "{}");

    assertThat(seen.get(0).authorization()).isEqualTo("Basic dXNlcjpwYXNz");
  }

  @Test
  void shouldAcceptAuthTypeCaseInsensitively() {
    new HttpSchemaRegistryClient(url(), "BeArEr", "tok").registerSchema("s", "{}");

    assertThat(seen.get(0).authorization()).isEqualTo("Bearer tok");
  }

  @Test
  void shouldSendBearerHeaderWithRawToken() {
    new HttpSchemaRegistryClient(url(), "bearer", "abc.def.ghi").registerSchema("s", "{}");

    assertThat(seen.get(0).authorization()).isEqualTo("Bearer abc.def.ghi");
  }

  @Test
  void shouldOmitAuthorizationHeaderWhenAuthTypeIsNullOrBlank() {
    new HttpSchemaRegistryClient(url(), (String) null, null).registerSchema("a", "{}");
    new HttpSchemaRegistryClient(url(), "  ", "ignored").registerSchema("b", "{}");

    assertThat(seen).hasSize(2).allSatisfy(s -> assertThat(s.authorization()).isNull());
  }

  @Test
  void shouldPostEncodedSubjectPathContentTypeAndSchemaBody() {
    var client = new HttpSchemaRegistryClient(url() + "/", (String) null, null);

    int id = client.registerSchema("topic/va lue", "{\"type\":\"string\"}");

    Seen s = seen.get(0);
    assertThat(id).isEqualTo(100);
    assertThat(s.path()).isEqualTo("/subjects/topic%2Fva+lue/versions");
    assertThat(s.contentType()).isEqualTo("application/vnd.schemaregistry.v1+json");
    assertThat(s.body()).isEqualTo("{\"schema\":\"{\\\"type\\\":\\\"string\\\"}\"}");
  }

  @Test
  void shouldRejectUnsupportedAuthTypeAndMissingToken() {
    String u = url();
    assertThatThrownBy(() -> new HttpSchemaRegistryClient(u, "digest", "x"))
        .isInstanceOf(SchemaRegistryException.class)
        .hasMessageContaining("digest");
    assertThatThrownBy(() -> new HttpSchemaRegistryClient(u, "basic", " "))
        .isInstanceOf(SchemaRegistryException.class)
        .hasMessageContaining("schema_registry_token");
    assertThatThrownBy(() -> new HttpSchemaRegistryClient(u, "bearer", null))
        .isInstanceOf(SchemaRegistryException.class);
  }

  /**
   * Characterization: the cache is keyed by subject only. Registering a different schema for an
   * already-cached subject returns the stale id with NO HTTP call, so a schema evolution within one
   * client instance is silently never registered.
   */
  @Test
  void shouldReturnStaleIdWithoutHttpCallWhenDifferentSchemaRegisteredForCachedSubject() {
    var client = new HttpSchemaRegistryClient(url(), (String) null, null);
    List<Integer> ids = new ArrayList<>();

    ids.add(client.registerSchema("subj", "{\"type\":\"string\"}"));
    ids.add(client.registerSchema("subj", "{\"type\":\"long\"}"));

    assertThat(ids).containsExactly(100, 100);
    assertThat(seen).hasSize(1);
  }

  @Test
  void shouldRegisterDistinctSubjectsSeparately() {
    var client = new HttpSchemaRegistryClient(url(), (String) null, null);

    assertThat(client.registerSchema("a", "{}")).isEqualTo(100);
    assertThat(client.registerSchema("b", "{}")).isEqualTo(101);
    assertThat(seen).hasSize(2);
  }
}
