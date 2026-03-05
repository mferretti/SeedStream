package com.datagenerator.generators.semantic;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DatafakerGeneratorTest {
  private DatafakerGenerator generator;
  private DataGeneratorFactory factory;
  private Random random;

  @BeforeEach
  void setUp() {
    generator = new DatafakerGenerator();
    random = new Random(12345L);

    // Create factory with mock registry for context
    StructureRegistry registry = new StructureRegistry((name, path, reg) -> Map.of());
    factory = new DataGeneratorFactory(registry, Paths.get("test"));
  }

  /** Helper to generate with context. */
  private Object generateWithContext(String geolocation, DataType dataType) {
    try (var ctx = GeneratorContext.enter(factory, geolocation)) {
      return generator.generate(random, dataType);
    }
  }

  @Test
  void shouldSupportSemanticTypes() {
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.NAME, null, null))).isTrue();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.EMAIL, null, null)))
        .isTrue();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.PHONE_NUMBER, null, null)))
        .isTrue();
  }

  @Test
  void shouldNotSupportPrimitiveTypesWithRanges() {
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.CHAR, "1", "10"))).isFalse();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.INT, "1", "100"))).isFalse();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null)))
        .isFalse();
  }

  @Test
  void shouldNotSupportNonPrimitiveTypes() {
    assertThat(generator.supports(new ObjectType("user"))).isFalse();
  }

  @Test
  void shouldGenerateDeterministicDataWithSameSeed() {
    Random random1 = new Random(12345L);
    Random random2 = new Random(12345L);
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);

    try (var ctx = GeneratorContext.enter(factory, "italy")) {
      String name1 = (String) generator.generate(random1, nameType);
      random2.setSeed(12345L); // Reset second random to same seed
      String name2 = (String) generator.generate(random2, nameType);

      assertThat(name1).isEqualTo(name2);
    }
  }

  @Test
  void shouldGenerateNameForItalianLocale() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("italy", nameType);

    assertThat(name).isNotNull();
    assertThat(name).isNotEmpty();
    assertThat(name).matches("^[A-Za-zÀ-ÖØ-öø-ÿ\\s'-]+$"); // Italian name characters
  }

  @Test
  void shouldGenerateEmailAddress() {
    PrimitiveType emailType = new PrimitiveType(PrimitiveType.Kind.EMAIL, null, null);
    String email = (String) generateWithContext("usa", emailType);

    assertThat(email).isNotNull();
    assertThat(email).contains("@");
    assertThat(email).matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$");
  }

  @Test
  void shouldGeneratePhoneNumber() {
    PrimitiveType phoneType = new PrimitiveType(PrimitiveType.Kind.PHONE_NUMBER, null, null);
    String phone = (String) generateWithContext("usa", phoneType);

    assertThat(phone).isNotNull();
    assertThat(phone).isNotEmpty();
    // Phone numbers can have various formats with digits, spaces, parentheses, hyphens
    assertThat(phone).matches("^[\\d\\s()+-]+$");
  }

  @Test
  void shouldGenerateAddress() {
    PrimitiveType addressType = new PrimitiveType(PrimitiveType.Kind.ADDRESS, null, null);
    String address = (String) generateWithContext("usa", addressType);

    assertThat(address).isNotNull();
    assertThat(address).isNotEmpty();
  }

  @Test
  void shouldGenerateCompanyName() {
    PrimitiveType companyType = new PrimitiveType(PrimitiveType.Kind.COMPANY, null, null);
    String company = (String) generateWithContext("usa", companyType);

    assertThat(company).isNotNull();
    assertThat(company).isNotEmpty();
  }

  @Test
  void shouldGenerateURL() {
    PrimitiveType urlType = new PrimitiveType(PrimitiveType.Kind.URL, null, null);
    String url = (String) generateWithContext("usa", urlType);

    assertThat(url).isNotNull();
    assertThat(url).matches("^https?://.*");
  }

  @Test
  void shouldGenerateUUID() {
    PrimitiveType uuidType = new PrimitiveType(PrimitiveType.Kind.UUID, null, null);
    String uuid = (String) generateWithContext("usa", uuidType);

    assertThat(uuid).isNotNull();
    assertThat(uuid).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  }

  @Test
  void shouldGenerateIPv4Address() {
    PrimitiveType ipv4Type = new PrimitiveType(PrimitiveType.Kind.IPV4, null, null);
    String ipv4 = (String) generateWithContext("usa", ipv4Type);

    assertThat(ipv4).isNotNull();
    assertThat(ipv4).matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
  }

  @Test
  void shouldGenerateValidISBN() {
    PrimitiveType isbnType = new PrimitiveType(PrimitiveType.Kind.ISBN, null, null);
    String isbn = (String) generateWithContext("usa", isbnType);

    assertThat(isbn).isNotNull();
    assertThat(isbn).matches("^\\d{13}$"); // ISBN-13 format
  }

  @Test
  void shouldGenerateIBAN() {
    PrimitiveType ibanType = new PrimitiveType(PrimitiveType.Kind.IBAN, null, null);
    String iban = (String) generateWithContext("germany", ibanType);

    assertThat(iban).isNotNull();
    assertThat(iban).isNotEmpty();
    assertThat(iban).matches("^[A-Z]{2}[0-9]{2}[A-Z0-9]+$"); // Basic IBAN format
  }

  @Test
  void shouldFallbackToEnglishForUnknownGeolocation() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext("unknown_locale_12345", nameType);

    // Should generate valid name (English fallback)
    assertThat(name).isNotNull();
    assertThat(name).isNotEmpty();
  }

  @Test
  void shouldFallbackToEnglishForNullGeolocation() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);
    String name = (String) generateWithContext(null, nameType);

    // Should generate valid name (English fallback)
    assertThat(name).isNotNull();
    assertThat(name).isNotEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = PrimitiveType.Kind.class,
      names = {
        "NAME",
        "FIRST_NAME",
        "LAST_NAME",
        "EMAIL",
        "PHONE_NUMBER",
        "ADDRESS",
        "CITY",
        "COMPANY",
        "URL",
        "UUID"
      })
  void shouldGenerateValidDataForAllSupportedSemanticTypes(PrimitiveType.Kind kind) {
    PrimitiveType type = new PrimitiveType(kind, null, null);
    String value = (String) generateWithContext("usa", type);

    assertThat(value).isNotNull();
    assertThat(value).isNotEmpty();
  }

  @Test
  void shouldGenerateDifferentValuesForDifferentSeeds() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);

    Random random1 = new Random(12345L);
    Random random2 = new Random(99999L);

    try (var ctx = GeneratorContext.enter(factory, "italy")) {
      String name1 = (String) generator.generate(random1, nameType);
      String name2 = (String) generator.generate(random2, nameType);

      // Different seeds should produce different names (with very high probability)
      assertThat(name1).isNotEqualTo(name2);
    }
  }

  @Test
  void shouldHandleMultipleLocales() {
    PrimitiveType nameType = new PrimitiveType(PrimitiveType.Kind.NAME, null, null);

    try (var ctx = GeneratorContext.enter(factory, "italy")) {
      String italianName = (String) generator.generate(random, nameType);
      assertThat(italianName).isNotNull();
    }

    Random newRandom = new Random(12345L);
    try (var ctx = GeneratorContext.enter(factory, "japan")) {
      String japaneseName = (String) generator.generate(newRandom, nameType);
      assertThat(japaneseName).isNotNull();
    }

    // Both should be valid names (content verification would require locale-specific knowledge)
  }

  @Test
  void shouldGeneratePrice() {
    PrimitiveType priceType = new PrimitiveType(PrimitiveType.Kind.PRICE, null, null);
    String price = (String) generateWithContext("usa", priceType);

    assertThat(price).isNotNull();
    assertThat(price).isNotEmpty(); // Price format can vary
  }

  @Test
  void shouldGenerateMacAddress() {
    PrimitiveType macType = new PrimitiveType(PrimitiveType.Kind.MAC_ADDRESS, null, null);
    String mac = (String) generateWithContext("usa", macType);

    assertThat(mac).isNotNull();
    assertThat(mac).matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"); // MAC address format
  }
}
