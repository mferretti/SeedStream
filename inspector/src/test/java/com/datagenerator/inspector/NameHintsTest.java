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

import org.junit.jupiter.api.Test;

class NameHintsTest {

  // --- empty / blank / unmatched ---

  @Test
  void shouldReturnEmptyForNullName() {
    assertThat(NameHints.forFieldName(null)).isEmpty();
  }

  @Test
  void shouldReturnEmptyForBlankName() {
    assertThat(NameHints.forFieldName("   ")).isEmpty();
  }

  @Test
  void shouldReturnEmptyForUnmatchedName() {
    assertThat(NameHints.forFieldName("balance")).isEmpty();
  }

  // --- rule 1: email ---

  @Test
  void shouldMatchEmailForUserEmail() {
    assertThat(NameHints.forFieldName("userEmail")).contains("email");
  }

  @Test
  void shouldMatchEmailForEmailAddress() {
    // tokenizes to ["email", "address"] — email rule fires first
    assertThat(NameHints.forFieldName("emailAddress")).contains("email");
  }

  @Test
  void shouldMatchEmailForSnakeCaseEmail() {
    assertThat(NameHints.forFieldName("email")).contains("email");
  }

  // "email_verified_at" tokenizes to ["email", "verified", "at"] — contains "email" → matches
  @Test
  void shouldMatchEmailForEmailVerifiedAt() {
    assertThat(NameHints.forFieldName("email_verified_at")).contains("email");
  }

  // --- rule 2: phone_number ---

  @Test
  void shouldMatchPhoneNumberForPhoneField() {
    assertThat(NameHints.forFieldName("phone")).contains("phone_number");
  }

  @Test
  void shouldMatchPhoneNumberForMobilePhone() {
    assertThat(NameHints.forFieldName("mobilePhone")).contains("phone_number");
  }

  @Test
  void shouldMatchPhoneNumberForCellNumber() {
    assertThat(NameHints.forFieldName("cellNumber")).contains("phone_number");
  }

  @Test
  void shouldMatchPhoneNumberForMobileField() {
    assertThat(NameHints.forFieldName("mobile")).contains("phone_number");
  }

  // --- rule 3: first_name ---

  @Test
  void shouldMatchFirstNameForFirstName() {
    assertThat(NameHints.forFieldName("firstName")).contains("first_name");
  }

  @Test
  void shouldMatchFirstNameForNameFirst() {
    assertThat(NameHints.forFieldName("name_first")).contains("first_name");
  }

  // --- rule 4: last_name ---

  @Test
  void shouldMatchLastNameForLastName() {
    assertThat(NameHints.forFieldName("lastName")).contains("last_name");
  }

  @Test
  void shouldMatchLastNameForSurname() {
    // "surname" does not tokenize to "last" or "name" — should not match
    assertThat(NameHints.forFieldName("surname")).isEmpty();
  }

  @Test
  void shouldMatchLastNameForFamilyLastName() {
    assertThat(NameHints.forFieldName("familyLastName")).contains("last_name");
  }

  // --- rule 5: city ---

  @Test
  void shouldMatchCityForCity() {
    assertThat(NameHints.forFieldName("city")).contains("city");
  }

  @Test
  void shouldMatchCityForBirthCity() {
    assertThat(NameHints.forFieldName("birthCity")).contains("city");
  }

  // --- rule 6: country ---

  @Test
  void shouldMatchCountryForCountry() {
    assertThat(NameHints.forFieldName("country")).contains("country");
  }

  @Test
  void shouldMatchCountryForCountryCode() {
    assertThat(NameHints.forFieldName("countryCode")).contains("country");
  }

  // --- rule 7: street_name ---

  @Test
  void shouldMatchStreetNameForStreetName() {
    assertThat(NameHints.forFieldName("streetName")).contains("street_name");
  }

  @Test
  void shouldMatchStreetNameForStreet() {
    assertThat(NameHints.forFieldName("street")).contains("street_name");
  }

  // --- rule 8: address ---

  @Test
  void shouldMatchAddressForAddress() {
    assertThat(NameHints.forFieldName("address")).contains("address");
  }

  @Test
  void shouldMatchAddressForBillingAddress() {
    assertThat(NameHints.forFieldName("billingAddress")).contains("address");
  }

  // --- rule 9: postal_code ---

  @Test
  void shouldMatchPostalCodeForZipCode() {
    assertThat(NameHints.forFieldName("zipCode")).contains("postal_code");
  }

  @Test
  void shouldMatchPostalCodeForPostalCode() {
    assertThat(NameHints.forFieldName("postalCode")).contains("postal_code");
  }

  @Test
  void shouldMatchPostalCodeForZip() {
    assertThat(NameHints.forFieldName("zip")).contains("postal_code");
  }

  // --- rule 10: uuid ---

  @Test
  void shouldMatchUuidForUuid() {
    assertThat(NameHints.forFieldName("uuid")).contains("uuid");
  }

  @Test
  void shouldMatchUuidForGuid() {
    assertThat(NameHints.forFieldName("guid")).contains("uuid");
  }

  @Test
  void shouldMatchUuidForOrderUuid() {
    assertThat(NameHints.forFieldName("orderUuid")).contains("uuid");
  }

  // --- rule 11: url ---

  @Test
  void shouldMatchUrlForUrl() {
    assertThat(NameHints.forFieldName("url")).contains("url");
  }

  @Test
  void shouldMatchUrlForUri() {
    assertThat(NameHints.forFieldName("uri")).contains("url");
  }

  @Test
  void shouldMatchUrlForProfileUrl() {
    assertThat(NameHints.forFieldName("profileUrl")).contains("url");
  }

  @Test
  void shouldMatchUrlForResourceUri() {
    assertThat(NameHints.forFieldName("resourceUri")).contains("url");
  }

  // --- first-match-wins ordering ---

  @Test
  void shouldPreferEmailOverAddressWhenBothTokensPresent() {
    // "emailAddress" contains both "email" and "address"; email rule (1) fires before address (8)
    assertThat(NameHints.forFieldName("emailAddress")).contains("email");
  }
}
