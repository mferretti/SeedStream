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
import org.junit.jupiter.api.Test;

class ConfigSubstitutorTest {

  private final SecretResolver mockResolver = path -> "resolved:" + path;

  @Test
  void shouldReturnNullForNullInput() {
    assertThat(ConfigSubstitutor.substitute(null, mockResolver)).isNull();
  }

  @Test
  void shouldReturnLiteralValueUnchanged() {
    assertThat(ConfigSubstitutor.substitute("plain-value", mockResolver)).isEqualTo("plain-value");
  }

  @Test
  void shouldReturnEmptyStringUnchanged() {
    assertThat(ConfigSubstitutor.substitute("", mockResolver)).isEqualTo("");
  }

  @Test
  void shouldResolveEnvVarSyntaxFromEnvironment() {
    System.setProperty("SEEDSTREAM_TEST_SUBST_VAR", "env-password");
    try {
      assertThat(ConfigSubstitutor.substitute("${SEEDSTREAM_TEST_SUBST_VAR}", mockResolver))
          .isEqualTo("env-password");
    } finally {
      System.clearProperty("SEEDSTREAM_TEST_SUBST_VAR");
    }
  }

  @Test
  void shouldDelegateSecretSyntaxToResolver() {
    assertThat(
            ConfigSubstitutor.substitute("${SECRET:secret/data/myapp/db#password}", mockResolver))
        .isEqualTo("resolved:secret/data/myapp/db#password");
  }

  @Test
  void shouldDelegateSecretSyntaxWithVaultPath() {
    assertThat(ConfigSubstitutor.substitute("${SECRET:kv/data/kafka/api_key}", mockResolver))
        .isEqualTo("resolved:kv/data/kafka/api_key");
  }

  @Test
  void shouldThrowForMissingEnvVar() {
    assertThatThrownBy(
            () -> ConfigSubstitutor.substitute("${SEEDSTREAM_MISSING_ENV_XYZ987}", mockResolver))
        .isInstanceOf(SecretResolutionException.class);
  }

  @Test
  void shouldNotSubstitutePartialPattern() {
    assertThat(ConfigSubstitutor.substitute("${not-closed", mockResolver))
        .isEqualTo("${not-closed");
    assertThat(ConfigSubstitutor.substitute("not-opened}", mockResolver)).isEqualTo("not-opened}");
  }

  @Test
  void shouldResolveEncryptedSecretViaEncryptedFileResolver() {
    byte[] key = new byte[32];
    EncryptedFileResolver encryptedResolver = new EncryptedFileResolver(key);
    String plaintext = "db-password";
    String ciphertext = AesGcmCrypto.encrypt(key, plaintext);
    // ConfigSubstitutor strips "${SECRET:" and "}", passing "enc:AES256GCM:BASE64" to resolve()
    String encPath =
        EncryptedFileResolver.ENC_PREFIX + ciphertext.substring(AesGcmCrypto.PREFIX.length());
    String yamlValue = "${SECRET:" + encPath + "}";

    assertThat(ConfigSubstitutor.substitute(yamlValue, encryptedResolver)).isEqualTo(plaintext);
  }

  @Test
  void shouldUseEnvResolverNotSecretResolverForDollarBracePattern() {
    // ${VAR} always goes to env, regardless of the configured secretResolver
    System.setProperty("SEEDSTREAM_TEST_ENV_ONLY", "from-env");
    try {
      // Even with a custom resolver that would return something different, ${VAR} hits env
      SecretResolver customResolver = path -> "SHOULD-NOT-BE-CALLED";
      assertThat(ConfigSubstitutor.substitute("${SEEDSTREAM_TEST_ENV_ONLY}", customResolver))
          .isEqualTo("from-env");
    } finally {
      System.clearProperty("SEEDSTREAM_TEST_ENV_ONLY");
    }
  }
}
