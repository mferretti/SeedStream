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

import com.datagenerator.schema.model.DataStructure;
import java.util.List;
import java.util.Map;

/**
 * Outcome of inspecting a schema: the structures to emit plus diagnostics.
 *
 * @param structures one {@link DataStructure} per schema object, ready to write
 * @param comments review comments to emit, keyed by structure name then field name — only for
 *     flagged guesses (name hints, unknown types); declared and default-range fields are absent
 * @param warnings human-readable warnings (skipped schemas, etc.)
 */
public record Inspection(
    List<DataStructure> structures,
    Map<String, Map<String, String>> comments,
    List<String> warnings) {

  /** Total number of flagged fields across all structures. */
  public int flaggedCount() {
    return comments.values().stream().mapToInt(Map::size).sum();
  }
}
