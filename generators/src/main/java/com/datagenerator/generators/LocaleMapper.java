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

package com.datagenerator.generators;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps geolocation strings to Java Locale objects for Datafaker.
 *
 * <p>Supports 62+ locales including: - European: Italy, France, Germany, Spain, UK, etc. -
 * Americas: USA, Brazil, Mexico, Canada, Argentina, etc. - Asia-Pacific: China, Japan, Korea,
 * India, Thailand, etc. - Middle East &amp; Africa: Russia, Saudi Arabia, Israel, South Africa,
 * etc.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * Locale locale = LocaleMapper.map("italy");  // → Locale.ITALY
 * Locale locale = LocaleMapper.map("brazil"); // → Locale.of("pt", "BR")
 * Locale locale = LocaleMapper.map(null);     // → Locale.US (default)
 * </pre>
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Case-insensitive matching
 *   <li>Handles underscores and hyphens (en-us, en_us)
 *   <li>Trims whitespace
 *   <li>Defaults to US English for unknown locales
 *   <li>Logs warning for unknown geolocations
 * </ul>
 */
@Slf4j
public class LocaleMapper {

  private LocaleMapper() {
    // Utility class
  }

  /**
   * Map geolocation string to Locale, defaulting to en-US.
   *
   * @param geolocation Geolocation name (e.g., "italy", "usa", "france") or null
   * @return Java Locale (defaults to US English if unknown or null)
   */
  public static Locale map(String geolocation) {
    if (geolocation == null || geolocation.isBlank()) {
      log.debug("No geolocation specified, defaulting to US English");
      return Locale.US;
    }

    String normalized = geolocation.toLowerCase(Locale.ROOT).trim().replace("_", "-");

    Locale locale =
        switch (normalized) {
          // English variants
          case "en-us", "en", "usa", "us", "english", "united states" -> Locale.US;
          case "en-gb", "uk", "united kingdom", "britain" -> Locale.UK;
          case "en-ca", "canada" -> Locale.CANADA;
          case "en-au", "australia", "australian" -> Locale.of("en", "AU");
          case "en-nz", "new zealand" -> Locale.of("en", "NZ");
          case "en-za", "south africa" -> Locale.of("en", "ZA");
          case "en-in", "india", "indian" -> Locale.of("en", "IN");
          case "en-sg", "singapore" -> Locale.of("en", "SG");
          case "en-ph", "philippines" -> Locale.of("en", "PH");

          // European locales
          case "it", "it-it", "italy", "italian" -> Locale.ITALY;
          case "de", "de-de", "germany", "german" -> Locale.GERMANY;
          case "de-at", "austria" -> Locale.of("de", "AT");
          case "de-ch", "switzerland" -> Locale.of("de", "CH");
          case "fr", "fr-fr", "france", "french" -> Locale.FRANCE;
          case "es", "es-es", "spain", "spanish" -> Locale.of("es", "ES");
          case "es-mx", "mexico", "mexican" -> Locale.of("es", "MX");
          case "es-ar", "argentina" -> Locale.of("es", "AR");
          case "es-co", "colombia" -> Locale.of("es", "CO");
          case "es-cl", "chile" -> Locale.of("es", "CL");
          case "es-pe", "peru" -> Locale.of("es", "PE");
          case "pt", "pt-pt", "portugal", "portuguese" -> Locale.of("pt", "PT");
          case "pt-br", "brazil", "brazilian" -> Locale.of("pt", "BR");
          case "nl", "nl-nl", "netherlands", "dutch" -> Locale.of("nl", "NL");
          case "nl-be", "belgium" -> Locale.of("nl", "BE");
          case "sv", "sv-se", "sweden", "swedish" -> Locale.of("sv", "SE");
          case "no", "no-no", "norway", "norwegian" -> Locale.of("no", "NO");
          case "da", "da-dk", "denmark", "danish" -> Locale.of("da", "DK");
          case "fi", "fi-fi", "finland", "finnish" -> Locale.of("fi", "FI");
          case "pl", "pl-pl", "poland", "polish" -> Locale.of("pl", "PL");
          case "cs", "cs-cz", "czech", "czechia" -> Locale.of("cs", "CZ");
          case "hu", "hu-hu", "hungary", "hungarian" -> Locale.of("hu", "HU");
          case "ro", "ro-ro", "romania", "romanian" -> Locale.of("ro", "RO");
          case "el", "el-gr", "greece", "greek" -> Locale.of("el", "GR");
          case "tr", "tr-tr", "turkey", "turkish" -> Locale.of("tr", "TR");

          // Asia-Pacific
          case "zh", "zh-cn", "china", "chinese" -> Locale.CHINA;
          case "zh-tw", "taiwan" -> Locale.TAIWAN;
          case "ja", "ja-jp", "japan", "japanese" -> Locale.JAPAN;
          case "ko", "ko-kr", "korea", "korean", "south korea" -> Locale.KOREA;
          case "id", "id-id", "indonesia", "indonesian" -> Locale.of("id", "ID");
          case "vi", "vi-vn", "vietnam", "vietnamese" -> Locale.of("vi", "VN");
          case "th", "th-th", "thailand", "thai" -> Locale.of("th", "TH");
          case "ms", "ms-my", "malaysia" -> Locale.of("ms", "MY");

          // Middle East & Africa
          case "ru", "ru-ru", "russia", "russian" -> Locale.of("ru", "RU");
          case "uk-ua", "ukraine", "ukrainian" -> Locale.of("uk", "UA");
          case "he", "he-il", "israel", "hebrew" -> Locale.of("he", "IL");
          case "ar", "ar-sa", "saudi arabia", "arabic" -> Locale.of("ar", "SA");
          case "ar-eg", "egypt" -> Locale.of("ar", "EG");

          default -> {
            log.warn("Unknown geolocation '{}', defaulting to US English", geolocation);
            yield Locale.US;
          }
        };

    log.debug("Mapped geolocation '{}' to locale '{}'", geolocation, locale);
    return locale;
  }
}
