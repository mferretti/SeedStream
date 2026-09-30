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

package com.datagenerator.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class RecordIndexTest {

  private static GenerationEngine.RecordGenerator capturing() {
    return random -> Map.of("i", RecordIndex.current());
  }

  private static Set<Long> indexes(List<Map<String, Object>> records) {
    Set<Long> out = new TreeSet<>();
    records.forEach(r -> out.add((Long) r.get("i")));
    return out;
  }

  @Test
  void shouldSetIndexesInOrderWhenSingleThreaded() throws InterruptedException {
    List<Map<String, Object>> written = new ArrayList<>();
    GenerationEngine.builder()
        .recordGenerator(capturing())
        .recordWriter(written::add)
        .masterSeed(1L)
        .singleThreadedThreshold(1000)
        .build()
        .generate(100);

    assertThat(written).hasSize(100);
    for (int i = 0; i < 100; i++) {
      assertThat(written.get(i).get("i")).isEqualTo((long) i);
    }
    assertThat(RecordIndex.current()).isEqualTo(-1L);
  }

  @Test
  void shouldSeeEveryIndexOnceWhenMultiThreaded() throws InterruptedException {
    List<Map<String, Object>> written = new ArrayList<>();
    GenerationEngine.builder()
        .recordGenerator(capturing())
        .recordWriter(
            r -> {
              synchronized (written) {
                written.add(r);
              }
            })
        .masterSeed(1L)
        .workerThreads(4)
        .singleThreadedThreshold(1000)
        .build()
        .generate(5000);

    assertThat(written).hasSize(5000);
    assertThat(indexes(written)).hasSize(5000).first().isEqualTo(0L);
    assertThat(indexes(written)).last().isEqualTo(4999L);
  }
}
