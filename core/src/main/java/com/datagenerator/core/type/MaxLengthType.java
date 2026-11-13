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

package com.datagenerator.core.type;

import lombok.Value;

/**
 * A text field capped by {@code max_length} (#440): the inner value is regenerated while it is too
 * long, then truncated. {@code bytes} selects UTF-8 bytes instead of code points as the unit.
 */
@Value
public class MaxLengthType implements DataType {
  DataType inner;
  int maxLength;
  boolean bytes;

  @Override
  public DataType unwrap() {
    return inner.unwrap();
  }

  @Override
  public String describe() {
    return inner.describe() + " max_length " + maxLength + (bytes ? " bytes" : "");
  }
}
