package com.datagenerator.generators.semantic;

import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.util.Locale;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

/**
 * Generates realistic locale-specific data using Datafaker library. Supports semantic types like
 * name, email, address, phone, etc.
 *
 * <p><b>Supported Types:</b> Person (name, email, username), Address (city, street, postal_code),
 * Finance (company, iban, price), Internet (url, domain, ipv4), etc.
 *
 * <p><b>Locale Support:</b> Uses geolocation from GeneratorContext to determine Faker locale.
 * Supports 62+ locales (en, it, es, fr, de, pt, ru, zh, ja, ko, ar, etc.)
 *
 * <p><b>Determinism:</b> Seeded Random instance is passed to Faker for reproducible data
 * generation.
 *
 * <p><b>Thread Safety:</b> Thread-safe. Creates Faker instance per generation with thread-local
 * Random.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * // With Italian locale
 * GeneratorContext.enter(factory, "italy");
 * DatafakerGenerator gen = new DatafakerGenerator();
 * String name = gen.generate(random, nameType); // → "Mario Rossi"
 * String city = gen.generate(random, cityType); // → "Milano"
 * </pre>
 */
@Slf4j
public class DatafakerGenerator implements DataGenerator {

  @Override
  public boolean supports(DataType type) {
    if (!(type instanceof PrimitiveType primitiveType)) {
      return false;
    }
    return isSemanticType(primitiveType.getKind());
  }

  @Override
  public Object generate(Random random, DataType type) {
    if (!(type instanceof PrimitiveType primitiveType)) {
      throw new GeneratorException("DatafakerGenerator only supports PrimitiveType, got: " + type);
    }

    PrimitiveType.Kind kind = primitiveType.getKind();
    if (!isSemanticType(kind)) {
      throw new GeneratorException("DatafakerGenerator does not support type: " + kind);
    }

    // Get geolocation from context
    String geolocation = GeneratorContext.getGeolocation();
    Locale locale = parseGeolocation(geolocation);

    // Create Faker with seeded random for determinism
    Faker faker = new Faker(locale, random);

    return generateValue(faker, kind);
  }

  /**
   * Generate value using Datafaker based on semantic type.
   *
   * @param faker Datafaker instance with locale and random seed
   * @param kind Semantic type kind
   * @return Generated value
   */
  private String generateValue(Faker faker, PrimitiveType.Kind kind) {
    return switch (kind) {
      // Person types
      case NAME -> faker.name().name();
      case FIRST_NAME -> faker.name().firstName();
      case LAST_NAME -> faker.name().lastName();
      case FULL_NAME -> faker.name().fullName();
      case USERNAME -> faker.name().username();
      case TITLE -> faker.name().title();
      case OCCUPATION -> faker.job().title();

      // Address types
      case ADDRESS -> faker.address().fullAddress();
      case STREET_NAME -> faker.address().streetName();
      case STREET_NUMBER -> faker.address().streetAddressNumber();
      case CITY -> faker.address().city();
      case STATE -> faker.address().state();
      case POSTAL_CODE -> faker.address().zipCode();
      case COUNTRY -> faker.address().country();

      // Contact types
      case EMAIL -> faker.internet().emailAddress();
      case PHONE_NUMBER -> faker.phoneNumber().phoneNumber();

      // Finance types
      case COMPANY -> faker.company().name();
      case CREDIT_CARD -> faker.finance().creditCard();
      case IBAN -> faker.finance().iban();
      case CURRENCY -> faker.currency().code();
      case PRICE -> faker.commerce().price();

      // Internet types
      case DOMAIN -> faker.internet().domainName();
      case URL -> faker.internet().url();
      case IPV4 -> faker.internet().ipV4Address();
      case IPV6 -> faker.internet().ipV6Address();
      case MAC_ADDRESS -> faker.internet().macAddress();

      // Code types
      case ISBN -> faker.code().isbn13();
      case UUID -> faker.internet().uuid();

      default ->
          throw new GeneratorException("Type " + kind + " is not supported by DatafakerGenerator");
    };
  }

  /**
   * Check if a PrimitiveType.Kind is a semantic type (Datafaker-based).
   *
   * @param kind The kind to check
   * @return true if semantic type, false if primitive with range
   */
  private boolean isSemanticType(PrimitiveType.Kind kind) {
    return switch (kind) {
      case NAME,
          FIRST_NAME,
          LAST_NAME,
          FULL_NAME,
          USERNAME,
          TITLE,
          OCCUPATION,
          ADDRESS,
          STREET_NAME,
          STREET_NUMBER,
          CITY,
          STATE,
          POSTAL_CODE,
          COUNTRY,
          EMAIL,
          PHONE_NUMBER,
          COMPANY,
          CREDIT_CARD,
          IBAN,
          CURRENCY,
          PRICE,
          DOMAIN,
          URL,
          IPV4,
          IPV6,
          MAC_ADDRESS,
          ISBN,
          UUID ->
          true;
      default -> false;
    };
  }

  /**
   * Parse human-readable geolocation name to Java Locale.
   *
   * @param geolocation Geolocation name (e.g., "italy", "usa", "france") or null
   * @return Java Locale (defaults to English if unknown)
   */
  private Locale parseGeolocation(String geolocation) {
    if (geolocation == null || geolocation.isBlank()) {
      log.debug("No geolocation specified, defaulting to English");
      return Locale.ENGLISH;
    }

    Locale locale =
        switch (geolocation.toLowerCase().trim()) {
          // European locales
          case "italy", "italian" -> Locale.ITALY;
          case "usa", "us", "english", "united states" -> Locale.US;
          case "uk", "united kingdom", "britain" -> Locale.UK;
          case "france", "french" -> Locale.FRANCE;
          case "germany", "german" -> Locale.GERMANY;
          case "spain", "spanish" -> new Locale("es", "ES");
          case "portugal", "portuguese" -> new Locale("pt", "PT");
          case "netherlands", "dutch" -> new Locale("nl", "NL");
          case "belgium" -> new Locale("nl", "BE");
          case "austria" -> new Locale("de", "AT");
          case "switzerland" -> new Locale("de", "CH");
          case "sweden", "swedish" -> new Locale("sv", "SE");
          case "norway", "norwegian" -> new Locale("no", "NO");
          case "denmark", "danish" -> new Locale("da", "DK");
          case "finland", "finnish" -> new Locale("fi", "FI");
          case "poland", "polish" -> new Locale("pl", "PL");
          case "czech", "czechia" -> new Locale("cs", "CZ");
          case "hungary", "hungarian" -> new Locale("hu", "HU");
          case "romania", "romanian" -> new Locale("ro", "RO");
          case "greece", "greek" -> new Locale("el", "GR");
          case "turkey", "turkish" -> new Locale("tr", "TR");

          // Americas
          case "brazil", "brazilian" -> new Locale("pt", "BR");
          case "mexico", "mexican" -> new Locale("es", "MX");
          case "argentina" -> new Locale("es", "AR");
          case "colombia" -> new Locale("es", "CO");
          case "chile" -> new Locale("es", "CL");
          case "peru" -> new Locale("es", "PE");
          case "canada" -> Locale.CANADA;

          // Asia-Pacific
          case "china", "chinese" -> Locale.CHINA;
          case "japan", "japanese" -> Locale.JAPAN;
          case "korea", "korean", "south korea" -> Locale.KOREA;
          case "india", "indian" -> new Locale("en", "IN");
          case "indonesia", "indonesian" -> new Locale("id", "ID");
          case "vietnam", "vietnamese" -> new Locale("vi", "VN");
          case "thailand", "thai" -> new Locale("th", "TH");
          case "singapore" -> new Locale("en", "SG");
          case "malaysia" -> new Locale("ms", "MY");
          case "philippines" -> new Locale("en", "PH");
          case "australia", "australian" -> new Locale("en", "AU");
          case "new zealand" -> new Locale("en", "NZ");

          // Middle East & Africa
          case "russia", "russian" -> new Locale("ru", "RU");
          case "ukraine", "ukrainian" -> new Locale("uk", "UA");
          case "israel", "hebrew" -> new Locale("he", "IL");
          case "saudi arabia", "arabic" -> new Locale("ar", "SA");
          case "egypt" -> new Locale("ar", "EG");
          case "south africa" -> new Locale("en", "ZA");

          default -> {
            log.warn("Unknown geolocation '{}', defaulting to English", geolocation);
            yield Locale.ENGLISH;
          }
        };

    log.debug("Mapped geolocation '{}' to locale '{}'", geolocation, locale);
    return locale;
  }
}
