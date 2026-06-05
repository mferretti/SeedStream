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

import com.datagenerator.schema.secret.AesGcmCrypto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * CLI subcommand that encrypts a plaintext value with AES-256-GCM for embedding in job YAML.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * # Using an env var key (default):
 * export SEEDSTREAM_ENCRYPTION_KEY=$(openssl rand -hex 32)
 * ./seedstream encrypt "my-db-password"
 *
 * # Using a key file:
 * ./seedstream encrypt --key-file /etc/seedstream/key.hex "my-db-password"
 *
 * # Output (paste into YAML as ${SECRET:enc:AES256GCM:...}):
 * AES256GCM:BASE64CIPHERTEXT...
 * </pre>
 *
 * <p>Exit codes: {@code 0} success, {@code 1} key not found or encryption error.
 */
@Command(
    name = "encrypt",
    description =
        "Encrypt a plaintext value with AES-256-GCM for use in job YAML (${SECRET:enc:AES256GCM:...})",
    mixinStandardHelpOptions = true)
public class EncryptCommand implements Callable<Integer> {

  static final String DEFAULT_KEY_ENV = "SEEDSTREAM_ENCRYPTION_KEY";

  /** Overridable in tests; production always uses {@code System::getenv}. */
  Function<String, String> envReader = System::getenv;

  @Spec CommandSpec spec;

  @Parameters(index = "0", description = "Plaintext value to encrypt")
  String plaintext;

  @Option(
      names = {"--key-env"},
      description =
          "Environment variable name containing the 64-char hex AES-256 key (default: "
              + DEFAULT_KEY_ENV
              + ")",
      defaultValue = DEFAULT_KEY_ENV)
  String keyEnv;

  @Option(
      names = {"--key-file"},
      description = "Path to a file containing the 64-char hex AES-256 key")
  String keyFile;

  @Override
  public Integer call() {
    String keyHex = loadKey();
    if (keyHex == null) {
      return 1;
    }
    try {
      byte[] key = AesGcmCrypto.hexToKey(keyHex);
      spec.commandLine().getOut().println(AesGcmCrypto.encrypt(key, plaintext));
      return 0;
    } catch (Exception e) {
      spec.commandLine().getErr().println("Encryption failed: " + e.getMessage());
      return 1;
    }
  }

  private String loadKey() {
    if (keyFile != null && !keyFile.isBlank()) {
      try {
        return Files.readString(Path.of(keyFile)).trim();
      } catch (IOException e) {
        spec.commandLine()
            .getErr()
            .println("Cannot read key file '" + keyFile + "': " + e.getMessage());
        return null;
      }
    }
    String hex = envReader.apply(keyEnv);
    if (hex == null || hex.isBlank()) {
      spec.commandLine()
          .getErr()
          .println(
              "Error: encryption key not found. Set the "
                  + keyEnv
                  + " environment variable or use --key-file.");
      spec.commandLine().getErr().println("Generate a key with: openssl rand -hex 32");
      return null;
    }
    return hex;
  }
}
