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
 * Result of mapping one schema property to a SeedStream datatype, tagged with why that datatype was
 * chosen. The reason drives which fields get a review comment in the generated YAML: only the two
 * <em>guesses</em> ({@link Reason#NAME_HINT}, {@link Reason#UNKNOWN_TYPE}) are flagged. The
 * <em>certainties</em> ({@link Reason#DECLARED}) and the inherent-gap defaults ({@link
 * Reason#DEFAULT_RANGE}) stay silent to avoid noise on every numeric column. See {@code
 * docs/INSPECT-V1-SPEC.md}.
 *
 * @param datatype the SeedStream type string (e.g. {@code int[1..100]})
 * @param reason why this datatype was chosen
 * @param note the review comment for {@link Reason#REVIEW} / {@link Reason#SCHEMA_ERROR}, else null
 */
public record MappedType(String datatype, Reason reason, String note) {

  public MappedType(String mappedDatatype, Reason mappedReason) {
    this(mappedDatatype, mappedReason, null);
  }

  /** Why a datatype was chosen, in increasing need for human review. */
  public enum Reason {
    /** Came from explicit schema info (format, enum, declared bound, VARCHAR(n), boolean, date). */
    DECLARED,
    /** Default range/length used because the source has no bound — inherent, expected, silent. */
    DEFAULT_RANGE,
    /** Datafaker type guessed from the column/field name — may be wrong, flagged for review. */
    NAME_HINT,
    /** Source type not recognized; fell back to a primitive — flagged for review. */
    UNKNOWN_TYPE,
    /**
     * Recognized, but lossy or approximate (DDL), or a declared constraint the preferred type
     * cannot honour and a plain type was used instead (OpenAPI/JSON Schema) — flagged with a
     * specific note.
     */
    REVIEW,
    /** The source schema contradicts itself (e.g. minLength > maxLength) — flagged and warned. */
    SCHEMA_ERROR
  }

  public static MappedType declared(String datatype) {
    return new MappedType(datatype, Reason.DECLARED);
  }

  public static MappedType defaultRange(String datatype) {
    return new MappedType(datatype, Reason.DEFAULT_RANGE);
  }

  public static MappedType nameHint(String datatype) {
    return new MappedType(datatype, Reason.NAME_HINT);
  }

  public static MappedType unknownType(String datatype) {
    return new MappedType(datatype, Reason.UNKNOWN_TYPE);
  }

  public static MappedType review(String datatype, String note) {
    return new MappedType(datatype, Reason.REVIEW, note);
  }

  public static MappedType schemaError(String datatype, String note) {
    return new MappedType(datatype, Reason.SCHEMA_ERROR, note);
  }

  /** True when this mapping is a guess worth a review comment in the output. */
  public boolean flagged() {
    return reason != Reason.DECLARED && reason != Reason.DEFAULT_RANGE;
  }

  /** The review comment for a flagged mapping, or null when no comment is warranted. */
  public String comment() {
    return switch (reason) {
      case NAME_HINT -> "guessed from column name — verify";
      case UNKNOWN_TYPE -> "unrecognized source type, defaulted — verify";
      case REVIEW, SCHEMA_ERROR -> note;
      default -> null;
    };
  }
}
