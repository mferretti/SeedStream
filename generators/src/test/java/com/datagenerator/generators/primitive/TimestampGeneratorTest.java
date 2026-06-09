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

package com.datagenerator.generators.primitive;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.GeneratorException;
import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TimestampGeneratorTest {

  private static final Random RANDOM = new Random(42L);
  private static final String TS_MIN = "2020-01-01T00:00:00";
  private static final String TS_MAX = "2025-12-31T23:59:59";

  private final TimestampGenerator generator = new TimestampGenerator();

  // ── ISO-8601 ────────────────────────────────────────────────────────────────

  @Test
  void shouldGenerateTimestampWithinIsoRange() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, TS_MIN, TS_MAX);
    Instant min = Instant.parse("2020-01-01T00:00:00Z");
    Instant max = Instant.parse("2025-12-31T23:59:59Z");
    Random random = new Random(42L);

    for (int i = 0; i < 50; i++) {
      Instant value = (Instant) generator.generate(random, type);
      assertThat(value).isAfterOrEqualTo(min).isBeforeOrEqualTo(max);
    }
  }

  @Test
  void shouldBeDeterministicWithSameSeed() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, TS_MIN, TS_MAX);
    Random r1 = new Random(77L);
    Random r2 = new Random(77L);

    assertThat(generator.generate(r1, type)).isEqualTo(generator.generate(r2, type));
  }

  @Test
  void shouldReturnSameTimestampWhenStartEqualsEnd() {
    PrimitiveType type =
        new PrimitiveType(
            PrimitiveType.Kind.TIMESTAMP, "2024-06-15T12:00:00", "2024-06-15T12:00:00");

    Instant result = (Instant) generator.generate(new Random(), type);
    assertThat(result).isEqualTo(Instant.parse("2024-06-15T12:00:00Z"));
  }

  // ── Relative formats ────────────────────────────────────────────────────────

  @Test
  void shouldHandleNowAsMinValue() {
    Instant before = Instant.now();
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "now", "now+1d");

    Instant result = (Instant) generator.generate(new Random(1L), type);
    assertThat(result).isAfterOrEqualTo(before);
  }

  @Test
  void shouldHandleNowAsMaxValue() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "now-30d", "now");

    Instant result = (Instant) generator.generate(new Random(1L), type);
    assertThat(result).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void shouldHandleRelativeMinusDays() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "now-30d", "now-1d");

    Instant result = (Instant) generator.generate(new Random(1L), type);
    assertThat(result).isBefore(Instant.now());
  }

  @Test
  void shouldHandleRelativePlusDays() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "now+1d", "now+30d");

    Instant result = (Instant) generator.generate(new Random(1L), type);
    assertThat(result).isAfter(Instant.now());
  }

  @Test
  void shouldHandleRelativeHours() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "now-7h", "now+7h");

    Instant result = (Instant) generator.generate(new Random(1L), type);
    assertThat(result)
        .isBetween(
            Instant.now().minusSeconds(7L * 3600 + 5), Instant.now().plusSeconds(7L * 3600 + 5));
  }

  @Test
  void shouldHandleRelativeMinutes() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "now-5m", "now+5m");

    Instant result = (Instant) generator.generate(new Random(1L), type);
    assertThat(result).isBetween(Instant.now().minusSeconds(305), Instant.now().plusSeconds(305));
  }

  @Test
  void shouldHandleRelativeSeconds() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "now-10s", "now+10s");

    Instant result = (Instant) generator.generate(new Random(1L), type);
    assertThat(result).isBetween(Instant.now().minusSeconds(15), Instant.now().plusSeconds(15));
  }

  // ── Error paths ─────────────────────────────────────────────────────────────

  @Test
  void shouldThrowWhenMinValueIsNull() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, null, TS_MAX);

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("minValue");
  }

  @Test
  void shouldThrowWhenMaxValueIsNull() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, TS_MIN, null);

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("maxValue");
  }

  @Test
  void shouldThrowWhenMinValueHasInvalidFormat() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, "not-a-timestamp", TS_MAX);

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("minValue");
  }

  @Test
  void shouldThrowWhenMinGreaterThanMax() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, TS_MAX, TS_MIN);

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("timestamp range");
  }

  @Test
  void shouldThrowWhenWrongKind() {
    PrimitiveType type = new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31");

    var rnd = RANDOM;
    assertThatThrownBy(() -> generator.generate(rnd, type))
        .isInstanceOf(GeneratorException.class)
        .hasMessageContaining("TimestampGenerator");
  }

  @Test
  void shouldSupportTimestampKindOnly() {
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.TIMESTAMP, TS_MIN, TS_MAX)))
        .isTrue();
    assertThat(
            generator.supports(
                new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31")))
        .isFalse();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.INT, "0", "100"))).isFalse();
  }
}
