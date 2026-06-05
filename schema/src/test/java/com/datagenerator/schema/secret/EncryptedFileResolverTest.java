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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.schema.exception.SecretResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptedFileResolverTest {

  private byte[] key;
  private EncryptedFileResolver resolver;

  @BeforeEach
  void setUp() {
    key = new byte[32]; // 32 zero bytes
    resolver = new EncryptedFileResolver(key);
  }

  // ── Happy path ────────────────────────────────────────────────────────────

  @Test
  void resolveDecryptsCorrectly() {
    String plaintext = "my-db-password";
    String encrypted = AesGcmCrypto.encrypt(key, plaintext);
    // path = "enc:" + "AES256GCM:BASE64..."
    String path =
        EncryptedFileResolver.ENC_PREFIX + encrypted.substring(AesGcmCrypto.PREFIX.length());

    assertThat(resolver.resolve(path)).isEqualTo(plaintext);
  }

  @Test
  void resolveHandlesSpecialCharactersInPlaintext() {
    String plaintext = "p@$$w0rd!#%^&*()";
    String encrypted = AesGcmCrypto.encrypt(key, plaintext);
    String path =
        EncryptedFileResolver.ENC_PREFIX + encrypted.substring(AesGcmCrypto.PREFIX.length());

    assertThat(resolver.resolve(path)).isEqualTo(plaintext);
  }

  // ── Error handling ────────────────────────────────────────────────────────

  @Test
  void throwsWhenPathMissingEncPrefix() {
    assertThatThrownBy(() -> resolver.resolve("AES256GCM:somebase64"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("enc:");
  }

  @Test
  void throwsWhenPathIsNull() {
    assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(SecretResolutionException.class);
  }

  @Test
  void throwsOnWrongKey() {
    byte[] wrongKey = new byte[32];
    java.util.Arrays.fill(wrongKey, (byte) 0x01);
    EncryptedFileResolver wrongResolver = new EncryptedFileResolver(wrongKey);

    String encrypted = AesGcmCrypto.encrypt(key, "secret");
    String path =
        EncryptedFileResolver.ENC_PREFIX + encrypted.substring(AesGcmCrypto.PREFIX.length());

    assertThatThrownBy(() -> wrongResolver.resolve(path))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("authentication tag mismatch");
  }

  @Test
  void throwsOnTamperedCiphertext() {
    String encrypted = AesGcmCrypto.encrypt(key, "secret");
    String tampered = encrypted.substring(0, encrypted.length() - 1) + "X";
    String path =
        EncryptedFileResolver.ENC_PREFIX + tampered.substring(AesGcmCrypto.PREFIX.length());

    assertThatThrownBy(() -> resolver.resolve(path)).isInstanceOf(SecretResolutionException.class);
  }

  // ── Constructor validation ────────────────────────────────────────────────

  @Test
  void throwsOnNullKey() {
    assertThatThrownBy(() -> new EncryptedFileResolver(null))
        .isInstanceOf(SecretResolutionException.class);
  }

  @Test
  void throwsOnWrongKeyLength() {
    assertThatThrownBy(() -> new EncryptedFileResolver(new byte[16]))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("32-byte");
  }

  @Test
  void keyIsDefensivelyCopied() {
    byte[] mutableKey = new byte[32];
    EncryptedFileResolver r = new EncryptedFileResolver(mutableKey);

    String encrypted = AesGcmCrypto.encrypt(mutableKey, "value");
    String path =
        EncryptedFileResolver.ENC_PREFIX + encrypted.substring(AesGcmCrypto.PREFIX.length());

    // mutate key after construction — should not affect resolver
    java.util.Arrays.fill(mutableKey, (byte) 0xFF);
    assertThat(r.resolve(path)).isEqualTo("value");
  }
}
