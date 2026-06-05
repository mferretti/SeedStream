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

import com.datagenerator.schema.secret.AesGcmCrypto;
import com.datagenerator.schema.secret.EncryptedFileResolver;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class EncryptCommandTest {

  private static final String VALID_KEY_HEX = "0".repeat(64);

  private CommandLine cmd() {
    return new CommandLine(new EncryptCommand());
  }

  // ── Exit codes ────────────────────────────────────────────────────────────

  @Test
  void exitCode0WhenKeyFileProvided(@TempDir Path tempDir) throws Exception {
    Path keyFile = tempDir.resolve("key.hex");
    Files.writeString(keyFile, VALID_KEY_HEX);

    int code = cmd().execute("--key-file", keyFile.toString(), "my-secret");
    assertThat(code).isZero();
  }

  @Test
  void exitCode1WhenKeyEnvNotSet() {
    // use a non-existent env var name
    int code = cmd().execute("--key-env", "NONEXISTENT_TEST_KEY_XYZ_12345", "my-secret");
    assertThat(code).isEqualTo(1);
  }

  @Test
  void exitCode1WhenKeyFileNotFound() {
    int code = cmd().execute("--key-file", "/nonexistent/path/key.hex", "my-secret");
    assertThat(code).isEqualTo(1);
  }

  @Test
  void exitCode0WhenKeyEnvSet() {
    StringWriter out = new StringWriter();
    EncryptCommand command = new EncryptCommand();
    command.envReader = name -> VALID_KEY_HEX;

    CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(out));
    int code = cli.execute("my-secret");

    assertThat(code).isZero();
    assertThat(out.toString().trim()).startsWith(AesGcmCrypto.PREFIX);
  }

  @Test
  void keyFileWithTrailingNewlineSucceeds(@TempDir Path tempDir) throws Exception {
    Path keyFile = tempDir.resolve("key.hex");
    Files.writeString(keyFile, VALID_KEY_HEX + "\n");

    int code = cmd().execute("--key-file", keyFile.toString(), "my-secret");
    assertThat(code).isZero();
  }

  // ── Output format ─────────────────────────────────────────────────────────

  @Test
  void outputStartsWithAes256GcmPrefix(@TempDir Path tempDir) throws Exception {
    Path keyFile = tempDir.resolve("key.hex");
    Files.writeString(keyFile, VALID_KEY_HEX);

    StringWriter out = new StringWriter();
    CommandLine cli = cmd();
    cli.setOut(new PrintWriter(out));
    cli.execute("--key-file", keyFile.toString(), "my-secret");

    assertThat(out.toString().trim()).startsWith(AesGcmCrypto.PREFIX);
  }

  @Test
  void outputCanBeDecryptedBack(@TempDir Path tempDir) throws Exception {
    Path keyFile = tempDir.resolve("key.hex");
    Files.writeString(keyFile, VALID_KEY_HEX);

    StringWriter out = new StringWriter();
    CommandLine cli = cmd();
    cli.setOut(new PrintWriter(out));
    cli.execute("--key-file", keyFile.toString(), "round-trip-value");

    String ciphertext = out.toString().trim();
    byte[] key = AesGcmCrypto.hexToKey(VALID_KEY_HEX);
    String path =
        EncryptedFileResolver.ENC_PREFIX + ciphertext.substring(AesGcmCrypto.PREFIX.length());
    EncryptedFileResolver resolver = new EncryptedFileResolver(key);
    assertThat(resolver.resolve(path)).isEqualTo("round-trip-value");
  }

  @Test
  void twoCiphertextsForSamePlaintextDiffer(@TempDir Path tempDir) throws Exception {
    Path keyFile = tempDir.resolve("key.hex");
    Files.writeString(keyFile, VALID_KEY_HEX);

    StringWriter out1 = new StringWriter();
    StringWriter out2 = new StringWriter();
    CommandLine c1 = cmd();
    c1.setOut(new PrintWriter(out1));
    c1.execute("--key-file", keyFile.toString(), "same-value");

    CommandLine c2 = cmd();
    c2.setOut(new PrintWriter(out2));
    c2.execute("--key-file", keyFile.toString(), "same-value");

    assertThat(out1.toString().trim()).isNotEqualTo(out2.toString().trim());
  }
}
