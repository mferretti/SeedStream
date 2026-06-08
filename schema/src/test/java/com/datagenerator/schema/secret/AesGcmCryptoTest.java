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
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AesGcmCryptoTest {

  private byte[] key;

  @BeforeEach
  void setUp() {
    // 32 zero bytes — a valid 256-bit key for testing
    key = new byte[32];
  }

  // ── Round-trip ────────────────────────────────────────────────────────────

  @Test
  void encryptThenDecryptReturnsOriginalValue() {
    String plaintext = "super-secret-password";
    String ciphertext = AesGcmCrypto.encrypt(key, plaintext);
    assertThat(AesGcmCrypto.decrypt(key, ciphertext)).isEqualTo(plaintext);
  }

  @Test
  void roundTripWithEmptyString() {
    String ciphertext = AesGcmCrypto.encrypt(key, "");
    assertThat(AesGcmCrypto.decrypt(key, ciphertext)).isEmpty();
  }

  @Test
  void roundTripWithUnicodeCharacters() {
    String plaintext = "pässwörð-日本語-🔑";
    String ciphertext = AesGcmCrypto.encrypt(key, plaintext);
    assertThat(AesGcmCrypto.decrypt(key, ciphertext)).isEqualTo(plaintext);
  }

  @Test
  void roundTripWithLongValue() {
    String plaintext = "x".repeat(10_000);
    assertThat(AesGcmCrypto.decrypt(key, AesGcmCrypto.encrypt(key, plaintext)))
        .isEqualTo(plaintext);
  }

  // ── Ciphertext format ─────────────────────────────────────────────────────

  @Test
  void encryptOutputStartsWithPrefix() {
    assertThat(AesGcmCrypto.encrypt(key, "val")).startsWith(AesGcmCrypto.PREFIX);
  }

  @Test
  void samePlaintextProducesDifferentCiphertexts() {
    // random IV must differ each call
    String c1 = AesGcmCrypto.encrypt(key, "same");
    String c2 = AesGcmCrypto.encrypt(key, "same");
    assertThat(c1).isNotEqualTo(c2);
  }

  // ── Wrong key rejected ────────────────────────────────────────────────────

  @Test
  void decryptWithWrongKeyThrows() {
    String ciphertext = AesGcmCrypto.encrypt(key, "secret");
    byte[] wrongKey = new byte[32];
    Arrays.fill(wrongKey, (byte) 0xFF);

    assertThatThrownBy(() -> AesGcmCrypto.decrypt(wrongKey, ciphertext))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("authentication tag mismatch");
  }

  // ── Tampered ciphertext rejected ──────────────────────────────────────────

  @Test
  void decryptWithTamperedCiphertextThrows() {
    String ciphertext = AesGcmCrypto.encrypt(key, "secret");
    // flip last character of base64 payload
    String tampered = ciphertext.substring(0, ciphertext.length() - 1) + "X";

    assertThatThrownBy(() -> AesGcmCrypto.decrypt(key, tampered))
        .isInstanceOf(SecretResolutionException.class);
  }

  @Test
  void decryptWithMissingPrefixThrows() {
    assertThatThrownBy(() -> AesGcmCrypto.decrypt(key, "NOTAES256GCM:something"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("AES256GCM:");
  }

  @Test
  void decryptNullThrows() {
    assertThatThrownBy(() -> AesGcmCrypto.decrypt(key, null))
        .isInstanceOf(SecretResolutionException.class);
  }

  // ── hexToKey ──────────────────────────────────────────────────────────────

  @Test
  void hexToKeyParsesValidHex() {
    String hex = "a".repeat(64);
    byte[] parsed = AesGcmCrypto.hexToKey(hex);
    assertThat(parsed).hasSize(32);
    assertThat(parsed[0]).isEqualTo((byte) 0xAA);
  }

  @Test
  void hexToKeyParsesUppercaseHex() {
    byte[] parsed = AesGcmCrypto.hexToKey("A".repeat(64));
    assertThat(parsed).hasSize(32);
    assertThat(parsed[0]).isEqualTo((byte) 0xAA);
  }

  @Test
  void hexToKeyThrowsOnTooShortHex() {
    assertThatThrownBy(() -> AesGcmCrypto.hexToKey("deadbeef"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("64 hex characters");
  }

  @Test
  void hexToKeyThrowsOnNullInput() {
    assertThatThrownBy(() -> AesGcmCrypto.hexToKey(null))
        .isInstanceOf(SecretResolutionException.class);
  }

  @Test
  void hexToKeyThrowsOnNonHexCharacters() {
    String badHex = "z".repeat(64);
    assertThatThrownBy(() -> AesGcmCrypto.hexToKey(badHex))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("non-hex");
  }

  // ── Key validation in encrypt/decrypt ─────────────────────────────────────

  @Test
  void decryptWithNullKeyThrows() {
    assertThatThrownBy(() -> AesGcmCrypto.decrypt(null, "AES256GCM:somevalue"))
        .isInstanceOf(SecretResolutionException.class);
  }

  @Test
  void encryptWithNullKeyThrows() {
    assertThatThrownBy(() -> AesGcmCrypto.encrypt(null, "val"))
        .isInstanceOf(SecretResolutionException.class);
  }

  @Test
  void encryptWithWrongKeySizeThrows() {
    assertThatThrownBy(() -> AesGcmCrypto.encrypt(new byte[16], "val"))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("32 bytes");
  }
}
