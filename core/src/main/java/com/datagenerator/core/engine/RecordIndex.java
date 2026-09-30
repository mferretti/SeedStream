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

/**
 * Per-thread holder for the global index of the record currently being generated. The engine writes
 * it once per record (no allocation, no {@code ThreadLocal.set}); generators read it.
 */
public final class RecordIndex {
  private static final ThreadLocal<long[]> HOLDER = ThreadLocal.withInitial(() -> new long[] {-1L});

  private RecordIndex() {}

  /** The calling thread's mutable slot; the engine grabs it once per worker/loop. */
  public static long[] holder() {
    return HOLDER.get();
  }

  /** Index of the record being generated on this thread, or -1 outside the engine. */
  public static long current() {
    return HOLDER.get()[0];
  }

  /** Drops this thread's slot. */
  public static void clear() {
    HOLDER.remove();
  }
}
