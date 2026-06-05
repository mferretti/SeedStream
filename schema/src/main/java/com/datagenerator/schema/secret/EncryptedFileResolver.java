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
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves inline encrypted ciphertext embedded directly in job YAML.
 *
 * <p>Intended for teams that commit configs to source control but cannot use a secrets manager.
 * Values are encrypted with AES-256-GCM at authoring time (via {@code seedstream encrypt}) and
 * decrypted at runtime using a key held outside the repository.
 *
 * <p><b>Path syntax:</b> {@code enc:AES256GCM:<base64>} — the portion after {@code ${SECRET:}} in
 * the job YAML. Example:
 *
 * <pre>
 * secrets:
 *   resolver: encrypted_file
 *   key_env: SEEDSTREAM_ENCRYPTION_KEY   # optional; this is the default
 *
 * conf:
 *   password: "${SECRET:enc:AES256GCM:BASE64CIPHERTEXT...}"
 * </pre>
 *
 * <p><b>Key:</b> 64 hex characters (32 bytes). Generate with {@code openssl rand -hex 32}.
 *
 * <p><b>Security:</b> Plaintext values are never logged. Ciphertext paths are logged at TRACE level
 * only.
 */
@Slf4j
public final class EncryptedFileResolver implements SecretResolver {

  public static final String ENC_PREFIX = "enc:";

  private final byte[] key;

  /**
   * @param key 32-byte AES-256 key
   */
  public EncryptedFileResolver(byte[] key) {
    if (key == null || key.length != AesGcmCrypto.KEY_BYTES) {
      throw new SecretResolutionException(
          "EncryptedFileResolver requires a 32-byte key; got "
              + (key == null ? "null" : key.length + " bytes"));
    }
    this.key = key.clone();
  }

  @Override
  public String resolve(String path) {
    log.trace("Resolving encrypted value at path length={}", path == null ? 0 : path.length());
    if (path == null || !path.startsWith(ENC_PREFIX)) {
      throw new SecretResolutionException(
          "EncryptedFileResolver: path must start with 'enc:'; got '"
              + (path == null ? "null" : path.substring(0, Math.min(path.length(), 20)))
              + "'");
    }
    String encoded = AesGcmCrypto.PREFIX + path.substring(ENC_PREFIX.length());
    return AesGcmCrypto.decrypt(key, encoded);
  }
}
