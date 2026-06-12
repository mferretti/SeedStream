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
 * Default SeedStream ranges used when a source schema carries no explicit bound. Shared by the
 * OpenAPI and DDL mappers so the inference policy lives in one place. See {@code
 * docs/INSPECT-V1-SPEC.md} §4.
 */
public final class Defaults {

  private Defaults() {}

  public static final long INT_MIN = 1L;
  public static final long INT_MAX = 999_999L;
  public static final String DECIMAL_MIN = "0.0";
  public static final String DECIMAL_MAX = "9999.99";
  public static final String DATE = "date[2020-01-01..2030-12-31]";
  public static final String TIMESTAMP = "timestamp[now-365d..now]";
  public static final String STRING = "char[1..50]";
  public static final int ARRAY_MIN = 1;
  public static final int ARRAY_MAX = 10;

  /** DDL {@code VARCHAR}/{@code CHAR} with no declared length. */
  public static final int VARCHAR_DEFAULT_LENGTH = 255;

  /** DDL {@code TEXT}/{@code CLOB} upper bound. */
  public static final int TEXT_MAX_LENGTH = 500;
}
