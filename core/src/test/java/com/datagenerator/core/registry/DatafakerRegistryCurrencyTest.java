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

package com.datagenerator.core.registry;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.datafaker.Faker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproducibility of the {@code currency} / {@code locale_currency} types (#458). Datafaker picks
 * from a list built out of an identity-hashed {@code Set<Currency>}, so the same seed gave
 * different codes depending on JVM state.
 */
class DatafakerRegistryCurrencyTest {

  /** Independent copy of the 233 ISO 4217 codes (JDK 21), sorted. The oracle for these tests. */
  private static final List<String> EXPECTED = loadExpected();

  private static final long SEED = 42L;

  private static List<String> loadExpected() {
    try (InputStream in =
        Objects.requireNonNull(
            DatafakerRegistryCurrencyTest.class.getResourceAsStream(
                "/currency-codes-expected.txt"))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  // ── Child-JVM reproduction (the bug) ───────────────────────────────────────

  @Test
  void shouldProduceSameValuesInJvmsWithDifferentIdentityHashing(@TempDir Path tmp)
      throws Exception {
    List<String> defaults = runChild(tmp, "default");
    List<String> counterHash =
        runChild(tmp, "hash3", "-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=3");

    assertThat(defaults).hasSize(2 * CurrencyReproMain.COUNT);
    assertThat(counterHash).isEqualTo(defaults);
  }

  // Launches this JVM's own java binary with this JVM's own classpath: nothing external is
  // injected.
  @SuppressFBWarnings(
      value = "COMMAND_INJECTION",
      justification = "test launches its own JVM with its own classpath")
  private static List<String> runChild(Path tmp, String name, String... jvmFlags)
      throws IOException, InterruptedException {
    String java =
        ProcessHandle.current()
            .info()
            .command()
            .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString());
    List<String> cmd = new ArrayList<>(List.of(java));
    cmd.addAll(List.of(jvmFlags));
    cmd.addAll(
        List.of("-cp", System.getProperty("java.class.path"), CurrencyReproMain.class.getName()));

    Path err = tmp.resolve(name + ".err");
    // Fixed argv: this JVM's own java binary and classpath plus constant flags, no external input
    Process process = new ProcessBuilder(cmd).redirectError(err.toFile()).start(); // nosemgrep
    List<String> lines;
    try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
      // stdout also carries the logging framework's debug output: keep only our value lines
      lines = reader.lines().filter(l -> l.contains("currency=") && !l.contains(" ")).toList();
    }
    assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("child JVM finished").isTrue();
    String stderr = Files.readString(err);
    assumeTrue(
        !stderr.contains("Unrecognized VM option"), "JVM rejects the flag: " + stderr.strip());
    assertThat(process.exitValue()).as("child JVM exit code, stderr: %s", stderr).isZero();
    return lines;
  }

  // ── Embedded list ──────────────────────────────────────────────────────────

  @Test
  void shouldDrawFromAllEntriesOfTheEmbeddedSortedList() {
    // 233 entries, strictly sorted, no duplicates (sanity of the oracle itself)
    assertThat(EXPECTED).hasSize(233).doesNotHaveDuplicates().isSorted();

    Random random = new Random(SEED);
    Faker faker = new Faker(Locale.US, random);
    List<String> generated = new ArrayList<>();
    for (int i = 0; i < 20_000; i++) {
      generated.add(DatafakerRegistry.generate("currency", faker, random));
    }

    // every value is in the list, and the whole list is reachable (so the registry list IS it)
    assertThat(generated).isSubsetOf(EXPECTED);
    assertThat(generated.stream().distinct().toList())
        .containsExactlyInAnyOrderElementsOf(EXPECTED);
  }

  // ── One draw per value, index into the sorted list ─────────────────────────

  @Test
  void shouldPickCodeAtSeededIndexWithOneDrawPerValue() {
    Random random = new Random(SEED);
    Faker faker = new Faker(Locale.US, random);
    Random oracle = new Random(SEED);

    for (int i = 0; i < 5_000; i++) {
      assertThat(DatafakerRegistry.generate("currency", faker, random))
          .as("value #%d", i)
          .isEqualTo(EXPECTED.get(oracle.nextInt(EXPECTED.size())));
    }
  }

  @Test
  void shouldMatchPinnedSequenceForSeed42() {
    Random random = new Random(SEED);
    Faker faker = new Faker(Locale.US, random);

    List<String> first10 = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      first10.add(DatafakerRegistry.generate("currency", faker, random));
    }

    assertThat(first10)
        .containsExactly("AED", "PEN", "ZWG", "SLE", "SDG", "BEF", "ILS", "MZM", "VUV", "MZM");
  }

  // ── Other fields unaffected ────────────────────────────────────────────────

  @Test
  void shouldLeaveDrawsOfSurroundingFieldsUntouched() {
    Random random = new Random(SEED);
    Faker faker = new Faker(Locale.US, random);
    Random oracle = new Random(SEED);

    for (int i = 0; i < 1_000; i++) {
      int before = random.nextInt(1_000_000);
      DatafakerRegistry.generate("currency", faker, random);
      int after = random.nextInt(1_000_000);

      assertThat(before).as("field before, record %d", i).isEqualTo(oracle.nextInt(1_000_000));
      oracle.nextInt(EXPECTED.size()); // exactly one draw for the currency field
      assertThat(after).as("field after, record %d", i).isEqualTo(oracle.nextInt(1_000_000));
    }
  }

  // ── locale_currency fallback ───────────────────────────────────────────────

  @Test
  void shouldFallBackToSameListAndSingleDrawForLocaleWithoutCountry() {
    Random random = new Random(SEED);
    Faker faker = new Faker(Locale.ENGLISH, random); // language only: no country
    Random oracle = new Random(SEED);

    for (int i = 0; i < 2_000; i++) {
      assertThat(DatafakerRegistry.generate("locale_currency", faker, random))
          .as("value #%d", i)
          .isEqualTo(EXPECTED.get(oracle.nextInt(EXPECTED.size())));
    }
  }

  @Test
  void shouldKeepLocaleCurrencyMainPathUnchangedAndDrawNothing() {
    Random random = new Random(SEED);
    Faker faker = new Faker(Locale.ITALY, random);

    assertThat(DatafakerRegistry.generate("locale_currency", faker, random)).isEqualTo("EUR");
    // no draw consumed: the stream continues exactly where a fresh Random(SEED) starts
    // Seeded RNG needed as the determinism oracle (not used for security)
    assertThat(random.ints(3).toArray()).isEqualTo(new Random(SEED).ints(3).toArray()); // nosemgrep
  }
}
