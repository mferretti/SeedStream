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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.BitSet;
import org.junit.jupiter.api.Test;

class FeistelPermutationTest {
  private static final long[] DOMAINS = {1, 2, 3, 7, 16, 17, 100, 4096, 4097, 65536, 50021};

  private static int halfBits(long domain) {
    int h = 1;
    while ((1L << (2 * h)) < domain) {
      h++;
    }
    return h;
  }

  @Test
  void shouldBeBijectionWhenPermutingWholeDomain() {
    for (long domain : DOMAINS) {
      BitSet seen = new BitSet((int) domain);
      for (long i = 0; i < domain; i++) {
        long y = FeistelPermutation.permute(i, domain, halfBits(domain), 12345L);
        assertThat(y).isBetween(0L, domain - 1);
        assertThat(seen.get((int) y)).as("domain %d duplicate %d", domain, y).isFalse();
        seen.set((int) y);
      }
      assertThat(seen.cardinality()).isEqualTo((int) domain);
    }
  }

  @Test
  void shouldReturnZeroWhenDomainIsOne() {
    assertThat(FeistelPermutation.permute(0, 1, 1, 99L)).isZero();
  }

  @Test
  void shouldBeDeterministicWhenKeyIsSame() {
    for (long i = 0; i < 4096; i++) {
      assertThat(FeistelPermutation.permute(i, 4096, 6, 7L))
          .isEqualTo(FeistelPermutation.permute(i, 4096, 6, 7L));
    }
  }

  @Test
  void shouldProduceDifferentPermutationWhenKeyDiffers() {
    int same = 0;
    for (long i = 0; i < 4096; i++) {
      if (FeistelPermutation.permute(i, 4096, 6, 1L)
          == FeistelPermutation.permute(i, 4096, 6, 2L)) {
        same++;
      }
    }
    assertThat(same).isLessThan(100);
  }
}
