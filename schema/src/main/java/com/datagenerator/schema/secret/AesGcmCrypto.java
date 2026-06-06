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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption utilities for encrypting config values at rest.
 *
 * <p><b>Wire format:</b> {@code AES256GCM:<base64>} where base64 encodes a 12-byte random IV
 * followed by the ciphertext (which includes the 16-byte GCM authentication tag appended by the
 * JCE).
 *
 * <p><b>Key format:</b> 64 hex characters (32 bytes / 256 bits). Generate with:
 *
 * <pre>
 * openssl rand -hex 32
 * </pre>
 *
 * <p>No external dependencies — uses {@code javax.crypto} from the JDK.
 */
public final class AesGcmCrypto {

  static final String ALGORITHM = "AES/GCM/NoPadding";
  public static final String PREFIX = "AES256GCM:";
  static final int IV_BYTES = 12;
  static final int TAG_BITS = 128;
  public static final int KEY_BYTES = 32;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private AesGcmCrypto() {}

  /**
   * Encrypt a plaintext string.
   *
   * @param key 32-byte AES-256 key
   * @param plaintext value to encrypt
   * @return {@code AES256GCM:<base64(iv + ciphertext)>}
   */
  @SuppressWarnings("java:S3329") // IV is a fresh SecureRandom 12-byte nonce — never reused
  public static String encrypt(byte[] key, String plaintext) {
    validateKey(key);
    try {
      // Fresh 12-byte IV generated via SecureRandom on every call — never reused.
      byte[] iv = new byte[IV_BYTES];
      SECURE_RANDOM.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(
          Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));

      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] payload = new byte[IV_BYTES + ciphertext.length];
      System.arraycopy(iv, 0, payload, 0, IV_BYTES);
      System.arraycopy(ciphertext, 0, payload, IV_BYTES, ciphertext.length);

      return PREFIX + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new SecretResolutionException("Encryption failed", e);
    }
  }

  /**
   * Decrypt a ciphertext produced by {@link #encrypt}.
   *
   * @param key 32-byte AES-256 key
   * @param encoded {@code AES256GCM:<base64(iv + ciphertext)>}
   * @return decrypted plaintext
   * @throws SecretResolutionException if the key is wrong, data is tampered, or format is invalid
   */
  public static String decrypt(byte[] key, String encoded) {
    validateKey(key);
    if (encoded == null || !encoded.startsWith(PREFIX)) {
      throw new SecretResolutionException(
          "Invalid ciphertext format: expected '" + PREFIX + "...'");
    }
    try {
      byte[] payload = Base64.getDecoder().decode(encoded.substring(PREFIX.length()));
      if (payload.length <= IV_BYTES) {
        throw new SecretResolutionException("Ciphertext too short");
      }

      byte[] iv = new byte[IV_BYTES];
      System.arraycopy(payload, 0, iv, 0, IV_BYTES);
      byte[] ciphertext = new byte[payload.length - IV_BYTES];
      System.arraycopy(payload, IV_BYTES, ciphertext, 0, ciphertext.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(
          Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));

      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (SecretResolutionException e) {
      throw e;
    } catch (AEADBadTagException e) {
      throw new SecretResolutionException(
          "Decryption failed: authentication tag mismatch (wrong key or tampered data)", e);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new SecretResolutionException("Decryption failed: " + e.getMessage(), e);
    }
  }

  /**
   * Parse a 64-character hex string into a 32-byte AES key.
   *
   * @param hex 64 hex characters
   * @return 32-byte key
   * @throws SecretResolutionException if the hex string is invalid or not 64 chars
   */
  public static byte[] hexToKey(String hex) {
    if (hex == null || hex.length() != KEY_BYTES * 2) {
      throw new SecretResolutionException(
          "Encryption key must be exactly 64 hex characters (32 bytes); got "
              + (hex == null ? "null" : hex.length() + " characters"));
    }
    try {
      byte[] key = new byte[KEY_BYTES];
      for (int i = 0; i < KEY_BYTES; i++) {
        key[i] = (byte) Integer.parseUnsignedInt(hex.substring(i * 2, i * 2 + 2), 16);
      }
      return key;
    } catch (NumberFormatException e) {
      throw new SecretResolutionException("Encryption key contains non-hex characters", e);
    }
  }

  private static void validateKey(byte[] key) {
    if (key == null || key.length != KEY_BYTES) {
      throw new SecretResolutionException(
          "AES-256 key must be exactly 32 bytes; got " + (key == null ? "null" : key.length));
    }
  }
}
