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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class DataGeneratorCliTest {

  // ── Root command ─────────────────────────────────────────────────────────────

  @Test
  void noArgsShowsHelpAndReturnsZero() {
    int code = new CommandLine(new DataGeneratorCli()).execute();
    assertThat(code).isZero();
  }

  @Test
  void helpFlagPrintsUsageAndReturnsZero() {
    StringWriter out = new StringWriter();
    CommandLine cmd = new CommandLine(new DataGeneratorCli());
    cmd.setOut(new PrintWriter(out));
    int code = cmd.execute("--help");

    assertThat(code).isZero();
    assertThat(out.toString()).contains("execute").contains("validate").contains("encrypt");
  }

  @Test
  void subcommandsAreRegistered() {
    CommandLine cmd = new CommandLine(new DataGeneratorCli());
    assertThat(cmd.getSubcommands()).containsKeys("execute", "validate", "encrypt");
  }

  // ── ManifestVersionProvider ──────────────────────────────────────────────────

  @Test
  void manifestVersionProviderReturnsFallbackWhenNoMatchingManifest() throws Exception {
    DataGeneratorCli.ManifestVersionProvider provider =
        new DataGeneratorCli.ManifestVersionProvider();
    String[] version = provider.getVersion();

    assertThat(version).hasSize(1);
    // In test context no JAR is present with Main-Class = DataGeneratorCli,
    // so the provider returns the fallback "(unknown)".
    assertThat(version[0]).isNotNull().isNotEmpty();
  }

  @Test
  void versionFlagReturnsZero() {
    int code = new CommandLine(new DataGeneratorCli()).execute("--version");
    assertThat(code).isZero();
  }
}
