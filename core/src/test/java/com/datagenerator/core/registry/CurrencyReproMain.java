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

import java.util.Locale;
import java.util.Random;
import net.datafaker.Faker;

/**
 * Entry point launched in a child JVM by {@link DatafakerRegistryCurrencyTest}: prints {@value
 * #COUNT} {@code currency} values and {@value #COUNT} {@code locale_currency} fallback values (a
 * language-only locale has no country) for a fixed seed through the real registry path.
 */
public final class CurrencyReproMain {
  static final int COUNT = 200;
  static final long SEED = 4242L;

  private CurrencyReproMain() {}

  public static void main(String[] args) {
    Random random = new Random(SEED);
    Faker faker = new Faker(Locale.ENGLISH, random);
    for (int i = 0; i < COUNT; i++) {
      System.out.println("currency=" + DatafakerRegistry.generate("currency", faker, random));
    }
    for (int i = 0; i < COUNT; i++) {
      System.out.println(
          "locale_currency=" + DatafakerRegistry.generate("locale_currency", faker, random));
    }
  }
}
