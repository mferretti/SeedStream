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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.MappedType.Reason;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec reference: {@code docs/INSPECT-V1-SPEC.md} §3 (type mapping), §4 (defaults), §6 Q2. */
class SchemaTypeMapperTest {

  private static final String INT_DEFAULT = "int[1..999999]";
  private static final String DEC_DEFAULT = "decimal[0.0..9999.99]";
  private static final String STR_DEFAULT = "char[1..50]";

  private static final ObjectMapper JSON =
      new ObjectMapper(
          new JsonFactoryBuilder().enable(JsonReadFeature.ALLOW_SINGLE_QUOTES).build());

  private final SchemaTypeMapper mapper = new SchemaTypeMapper();

  // --- integer ---

  @ParameterizedTest
  @ValueSource(strings = {"{'type':'integer'}", "{'type':'integer','format':'int32'}"})
  void shouldMapUnboundedIntegerToDefaultRange(String schema) {
    MappedType mapped = map("count", schema);

    assertThat(mapped.datatype()).isEqualTo(INT_DEFAULT);
    assertThat(mapped.reason()).isEqualTo(Reason.DEFAULT_RANGE);
    assertThat(mapped.flagged()).isFalse();
  }

  @Test
  void shouldMapInt64WithBoundsToDeclaredRange() {
    MappedType mapped =
        map("count", "{'type':'integer','format':'int64','minimum':-5,'maximum':20}");

    assertThat(mapped.datatype()).isEqualTo("int[-5..20]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldKeepDefaultMaxWhenOnlyIntegerMinimumGiven() {
    assertThat(map("n", "{'type':'integer','minimum':18}").datatype()).isEqualTo("int[18..999999]");
  }

  @Test
  void shouldKeepDefaultMinWhenOnlyIntegerMaximumGiven() {
    assertThat(map("n", "{'type':'integer','maximum':500}").datatype()).isEqualTo("int[1..500]");
  }

  @Test
  void shouldNotEmitInvertedRangeWhenIntegerMaximumIsBelowDefaultMin() {
    // maximum: 0 with the implicit default minimum 1 would be int[1..0], which cannot generate.
    double[] range = range(map("n", "{'type':'integer','maximum':0}").datatype());

    assertThat(range[0]).isLessThanOrEqualTo(range[1]);
    assertThat(range[1]).isEqualTo(0d);
  }

  @Test
  void shouldKeepDefaultSpanFromDeclaredSideWhenDefaultWouldInvert() {
    // Documented in INSPECT-V1-SPEC §3: default span (999999 - 1) from the declared minimum.
    assertThat(map("n", "{'type':'integer','minimum':2000000}").datatype())
        .isEqualTo("int[2000000..2999998]");
    assertThat(map("n", "{'type':'integer','maximum':0}").datatype()).isEqualTo("int[-999998..0]");
    assertThat(map("a", "{'type':'array','minItems':20,'items':{'type':'boolean'}}").datatype())
        .isEqualTo("array[boolean, 20..29]");
  }

  @Test
  void shouldNotEmitInvertedRangeWhenIntegerMinimumIsAboveDefaultMax() {
    double[] range = range(map("n", "{'type':'integer','minimum':2000000}").datatype());

    assertThat(range[0]).isLessThanOrEqualTo(range[1]).isEqualTo(2000000d);
  }

  @Test
  void shouldHonorExclusiveMinimumAndMaximumForInteger() {
    // exclusiveMinimum: 10 means >= 11; exclusiveMaximum: 100 means <= 99.
    assertThat(
            map("n", "{'type':'integer','exclusiveMinimum':10,'exclusiveMaximum':100}").datatype())
        .isEqualTo("int[11..99]");
  }

  @Test
  void shouldHonorEnumOnIntegerBeforeNumericBounds() {
    // spec §3 resolution order: enum precedes bounded numeric / default.
    assertThat(map("n", "{'type':'integer','enum':[1,2,3]}").datatype()).isEqualTo("enum[1,2,3]");
  }

  // --- number ---

  @ParameterizedTest
  @ValueSource(strings = {"{'type':'number'}", "{'type':'number','format':'float'}"})
  void shouldMapUnboundedNumberToDefaultDecimal(String schema) {
    MappedType mapped = map("amount", schema);

    assertThat(mapped.datatype()).isEqualTo(DEC_DEFAULT);
    assertThat(mapped.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void shouldMapDoubleWithBoundsToDeclaredDecimal() {
    MappedType mapped =
        map("amount", "{'type':'number','format':'double','minimum':0.5,'maximum':99.25}");

    assertThat(mapped.datatype()).isEqualTo("decimal[0.5..99.25]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldKeepDefaultMaxWhenOnlyNumberMinimumGiven() {
    assertThat(map("a", "{'type':'number','minimum':1.5}").datatype())
        .isEqualTo("decimal[1.5..9999.99]");
  }

  @Test
  void shouldKeepDefaultMinWhenOnlyNumberMaximumGiven() {
    assertThat(map("a", "{'type':'number','maximum':100.5}").datatype())
        .isEqualTo("decimal[0.0..100.5]");
  }

  @Test
  void shouldNotEmitInvertedRangeWhenNumberMinimumIsAboveDefaultMax() {
    double[] range = range(map("a", "{'type':'number','minimum':10000}").datatype());

    assertThat(range[0]).isLessThanOrEqualTo(range[1]).isEqualTo(10000d);
  }

  @Test
  void shouldNotEmitInvertedRangeWhenNumberMaximumIsNegative() {
    double[] range = range(map("a", "{'type':'number','maximum':-5.5}").datatype());

    assertThat(range[0]).isLessThanOrEqualTo(range[1]);
    assertThat(range[1]).isEqualTo(-5.5d);
  }

  @Test
  void shouldNotUseScientificNotationForSmallDecimalBounds() {
    // Jackson renders 0.0000001 as "1.0E-7"; the SeedStream decimal[...] syntax has no exponent.
    assertThat(map("a", "{'type':'number','minimum':0.0000001,'maximum':1}").datatype())
        .isEqualTo("decimal[0.0000001..1]");
  }

  @Test
  void shouldHonorExclusiveMinimumForNumber() {
    // exclusive bound must not be emitted as an inclusive one
    MappedType mapped = map("a", "{'type':'number','exclusiveMinimum':0,'maximum':10}");

    assertThat(mapped.datatype()).isNotEqualTo("decimal[0.0..10]").isNotEqualTo("decimal[0..10]");
  }

  // --- boolean ---

  @Test
  void shouldMapBooleanToBoolean() {
    MappedType mapped = map("flag", "{'type':'boolean'}");

    assertThat(mapped.datatype()).isEqualTo("boolean");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  // --- string formats ---

  @Test
  void shouldMapDateFormatToDefaultDateRange() {
    MappedType mapped = map("born", "{'type':'string','format':'date'}");

    assertThat(mapped.datatype()).isEqualTo("date[2020-01-01..2030-12-31]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldMapDateTimeFormatToRollingTimestamp() {
    MappedType mapped = map("seenAt", "{'type':'string','format':'date-time'}");

    assertThat(mapped.datatype()).isEqualTo("timestamp[now-365d..now]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldMapUuidFormatToUuidFakerKey() {
    MappedType mapped = map("ident", "{'type':'string','format':'uuid'}");

    assertThat(mapped.datatype()).isEqualTo("uuid");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldMapEmailFormatToEmailFakerKeyWhenMaxLengthIsGenerous() {
    // #452: the old test used maxLength 20, which Datafaker emails can exceed; now it is a
    // char range with a review comment (see the #452 section below). 64 and up keep `email`.
    MappedType mapped = map("contact", "{'type':'string','format':'email','maxLength':64}");

    assertThat(mapped.datatype()).isEqualTo("email");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldLetDateFormatWinOverEnum() {
    assertThat(map("d", "{'type':'string','format':'date','enum':['a','b']}").datatype())
        .isEqualTo("date[2020-01-01..2030-12-31]");
  }

  @ParameterizedTest
  @ValueSource(strings = {"uri", "byte", "binary", "password", "hostname", "ipv4"})
  void shouldFallBackToDefaultStringForFormatsNotInSpecTable(String format) {
    // spec §3 lists only email/date/date-time/uuid; others fall through to hint/default.
    MappedType mapped = map("payload", "{'type':'string','format':'" + format + "'}");

    assertThat(mapped.datatype()).isEqualTo(STR_DEFAULT);
    assertThat(mapped.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void shouldApplyMaxLengthToUriFormat() {
    assertThat(map("link", "{'type':'string','format':'uri','maxLength':200}").datatype())
        .isEqualTo("char[1..200]");
  }

  @Test
  void shouldUseNameHintForUriFormatWhenFieldNameIsUrlLike() {
    MappedType mapped = map("homepageUrl", "{'type':'string','format':'uri'}");

    assertThat(mapped.datatype()).isEqualTo("url");
    assertThat(mapped.reason()).isEqualTo(Reason.NAME_HINT);
  }

  // --- string enum / length / pattern / hints ---

  @Test
  void shouldMapStringEnumToEnumList() {
    MappedType mapped = map("status", "{'type':'string','enum':['NEW','IN_PROGRESS','DONE']}");

    assertThat(mapped.datatype()).isEqualTo("enum[NEW,IN_PROGRESS,DONE]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldLetEnumWinOverMaxLength() {
    assertThat(map("s", "{'type':'string','enum':['A','B'],'maxLength':1}").datatype())
        .isEqualTo("enum[A,B]");
  }

  @Test
  void shouldLetEnumWinOverNameHint() {
    assertThat(map("country", "{'type':'string','enum':['IT','FR']}").datatype())
        .isEqualTo("enum[IT,FR]");
  }

  @Test
  void shouldMapMaxLengthToCharRangeStartingAtOne() {
    MappedType mapped = map("nick", "{'type':'string','maxLength':30}");

    assertThat(mapped.datatype()).isEqualTo("char[1..30]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldMapMinAndMaxLengthToCharRangeWhenBothGiven() {
    // #452: minLength is now honoured (was: char[1..8], minLength ignored)
    assertThat(map("code", "{'type':'string','minLength':8,'maxLength':8}").datatype())
        .isEqualTo("char[8..8]");
  }

  @Test
  void shouldLetMaxLengthWinOverNameHint() {
    // spec §3: maxLength (step 2) precedes name hint (step 4)
    MappedType mapped = map("email", "{'type':'string','maxLength':20}");

    assertThat(mapped.datatype()).isEqualTo("char[1..20]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldMapPlainStringToDefaultStringSilently() {
    MappedType mapped = map("remark", "{'type':'string'}");

    assertThat(mapped.datatype()).isEqualTo(STR_DEFAULT);
    assertThat(mapped.reason()).isEqualTo(Reason.DEFAULT_RANGE);
    assertThat(mapped.comment()).isNull();
  }

  @Test
  void shouldMapStringWithPatternToDefaultStringAtMapperLevel() {
    // regex handling lives in JsonSchemaInspector; the mapper itself has no regex type
    MappedType mapped = map("sku", "{'type':'string','pattern':'^[A-Z]{3}-\\\\d{3}$'}");

    assertThat(mapped.datatype()).isEqualTo(STR_DEFAULT);
    assertThat(mapped.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @ParameterizedTest
  @CsvSource({
    "email,email",
    "userEmail,email",
    "phone,phone_number",
    "firstName,first_name",
    "last_name,last_name",
    "city,city",
    "country,country",
    "zipCode,postal_code",
    "guid,uuid"
  })
  void shouldFlagNameHintForUntypedStringField(String field, String expected) {
    MappedType mapped = map(field, "{'type':'string'}");

    assertThat(mapped.datatype()).isEqualTo(expected);
    assertThat(mapped.reason()).isEqualTo(Reason.NAME_HINT);
    assertThat(mapped.comment()).isEqualTo("guessed from column name — verify");
  }

  @Test
  void shouldNotApplyNameHintToSubstringInsideSingleToken() {
    assertThat(map("emailish", "{'type':'string'}").datatype()).isEqualTo(STR_DEFAULT);
  }

  // --- string lengths, #452 phase 1 ---

  @Test
  void shouldHonorMinLengthAndMaxLength() {
    MappedType mapped = map("code", "{'type':'string','minLength':4,'maxLength':6}");

    assertThat(mapped.datatype()).isEqualTo("char[4..6]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
    assertThat(mapped.flagged()).isFalse();
  }

  @Test
  void shouldHonorMinLengthAndMaxLengthOnNullableTypeArray() {
    assertThat(map("code", "{'type':['string','null'],'minLength':2,'maxLength':5}").datatype())
        .isEqualTo("char[2..5]");
  }

  @Test
  void shouldKeepDefaultMaxWhenOnlyMinLengthGiven() {
    MappedType mapped = map("remark", "{'type':'string','minLength':3}");

    assertThat(mapped.datatype()).isEqualTo("char[3..50]");
    assertThat(mapped.flagged()).isFalse();
  }

  @Test
  void shouldNotEmitInvertedRangeWhenMinLengthExceedsDefaultMax() {
    // max(minLength, default max): char[80..50] cannot be generated
    assertThat(map("remark", "{'type':'string','minLength':80}").datatype())
        .isEqualTo("char[80..80]");
  }

  @Test
  void shouldAcceptZeroLowerBoundInCharRangeBecauseTypeParserAndCharGeneratorDo() {
    // Verified: TypeParser's primitive pattern takes any bounds and CharGenerator.parseLength only
    // rejects negatives ("must be >= 0"); config/structures/store_movement.yaml ships char[0..100].
    // So minLength 0 is honoured instead of being rounded up to 1.
    MappedType mapped = map("note", "{'type':'string','minLength':0,'maxLength':5}");

    assertThat(mapped.datatype()).isEqualTo("char[0..5]");
    assertThat(mapped.flagged()).isFalse();
  }

  @Test
  void shouldKeepCharRangeFromOneAndFlagWhenMinLengthExceedsMaxLength() {
    MappedType mapped = map("broken", "{'type':'string','minLength':10,'maxLength':5}");

    assertThat(mapped.datatype()).isEqualTo("char[1..5]");
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("minLength");
  }

  @ParameterizedTest
  @ValueSource(ints = {35, 10, 1})
  void shouldFallBackToCharRangeWithCommentWhenUuidCannotFitMaxLength(int maxLength) {
    MappedType mapped =
        map("ident", "{'type':'string','format':'uuid','maxLength':" + maxLength + "}");

    assertThat(mapped.datatype()).isEqualTo("char[1.." + maxLength + "]");
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("uuid format cannot fit maxLength " + maxLength);
  }

  @Test
  void shouldFallBackToCharRangeWithCommentWhenUuidMinLengthExceedsItsLength() {
    MappedType mapped =
        map("ident", "{'type':'string','format':'uuid','minLength':37,'maxLength':40}");

    assertThat(mapped.datatype()).isEqualTo("char[37..40]");
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("uuid");
  }

  @Test
  void shouldFallBackToCharRangeFromDefaultMaxWhenUuidMinLengthExceedsItsLengthWithoutMaxLength() {
    MappedType mapped = map("ident", "{'type':'string','format':'uuid','minLength':37}");

    assertThat(mapped.datatype()).isEqualTo("char[37..50]");
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("uuid");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{'type':'string','format':'uuid'}",
        "{'type':'string','format':'uuid','maxLength':36}",
        "{'type':'string','format':'uuid','maxLength':100}",
        "{'type':'string','format':'uuid','minLength':36,'maxLength':36}",
        "{'type':'string','format':'uuid','minLength':36}"
      })
  void shouldKeepUuidWhenItFitsTheDeclaredLengths(String schema) {
    // guard, passes today
    MappedType mapped = map("ident", schema);

    assertThat(mapped.datatype()).isEqualTo("uuid");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
    assertThat(mapped.flagged()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {63, 20, 5})
  void shouldFallBackToCharRangeWithCommentWhenEmailMaxLengthIsBelowObservedMax(int maxLength) {
    MappedType mapped =
        map("contact", "{'type':'string','format':'email','maxLength':" + maxLength + "}");

    assertThat(mapped.datatype()).isEqualTo("char[1.." + maxLength + "]");
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment())
        .contains("email values can exceed maxLength " + maxLength)
        .contains("#440");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{'type':'string','format':'email'}",
        "{'type':'string','format':'email','maxLength':64}",
        "{'type':'string','format':'email','maxLength':320}",
        "{'type':'string','format':'email','minLength':6}",
        "{'type':'string','format':'email','minLength':6,'maxLength':254}"
      })
  void shouldKeepEmailWhenLengthsLeaveRoomForIt(String schema) {
    // guard, passes today (measured: 1M Datafaker emails, longest 36 chars, shortest > 6)
    MappedType mapped = map("contact", schema);

    assertThat(mapped.datatype()).isEqualTo("email");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
    assertThat(mapped.flagged()).isFalse();
  }

  @Test
  void shouldFallBackToCharRangeWithCommentWhenEmailMinLengthIsAboveSix() {
    MappedType mapped = map("contact", "{'type':'string','format':'email','minLength':7}");

    assertThat(mapped.datatype()).isEqualTo("char[7..50]");
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("email");
  }

  @Test
  void shouldFallBackToCharRangeWithCommentWhenEmailMinLengthIsAboveSixWithMaxLength() {
    MappedType mapped =
        map("contact", "{'type':'string','format':'email','minLength':12,'maxLength':254}");

    assertThat(mapped.datatype()).isEqualTo("char[12..254]");
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("email");
  }

  @Test
  void shouldLetMaxLengthWinOverNameHintAndStillHonorMinLength() {
    MappedType mapped = map("city", "{'type':'string','minLength':3,'maxLength':12}");

    assertThat(mapped.datatype()).isEqualTo("char[3..12]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldKeepCharRangeFromOneForNameHintedFieldWithOnlyMaxLength() {
    // guard, passes today: maxLength beats the name hint (current behaviour)
    MappedType mapped = map("city", "{'type':'string','maxLength':12}");

    assertThat(mapped.datatype()).isEqualTo("char[1..12]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldIgnoreLengthsWhenEnumPresent() {
    // guard, passes today
    assertThat(
            map("status", "{'type':'string','enum':['AB','CD'],'minLength':4,'maxLength':9}")
                .datatype())
        .isEqualTo("enum[AB,CD]");
  }

  @Test
  void shouldNotEmitInvertedRangeWhenMaxLengthIsZeroAndMinLengthIsPositive() {
    // char[1..0] fails at generation time; the schema error falls back to the only valid range
    MappedType mapped = map("broken", "{'type':'string','minLength':3,'maxLength':0}");

    assertThat(mapped.datatype()).isEqualTo("char[0..0]");
    assertThat(mapped.reason()).isEqualTo(Reason.SCHEMA_ERROR);
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("minLength");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{'type':'array','items':{'type':'string','minLength':10,'maxLength':5}}",
        "{'type':'array','minItems':1,'maxItems':3,"
            + "'items':{'type':'string','minLength':10,'maxLength':5}}"
      })
  void shouldKeepSchemaErrorFlagOfArrayItemsWhetherOrNotTheArrayIsBounded(String schema) {
    MappedType mapped = map("tags", schema);

    assertThat(mapped.datatype()).startsWith("array[char[1..5], ");
    assertThat(mapped.reason()).isEqualTo(Reason.SCHEMA_ERROR);
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("minLength");
  }

  @Test
  void shouldKeepUuidReviewCommentOfUnboundedArrayItems() {
    MappedType mapped =
        map("ids", "{'type':'array','items':{'type':'string','format':'uuid','maxLength':10}}");

    assertThat(mapped.datatype()).isEqualTo("array[char[1..10], 1..10]");
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("uuid format cannot fit maxLength 10");
  }

  @Test
  void shouldKeepUuidReviewCommentOfBoundedArrayItems() {
    MappedType mapped =
        map(
            "ids",
            "{'type':'array','minItems':1,'maxItems':3,"
                + "'items':{'type':'string','format':'uuid','maxLength':10}}");

    assertThat(mapped.datatype()).isEqualTo("array[char[1..10], 1..3]");
    assertThat(mapped.flagged()).isTrue();
    assertThat(mapped.comment()).contains("uuid format cannot fit maxLength 10");
  }

  // --- nullable ---

  @Test
  void shouldIgnoreOpenApi30NullableFlag() {
    assertThat(map("n", "{'type':'integer','minimum':1,'maximum':9,'nullable':true}").datatype())
        .isEqualTo("int[1..9]");
  }

  @Test
  void shouldMapNullableTypeArrayToItsNonNullType() {
    // OpenAPI 3.1 / JSON Schema: "type": ["integer", "null"] is a nullable integer.
    MappedType mapped = map("n", "{'type':['integer','null'],'minimum':1,'maximum':9}");

    assertThat(mapped.datatype()).isEqualTo("int[1..9]");
    assertThat(mapped.flagged()).isFalse();
  }

  @Test
  void shouldMapNullableStringTypeArrayWithMaxLength() {
    assertThat(map("s", "{'type':['null','string'],'maxLength':12}").datatype())
        .isEqualTo("char[1..12]");
  }

  // --- $ref ---

  @ParameterizedTest
  @CsvSource({
    "#/components/schemas/LineItem,object[line_item]",
    "#/components/schemas/Address,object[address]",
    "#/$defs/PostalAddress,object[postal_address]",
    "#/definitions/Foo-Bar,object[foo_bar]",
    "common.yaml#/components/schemas/Address,object[address]"
  })
  void shouldMapRefToSnakeCasedObject(String ref, String expected) {
    MappedType mapped = map("x", "{'$ref':'" + ref + "'}");

    assertThat(mapped.datatype()).isEqualTo(expected);
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldLetRefWinOverSiblingType() {
    assertThat(map("x", "{'$ref':'#/components/schemas/Foo','type':'string'}").datatype())
        .isEqualTo("object[foo]");
  }

  @Test
  void shouldNotEmitFileExtensionInRefTargetForBareFileRef() {
    // a $ref to a whole file ("Address.yaml") has no '/' - the name must still be a legal
    // structure reference, not "address.yaml"
    assertThat(map("x", "{'$ref':'Address.yaml'}").datatype()).doesNotContain(".yaml");
  }

  // --- arrays ---

  @Test
  void shouldMapUnboundedArrayWithDefaultItemCount() {
    MappedType mapped = map("tags", "{'type':'array','items':{'type':'string','maxLength':5}}");

    assertThat(mapped.datatype()).isEqualTo("array[char[1..5], 1..10]");
    assertThat(mapped.reason()).isEqualTo(Reason.DEFAULT_RANGE);
  }

  @Test
  void shouldMapBoundedArrayOfRefs() {
    MappedType mapped =
        map(
            "lines",
            "{'type':'array','minItems':2,'maxItems':7,"
                + "'items':{'$ref':'#/components/schemas/OrderLine'}}");

    assertThat(mapped.datatype()).isEqualTo("array[object[order_line], 2..7]");
    assertThat(mapped.reason()).isEqualTo(Reason.DECLARED);
  }

  @Test
  void shouldKeepDefaultMaxWhenOnlyMinItemsGiven() {
    assertThat(map("a", "{'type':'array','minItems':3,'items':{'type':'boolean'}}").datatype())
        .isEqualTo("array[boolean, 3..10]");
  }

  @Test
  void shouldKeepDefaultMinWhenOnlyMaxItemsGiven() {
    assertThat(map("a", "{'type':'array','maxItems':4,'items':{'type':'boolean'}}").datatype())
        .isEqualTo("array[boolean, 1..4]");
  }

  @Test
  void shouldNotEmitInvertedItemRangeWhenMinItemsExceedsDefaultMax() {
    String datatype =
        map("a", "{'type':'array','minItems':20,'items':{'type':'boolean'}}").datatype();

    // array[boolean, 20..10] cannot be generated
    assertThat(datatype).doesNotContain("20..10");
  }

  @Test
  void shouldFallBackToDefaultStringItemsWhenItemsMissing() {
    assertThat(map("a", "{'type':'array'}").datatype()).isEqualTo("array[char[1..50], 1..10]");
  }

  @Test
  void shouldMapNestedArrays() {
    String datatype =
        map("grid", "{'type':'array','items':{'type':'array','items':{'type':'integer'}}}")
            .datatype();

    assertThat(datatype).isEqualTo("array[array[int[1..999999], 1..10], 1..10]");
  }

  @Test
  void shouldFlagBoundedArrayWhenItemMappingIsAGuess() {
    MappedType mapped =
        map("emails", "{'type':'array','minItems':1,'maxItems':3,'items':{'type':'string'}}");

    // items inherit the field name 'emails' -> no token match -> plain default string
    assertThat(mapped.datatype()).isEqualTo("array[char[1..50], 1..3]");
    MappedType hinted =
        map("email", "{'type':'array','minItems':1,'maxItems':3,'items':{'type':'string'}}");
    assertThat(hinted.datatype()).isEqualTo("array[email, 1..3]");
    assertThat(hinted.reason()).isEqualTo(Reason.NAME_HINT);
  }

  // --- composition / unknown ---

  @ParameterizedTest
  @ValueSource(strings = {"oneOf", "anyOf", "allOf"})
  void shouldFallBackToFlaggedDefaultStringForPropertyLevelComposition(String keyword) {
    MappedType mapped = map("poly", "{'" + keyword + "':[{'type':'string'},{'type':'integer'}]}");

    assertThat(mapped.datatype()).isEqualTo(STR_DEFAULT);
    assertThat(mapped.reason()).isEqualTo(Reason.UNKNOWN_TYPE);
    assertThat(mapped.comment()).isEqualTo("unrecognized source type, defaulted — verify");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{'type':'object'}", "{'type':'null'}", "{'type':'wat'}", "{}"})
  void shouldFallBackToFlaggedDefaultStringForUnknownOrMissingType(String schema) {
    MappedType mapped = map("thing", schema);

    assertThat(mapped.datatype()).isEqualTo(STR_DEFAULT);
    assertThat(mapped.reason()).isEqualTo(Reason.UNKNOWN_TYPE);
    assertThat(mapped.flagged()).isTrue();
  }

  // --- helpers ---

  private MappedType map(String field, String schemaJson) {
    return mapper.map(field, parse(schemaJson));
  }

  private static JsonNode parse(String json) {
    try {
      return JSON.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(json, e);
    }
  }

  /** Parses {@code type[min..max]} into {@code {min, max}}. */
  private static double[] range(String datatype) {
    String body = datatype.substring(datatype.indexOf('[') + 1, datatype.lastIndexOf(']'));
    String[] parts = body.split("\\.\\.", 2);
    return new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
  }
}
