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

package com.datagenerator.schema.parser;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.seed.SeedConfig;
import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.JobConfig;
import com.datagenerator.schema.model.SecretsConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JobConfigParserHardeningTest {
  private static final String MIN_TAIL = "conf:\n  k: v\n";
  private static final String HEAD = "source: a.yaml\ntype: file\n";

  private final JobConfigParser parser = new JobConfigParser();

  @TempDir Path tempDir;

  private Path write(String content) throws IOException {
    return Files.writeString(tempDir.resolve("j.yaml"), content);
  }

  private JobConfig parse(String content) throws IOException {
    return parser.parse(write(content));
  }

  // ---- required fields ----

  @Test
  void shouldNameOnlySourceWhenSourceMissing() throws Exception {
    Path f = write("type: file\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Validation failed")
        .hasMessageContaining("source: must not be null")
        .hasMessageNotContaining("type:")
        .hasMessageNotContaining("conf:");
  }

  @Test
  void shouldNameOnlyTypeWhenTypeMissing() throws Exception {
    Path f = write("source: a.yaml\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("type: must not be null")
        .hasMessageNotContaining("source:")
        .hasMessageNotContaining("conf:");
  }

  @Test
  void shouldNameOnlyConfWhenConfMissing() throws Exception {
    Path f = write(HEAD);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("conf: must not be null")
        .hasMessageNotContaining("source:")
        .hasMessageNotContaining("type:");
  }

  @Test
  void shouldNameAllThreeWhenAllRequiredFieldsMissing() throws Exception {
    Path f = write("structures_path: x\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("source: must not be null")
        .hasMessageContaining("type: must not be null")
        .hasMessageContaining("conf: must not be null");
  }

  @Test
  void shouldNameSeedPathWhenFileSeedPathMissing() throws Exception {
    Path f = write(HEAD + "seed:\n  type: file\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("seed.path: must not be null");
  }

  @Test
  void shouldNameSeedNameWhenEnvSeedNameMissing() throws Exception {
    Path f = write(HEAD + "seed:\n  type: env\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("seed.name: must not be null");
  }

  @Test
  void shouldNameSeedUrlWhenRemoteSeedUrlMissing() throws Exception {
    Path f = write(HEAD + "seed:\n  type: remote\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("seed.url: must not be null");
  }

  @Test
  void shouldNameAuthTypeWhenRemoteAuthTypeMissing() throws Exception {
    Path f =
        write(HEAD + "seed:\n  type: remote\n  url: http://h\n  auth:\n    token: t\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("seed.auth.type: must not be null");
  }

  @Test
  void shouldFailWithTypeIdErrorWhenSeedTypeMissing() throws Exception {
    Path f = write(HEAD + "seed:\n  value: 1\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read job config")
        .hasMessageContaining(f.toString())
        .hasCauseInstanceOf(JsonProcessingException.class)
        .cause()
        .hasMessageContaining("type");
  }

  @Test
  void shouldFailNamingUnknownSeedTypeWhenSeedTypeUnknown() throws Exception {
    Path f = write(HEAD + "seed:\n  type: vault\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .cause()
        .hasMessageContaining("vault");
  }

  // ---- wrong types ----

  @Test
  void shouldFailWhenSourceIsMapInsteadOfScalar() throws Exception {
    Path f = write("source:\n  a: b\ntype: file\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read job config")
        .hasCauseInstanceOf(JsonProcessingException.class)
        .cause()
        .hasMessageContaining("source");
  }

  @Test
  void shouldFailWhenTypeIsListInsteadOfScalar() throws Exception {
    Path f = write("source: a.yaml\ntype: [file, kafka]\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .cause()
        .hasMessageContaining("type");
  }

  @Test
  void shouldFailWhenSeedIsScalarInsteadOfMap() throws Exception {
    Path f = write(HEAD + "seed: 42\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining(f.toString())
        .hasCauseInstanceOf(JsonProcessingException.class);
  }

  @Test
  void shouldFailWhenSecretsIsListInsteadOfMap() throws Exception {
    Path f = write(HEAD + "secrets: [vault]\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .cause()
        .hasMessageContaining("SecretsConfig");
  }

  @Test
  void shouldFailWhenEmbeddedSeedValueIsNotANumber() throws Exception {
    Path f = write(HEAD + "seed:\n  type: embedded\n  value: abc\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .cause()
        .hasMessageContaining("abc");
  }

  @Test
  void shouldCoerceNumericSourceToStringWhenSourceIsNumber() throws Exception {
    assertThat(parse("source: 123\ntype: file\n" + MIN_TAIL).getSource()).isEqualTo("123");
  }

  @Test
  void shouldRejectScalarConfWhenConfIsString() throws Exception {
    // Destinations read named settings from conf: a scalar is unusable (#383).
    Path f = write(HEAD + "conf: just-a-string\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("conf: must be a mapping")
        .hasMessageContaining("STRING");
  }

  @Test
  void shouldRejectExplicitNullConf() throws Exception {
    Path f = write(HEAD + "conf:\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("conf: must be a mapping")
        .hasMessageContaining("NULL");
  }

  // ---- unknown properties ----

  @Test
  void shouldRejectMisspelledSeedKeyWhenSedeUsedForSeed() throws Exception {
    Path f = write(HEAD + "sede:\n  type: embedded\n  value: 1\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining(f.toString())
        .cause()
        .hasMessageContaining("sede");
  }

  @Test
  void shouldHonourCamelCaseStructuresPathWhenUsedInsteadOfSnakeCase() throws Exception {
    // Documents a quirk: Jackson also binds the private final field, so the Java field name is
    // accepted as an undocumented alias of structures_path rather than rejected as unknown.
    JobConfig c = parse(HEAD + "structuresPath: x\n" + MIN_TAIL);
    assertThat(c.getStructuresPath()).isEqualTo("x");
  }

  @Test
  void shouldRejectUnknownKeyInsideSecretsBlock() throws Exception {
    Path f = write(HEAD + "secrets:\n  resolvr: vault\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .cause()
        .hasMessageContaining("resolvr");
  }

  @Test
  void shouldRejectUnknownKeyInsideSeedBlock() throws Exception {
    Path f = write(HEAD + "seed:\n  type: embedded\n  valeu: 1\n" + MIN_TAIL);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .cause()
        .hasMessageContaining("valeu");
  }

  @Test
  void shouldPreserveUnknownKeysInsideConfWhenConfIsFreeForm() throws Exception {
    JobConfig c = parse(HEAD + "conf:\n  anything_goes: 1\n");
    assertThat(c.getConf().get("anything_goes").asInt()).isEqualTo(1);
  }

  // ---- seed mapping ----

  @Test
  void shouldMapEmbeddedSeedExactly() throws Exception {
    JobConfig c =
        parse(HEAD + "seed:\n  type: embedded\n  value: 9223372036854775807\n" + MIN_TAIL);
    SeedConfig.EmbeddedSeed s = (SeedConfig.EmbeddedSeed) c.getSeed();
    assertThat(s.getType()).isEqualTo("embedded");
    assertThat(s.getValue()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void shouldMapNegativeEmbeddedSeed() throws Exception {
    JobConfig c = parse(HEAD + "seed:\n  type: embedded\n  value: -5\n" + MIN_TAIL);
    assertThat(((SeedConfig.EmbeddedSeed) c.getSeed()).getValue()).isEqualTo(-5L);
  }

  @Test
  void shouldMapFileAndEnvSeedTypeNames() throws Exception {
    SeedConfig file = parse(HEAD + "seed:\n  type: file\n  path: /p/s\n" + MIN_TAIL).getSeed();
    SeedConfig env = parse(HEAD + "seed:\n  type: env\n  name: MY_SEED\n" + MIN_TAIL).getSeed();
    assertThat(file.getType()).isEqualTo("file");
    assertThat(((SeedConfig.FileSeed) file).getPath()).isEqualTo("/p/s");
    assertThat(env.getType()).isEqualTo("env");
    assertThat(((SeedConfig.EnvSeed) env).getName()).isEqualTo("MY_SEED");
  }

  @Test
  void shouldMapRemoteSeedWithoutAuth() throws Exception {
    SeedConfig.RemoteSeed s =
        (SeedConfig.RemoteSeed)
            parse(HEAD + "seed:\n  type: remote\n  url: http://h/seed\n" + MIN_TAIL).getSeed();
    assertThat(s.getType()).isEqualTo("remote");
    assertThat(s.getUrl()).isEqualTo("http://h/seed");
    assertThat(s.getAuth()).isNull();
  }

  @Test
  void shouldMapRemoteSeedBearerAuth() throws Exception {
    SeedConfig.RemoteSeed.AuthConfig a = remoteAuth("    type: bearer\n    token: tok\n");
    assertThat(a.getType()).isEqualTo("bearer");
    assertThat(a.getToken()).isEqualTo("tok");
    assertThat(a.getUsername()).isNull();
    assertThat(a.getPassword()).isNull();
    assertThat(a.getKey()).isNull();
    assertThat(a.getValue()).isNull();
  }

  @Test
  void shouldMapRemoteSeedBasicAuth() throws Exception {
    SeedConfig.RemoteSeed.AuthConfig a =
        remoteAuth("    type: basic\n    username: u\n    password: p\n");
    assertThat(a.getType()).isEqualTo("basic");
    assertThat(a.getUsername()).isEqualTo("u");
    assertThat(a.getPassword()).isEqualTo("p");
    assertThat(a.getToken()).isNull();
    assertThat(a.getKey()).isNull();
  }

  @Test
  void shouldMapRemoteSeedApiKeyAuth() throws Exception {
    SeedConfig.RemoteSeed.AuthConfig a =
        remoteAuth("    type: api_key\n    key: X-Api-Key\n    value: secret\n");
    assertThat(a.getType()).isEqualTo("api_key");
    assertThat(a.getKey()).isEqualTo("X-Api-Key");
    assertThat(a.getValue()).isEqualTo("secret");
    assertThat(a.getToken()).isNull();
    assertThat(a.getUsername()).isNull();
  }

  @Test
  void shouldNotLeakAuthSecretsInToString() throws Exception {
    SeedConfig.RemoteSeed.AuthConfig a =
        remoteAuth("    type: basic\n    username: alice\n    password: hunter2\n");
    assertThat(a.toString()).contains("alice").doesNotContain("hunter2");
  }

  private SeedConfig.RemoteSeed.AuthConfig remoteAuth(String authBody) throws IOException {
    SeedConfig.RemoteSeed s =
        (SeedConfig.RemoteSeed)
            parse(HEAD + "seed:\n  type: remote\n  url: http://h\n  auth:\n" + authBody + MIN_TAIL)
                .getSeed();
    return s.getAuth();
  }

  @Test
  void shouldDefaultEmbeddedSeedValueToZeroWhenValueOmitted() throws Exception {
    // documents current behaviour: primitive long silently defaults to 0 when 'value' is absent
    JobConfig c = parse(HEAD + "seed:\n  type: embedded\n" + MIN_TAIL);
    assertThat(((SeedConfig.EmbeddedSeed) c.getSeed()).getValue()).isZero();
  }

  // ---- structures_path / secrets ----

  @Test
  void shouldMapStructuresPathVerbatim() throws Exception {
    JobConfig c = parse(HEAD + "structures_path: ../my structs/\n" + MIN_TAIL);
    assertThat(c.getStructuresPath()).isEqualTo("../my structs/");
  }

  @Test
  void shouldLeaveStructuresPathNullWhenAbsent() throws Exception {
    assertThat(parse(HEAD + MIN_TAIL).getStructuresPath()).isNull();
  }

  @Test
  void shouldMapGcpSecretsAndLeaveOthersNull() throws Exception {
    SecretsConfig s =
        parse(
                HEAD
                    + "secrets:\n  resolver: gcp_secretmanager\n  gcp_project_id: my-proj\n"
                    + MIN_TAIL)
            .getSecrets();
    assertThat(s.getResolver()).isEqualTo("gcp_secretmanager");
    assertThat(s.getGcpProjectId()).isEqualTo("my-proj");
    assertThat(s.getVaultAddr()).isNull();
    assertThat(s.getVaultNamespace()).isNull();
    assertThat(s.getAwsRegion()).isNull();
    assertThat(s.getVaultUri()).isNull();
    assertThat(s.getKeyEnv()).isNull();
    assertThat(s.getKeyFile()).isNull();
  }

  @Test
  void shouldMapEverySecretsKeyToItsOwnProperty() throws Exception {
    SecretsConfig s =
        parse(
                HEAD
                    + "secrets:\n  resolver: r\n  vault_addr: va\n  vault_namespace: vn\n"
                    + "  aws_region: ar\n  vault_uri: vu\n  key_env: ke\n  key_file: kf\n"
                    + "  gcp_project_id: gp\n"
                    + MIN_TAIL)
            .getSecrets();
    assertThat(s.getResolver()).isEqualTo("r");
    assertThat(s.getVaultAddr()).isEqualTo("va");
    assertThat(s.getVaultNamespace()).isEqualTo("vn");
    assertThat(s.getAwsRegion()).isEqualTo("ar");
    assertThat(s.getVaultUri()).isEqualTo("vu");
    assertThat(s.getKeyEnv()).isEqualTo("ke");
    assertThat(s.getKeyFile()).isEqualTo("kf");
    assertThat(s.getGcpProjectId()).isEqualTo("gp");
  }

  // ---- conf passthrough ----

  @Test
  void shouldPassConfThroughWithNestedValuesIntact() throws Exception {
    JobConfig c =
        parse(
            String.join(
                "\n",
                "source: a.yaml",
                "type: kafka",
                "conf:",
                "  s: text",
                "  i: 42",
                "  d: 1.5",
                "  b: true",
                "  n: ~",
                "  quoted_num: \"007\"",
                "  list: [1, two, {k: v}]",
                "  nested:",
                "    deep:",
                "      deeper: x",
                "  placeholder: \"${SECRET:a/b#c}\"",
                ""));
    JsonNode conf = c.getConf();
    assertThat(conf.get("s").asText()).isEqualTo("text");
    assertThat(conf.get("i").isInt()).isTrue();
    assertThat(conf.get("i").asInt()).isEqualTo(42);
    assertThat(conf.get("d").isDouble()).isTrue();
    assertThat(conf.get("b").isBoolean()).isTrue();
    assertThat(conf.get("n").isNull()).isTrue();
    assertThat(conf.get("quoted_num").isTextual()).isTrue();
    assertThat(conf.get("quoted_num").asText()).isEqualTo("007");
    assertThat(conf.get("list")).hasSize(3);
    assertThat(conf.get("list").get(1).asText()).isEqualTo("two");
    assertThat(conf.get("list").get(2).get("k").asText()).isEqualTo("v");
    assertThat(conf.at("/nested/deep/deeper").asText()).isEqualTo("x");
    assertThat(conf.get("placeholder").asText()).isEqualTo("${SECRET:a/b#c}");
  }

  @Test
  void shouldKeepLastValueWhenConfHasDuplicateKeys() throws Exception {
    // documents silent last-wins on duplicate keys
    JobConfig c = parse(HEAD + "conf:\n  k: first\n  k: second\n");
    assertThat(c.getConf().get("k").asText()).isEqualTo("second");
  }

  // ---- file-level problems ----

  @Test
  void shouldFailWithFileNameAndYamlCauseWhenSyntaxIsInvalid() throws Exception {
    Path f = write("source: a.yaml\ntype: [unclosed\nconf:\n  k: v\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read job config")
        .hasMessageContaining(f.toString())
        .hasCauseInstanceOf(JsonProcessingException.class)
        .cause()
        .hasMessageContaining("line: ");
  }

  @Test
  void shouldFailWhenFileIsEmpty() throws Exception {
    Path f = write("");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read job config")
        .hasMessageContaining(f.toString())
        .cause()
        .hasMessageContaining("No content");
  }

  @Test
  void shouldFailWhenFileIsOnlyComments() throws Exception {
    Path f = write("# nothing here\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining(f.toString());
  }

  @Test
  void shouldFailWhenTopLevelIsAList() throws Exception {
    Path f = write("- source: a.yaml\n- type: file\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining(f.toString())
        .hasCauseInstanceOf(JsonProcessingException.class);
  }

  @Test
  void shouldFailWhenTopLevelIsAScalar() throws Exception {
    Path f = write("just text\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining(f.toString());
  }

  @Test
  void shouldFailWithIoCauseWhenFileIsNotUtf8() throws Exception {
    Path f = tempDir.resolve("latin1.yaml");
    byte[] head = "source: a.yaml\ntype: file\nconf:\n  k: caf".getBytes(StandardCharsets.UTF_8);
    byte[] all = new byte[head.length + 2];
    System.arraycopy(head, 0, all, 0, head.length);
    all[head.length] = (byte) 0xE9; // latin-1 e-acute, invalid UTF-8 sequence
    all[head.length + 1] = '\n';
    Files.write(f, all);
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read job config")
        .hasMessageContaining(f.toString())
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldHandleBomPrefixedFile() throws Exception {
    Path f = tempDir.resolve("bom.yaml");
    Files.writeString(f, "﻿" + HEAD + MIN_TAIL, StandardCharsets.UTF_8);
    JobConfig c = parser.parse(f);
    assertThat(c.getSource()).isEqualTo("a.yaml");
    assertThat(c.getConf().get("k").asText()).isEqualTo("v");
  }

  @Test
  void shouldPreserveUnicodeValues() throws Exception {
    JobConfig c = parse("source: \"città-日本.yaml\"\ntype: file\nconf:\n  k: ü\n");
    assertThat(c.getSource()).isEqualTo("città-日本.yaml");
    assertThat(c.getConf().get("k").asText()).isEqualTo("ü");
  }

  static Stream<Path> shippedJobConfigs() throws IOException {
    List<Path> jobs = new ArrayList<>();
    for (Path root : List.of(Path.of("../config/jobs"), Path.of("../use-cases"))) {
      try (Stream<Path> files = Files.walk(root)) {
        files
            .filter(
                p -> {
                  Path parent = p.getParent();
                  return parent != null && parent.endsWith("jobs");
                })
            .filter(p -> p.toString().endsWith(".yaml"))
            .forEach(jobs::add);
      }
    }
    return jobs.stream().sorted();
  }

  @ParameterizedTest
  @MethodSource("shippedJobConfigs")
  void shouldParseEveryShippedJobConfig(Path job) {
    // Guards config/ and use-cases/ against parser tightening (e.g. conf must be a mapping, #383).
    JobConfig config = parser.parse(job);

    assertThat(config.getConf().isObject()).isTrue();
  }

  @Test
  void shouldFindShippedJobConfigs() throws IOException {
    assertThat(shippedJobConfigs()).hasSizeGreaterThan(20);
  }
}
