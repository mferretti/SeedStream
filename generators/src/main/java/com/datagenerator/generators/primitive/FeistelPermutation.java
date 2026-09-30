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

/**
 * Keyed bijection on {@code [0, domain)} built from a balanced 4-round Feistel network with
 * cycle-walking. Pure static functions, no allocation.
 */
public final class FeistelPermutation {
  private static final long GOLDEN = 0x9E3779B97F4A7C15L;
  private static final int ROUNDS = 4;

  private FeistelPermutation() {}

  /** SplitMix64 finalizer. */
  public static long mix64(long z) {
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
  }

  /**
   * Maps {@code index} to its image under the keyed permutation of {@code [0, domain)}.
   *
   * @param index position in {@code [0, domain)}
   * @param domain size of the permuted set
   * @param halfBits h such that {@code 4^h >= domain}; the network works over {@code 2h} bits
   * @param baseKey permutation key, e.g. {@code mix64(masterSeed ^ groupHash)}
   */
  public static long permute(long index, long domain, int halfBits, long baseKey) {
    if (domain == 1L) {
      return 0L;
    }
    long y = feistel(index, halfBits, baseKey);
    while (y >= domain) { // cycle-walk: terminates because feistel is a bijection on [0, 4^h)
      y = feistel(y, halfBits, baseKey);
    }
    return y;
  }

  private static long feistel(long x, int h, long baseKey) {
    long mask = (1L << h) - 1;
    long left = x >>> h;
    long right = x & mask;
    for (int r = 0; r < ROUNDS; r++) {
      long f = mix64((baseKey + r * GOLDEN) ^ right) & mask;
      long next = left ^ f;
      left = right;
      right = next;
    }
    return (left << h) | right;
  }
}
