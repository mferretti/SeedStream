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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpSchemaRegistryClientTest {

  private static final String HEADER_AUTHORIZATION = "Authorization";

  @Mock private HttpClient httpClient;

  @SuppressWarnings("unchecked")
  @Mock
  private HttpResponse<String> response;

  private HttpSchemaRegistryClient client;

  @BeforeEach
  void setUp() {
    client = new HttpSchemaRegistryClient("http://registry:8081", httpClient);
  }

  @SuppressWarnings("unchecked")
  private void stubResponse(int status, String body) throws Exception {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
  }

  // ── Happy path ────────────────────────────────────────────────────────────

  @Test
  void returnsSchemaIdFromRegistryResponse() throws Exception {
    stubResponse(200, "{\"id\":42}");

    assertThat(client.registerSchema("my-subject", "{\"type\":\"string\"}")).isEqualTo(42);
  }

  @Test
  void sendsCorrectContentTypeHeader() throws Exception {
    stubResponse(200, "{\"id\":1}");

    client.registerSchema("subj", "{}");

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    assertThat(captor.getValue().headers().firstValue("Content-Type"))
        .hasValue("application/vnd.schemaregistry.v1+json");
  }

  @Test
  void sendsToCorrectUrl() throws Exception {
    stubResponse(200, "{\"id\":3}");

    client.registerSchema("orders-value", "{}");

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    assertThat(captor.getValue().uri())
        .hasToString("http://registry:8081/subjects/orders-value/versions");
  }

  @Test
  void trailingSlashInBaseUrlIsTrimmed() throws Exception {
    HttpSchemaRegistryClient slashClient =
        new HttpSchemaRegistryClient("http://registry:8081/", httpClient);
    stubResponse(200, "{\"id\":7}");

    slashClient.registerSchema("sub", "{}");

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    assertThat(captor.getValue().uri()).hasToString("http://registry:8081/subjects/sub/versions");
  }

  // ── Caching ───────────────────────────────────────────────────────────────

  @Test
  void secondCallForSameSubjectUsesCache() throws Exception {
    stubResponse(200, "{\"id\":5}");

    client.registerSchema("cached-subject", "{}");
    int id = client.registerSchema("cached-subject", "{}");

    assertThat(id).isEqualTo(5);
    verify(httpClient, times(1)).send(any(), any());
  }

  @Test
  void differentSubjectsAreEachRegistered() throws Exception {
    stubResponse(200, "{\"id\":10}");

    client.registerSchema("subjectA", "{}");
    client.registerSchema("subjectB", "{}");

    verify(httpClient, times(2)).send(any(), any());
  }

  // ── Error handling ────────────────────────────────────────────────────────

  @Test
  void throwsOnNon200Response() throws Exception {
    stubResponse(409, "{\"error_code\":409,\"message\":\"Schema already exists\"}");

    assertThatThrownBy(() -> client.registerSchema("s", "{}"))
        .isInstanceOf(SchemaRegistryException.class)
        .hasMessageContaining("409");
  }

  @Test
  void throwsOnMalformedJsonResponse() throws Exception {
    stubResponse(200, "not-json");

    assertThatThrownBy(() -> client.registerSchema("s", "{}"))
        .isInstanceOf(SchemaRegistryException.class);
  }

  @Test
  void throwsWhenResponseMissingIdField() throws Exception {
    stubResponse(200, "{\"schema\":\"...\"}");

    assertThatThrownBy(() -> client.registerSchema("s", "{}"))
        .isInstanceOf(SchemaRegistryException.class)
        .hasMessageContaining("missing 'id' field");
  }

  @Test
  void wrapsIoExceptionAsSchemaRegistryException() throws Exception {
    when(httpClient.send(any(), any())).thenThrow(new IOException("connection refused"));

    assertThatThrownBy(() -> client.registerSchema("s", "{}"))
        .isInstanceOf(SchemaRegistryException.class)
        .hasMessageContaining("Failed to connect")
        .hasCauseInstanceOf(IOException.class);
  }

  // ── Auth ──────────────────────────────────────────────────────────────────

  @Test
  void bearerAuthHeaderSentWhenConfigured() throws Exception {
    HttpSchemaRegistryClient bearerClient =
        new HttpSchemaRegistryClient("http://registry:8081", httpClient, "Bearer my-token");
    stubResponse(200, "{\"id\":1}");

    bearerClient.registerSchema("subj", "{}");

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    assertThat(captor.getValue().headers().firstValue("Authorization")).hasValue("Bearer my-token");
  }

  @Test
  void basicAuthHeaderSentWhenConfigured() throws Exception {
    // "user:pass" base64 = "dXNlcjpwYXNz"
    HttpSchemaRegistryClient basicClient =
        new HttpSchemaRegistryClient("http://registry:8081", httpClient, "Basic dXNlcjpwYXNz");
    stubResponse(200, "{\"id\":2}");

    basicClient.registerSchema("subj", "{}");

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    assertThat(captor.getValue().headers().firstValue("Authorization"))
        .hasValue("Basic dXNlcjpwYXNz");
  }

  @Test
  void noAuthHeaderSentWhenAuthTypeIsNull() throws Exception {
    stubResponse(200, "{\"id\":2}");

    client.registerSchema("no-auth-subject", "{}");

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    assertThat(captor.getValue().headers().firstValue("Authorization")).isEmpty();
  }

  // ── Constructor validation ────────────────────────────────────────────────

  @Test
  void throwsWhenBaseUrlIsNull() {
    assertThatThrownBy(() -> new HttpSchemaRegistryClient(null, "bearer", "token"))
        .isInstanceOf(SchemaRegistryException.class)
        .hasMessageContaining("schema_registry_url");
  }

  @Test
  void throwsWhenBaseUrlIsBlank() {
    assertThatThrownBy(() -> new HttpSchemaRegistryClient("   ", "bearer", "token"))
        .isInstanceOf(SchemaRegistryException.class)
        .hasMessageContaining("schema_registry_url");
  }
}
