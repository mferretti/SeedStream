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

package com.datagenerator.inspector;

/**
 * Result of mapping one schema property to a SeedStream datatype.
 *
 * @param datatype the SeedStream type string (e.g. {@code int[1..100]})
 * @param inferred true when a default range/fallback was used because the source carried no
 *     explicit bound or type — surfaced to the user so they can review the guess
 */
public record MappedType(String datatype, boolean inferred) {

  public static MappedType explicit(String datatype) {
    return new MappedType(datatype, false);
  }

  public static MappedType inferred(String datatype) {
    return new MappedType(datatype, true);
  }
}
