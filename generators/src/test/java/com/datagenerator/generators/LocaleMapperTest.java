package com.datagenerator.generators;

import static org.assertj.core.api.Assertions.*;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class LocaleMapperTest {

  @Test
  void shouldMapCommonLocales() {
    assertThat(LocaleMapper.map("italy")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("it")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("japan")).isEqualTo(Locale.JAPAN);
    assertThat(LocaleMapper.map("brazil")).isEqualTo(Locale.of("pt", "BR"));
    assertThat(LocaleMapper.map("france")).isEqualTo(Locale.FRANCE);
    assertThat(LocaleMapper.map("germany")).isEqualTo(Locale.GERMANY);
  }

  @Test
  void shouldBeCaseInsensitive() {
    assertThat(LocaleMapper.map("ITALY")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("Italy")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("iTaLy")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("BRAZIL")).isEqualTo(Locale.of("pt", "BR"));
  }

  @Test
  void shouldDefaultToEnUS() {
    assertThat(LocaleMapper.map(null)).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("")).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("   ")).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("unknown")).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("some-random-place")).isEqualTo(Locale.US);
  }

  @Test
  void shouldHandleUnderscoresAndHyphens() {
    assertThat(LocaleMapper.map("en-us")).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("en_us")).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("pt-br")).isEqualTo(Locale.of("pt", "BR"));
    assertThat(LocaleMapper.map("pt_br")).isEqualTo(Locale.of("pt", "BR"));
    assertThat(LocaleMapper.map("zh-cn")).isEqualTo(Locale.CHINA);
    assertThat(LocaleMapper.map("zh_cn")).isEqualTo(Locale.CHINA);
  }

  @Test
  void shouldMapEnglishVariants() {
    assertThat(LocaleMapper.map("usa")).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("us")).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("english")).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("united states")).isEqualTo(Locale.US);
    assertThat(LocaleMapper.map("uk")).isEqualTo(Locale.UK);
    assertThat(LocaleMapper.map("united kingdom")).isEqualTo(Locale.UK);
    assertThat(LocaleMapper.map("britain")).isEqualTo(Locale.UK);
    assertThat(LocaleMapper.map("canada")).isEqualTo(Locale.CANADA);
  }

  @Test
  void shouldMapEuropeanLocales() {
    assertThat(LocaleMapper.map("italy")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("italian")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("france")).isEqualTo(Locale.FRANCE);
    assertThat(LocaleMapper.map("french")).isEqualTo(Locale.FRANCE);
    assertThat(LocaleMapper.map("germany")).isEqualTo(Locale.GERMANY);
    assertThat(LocaleMapper.map("german")).isEqualTo(Locale.GERMANY);
    assertThat(LocaleMapper.map("spain")).isEqualTo(Locale.of("es", "ES"));
    assertThat(LocaleMapper.map("spanish")).isEqualTo(Locale.of("es", "ES"));
    assertThat(LocaleMapper.map("austria")).isEqualTo(Locale.of("de", "AT"));
    assertThat(LocaleMapper.map("switzerland")).isEqualTo(Locale.of("de", "CH"));
  }

  @Test
  void shouldMapAmericasLocales() {
    assertThat(LocaleMapper.map("brazil")).isEqualTo(Locale.of("pt", "BR"));
    assertThat(LocaleMapper.map("brazilian")).isEqualTo(Locale.of("pt", "BR"));
    assertThat(LocaleMapper.map("mexico")).isEqualTo(Locale.of("es", "MX"));
    assertThat(LocaleMapper.map("mexican")).isEqualTo(Locale.of("es", "MX"));
    assertThat(LocaleMapper.map("argentina")).isEqualTo(Locale.of("es", "AR"));
    assertThat(LocaleMapper.map("colombia")).isEqualTo(Locale.of("es", "CO"));
    assertThat(LocaleMapper.map("chile")).isEqualTo(Locale.of("es", "CL"));
  }

  @Test
  void shouldMapAsianLocales() {
    assertThat(LocaleMapper.map("china")).isEqualTo(Locale.CHINA);
    assertThat(LocaleMapper.map("chinese")).isEqualTo(Locale.CHINA);
    assertThat(LocaleMapper.map("japan")).isEqualTo(Locale.JAPAN);
    assertThat(LocaleMapper.map("japanese")).isEqualTo(Locale.JAPAN);
    assertThat(LocaleMapper.map("korea")).isEqualTo(Locale.KOREA);
    assertThat(LocaleMapper.map("korean")).isEqualTo(Locale.KOREA);
    assertThat(LocaleMapper.map("south korea")).isEqualTo(Locale.KOREA);
    assertThat(LocaleMapper.map("india")).isEqualTo(Locale.of("en", "IN"));
    assertThat(LocaleMapper.map("thailand")).isEqualTo(Locale.of("th", "TH"));
  }

  @Test
  void shouldMapMiddleEastAndAfricanLocales() {
    assertThat(LocaleMapper.map("russia")).isEqualTo(Locale.of("ru", "RU"));
    assertThat(LocaleMapper.map("russian")).isEqualTo(Locale.of("ru", "RU"));
    assertThat(LocaleMapper.map("ukraine")).isEqualTo(Locale.of("uk", "UA"));
    assertThat(LocaleMapper.map("ukrainian")).isEqualTo(Locale.of("uk", "UA"));
    assertThat(LocaleMapper.map("uk-ua")).isEqualTo(Locale.of("uk", "UA"));
    assertThat(LocaleMapper.map("israel")).isEqualTo(Locale.of("he", "IL"));
    assertThat(LocaleMapper.map("saudi arabia")).isEqualTo(Locale.of("ar", "SA"));
    assertThat(LocaleMapper.map("egypt")).isEqualTo(Locale.of("ar", "EG"));
    assertThat(LocaleMapper.map("south africa")).isEqualTo(Locale.of("en", "ZA"));
  }

  @Test
  void shouldMapISOLocaleCodes() {
    assertThat(LocaleMapper.map("it")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("de")).isEqualTo(Locale.GERMANY);
    assertThat(LocaleMapper.map("fr")).isEqualTo(Locale.FRANCE);
    assertThat(LocaleMapper.map("es")).isEqualTo(Locale.of("es", "ES"));
    assertThat(LocaleMapper.map("pt")).isEqualTo(Locale.of("pt", "PT"));
    assertThat(LocaleMapper.map("ja")).isEqualTo(Locale.JAPAN);
    assertThat(LocaleMapper.map("ko")).isEqualTo(Locale.KOREA);
    assertThat(LocaleMapper.map("zh")).isEqualTo(Locale.CHINA);
  }

  @Test
  void shouldHandleWhitespace() {
    assertThat(LocaleMapper.map("  italy  ")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("  brazil  ")).isEqualTo(Locale.of("pt", "BR"));
    assertThat(LocaleMapper.map("\titaly\t")).isEqualTo(Locale.ITALY);
    assertThat(LocaleMapper.map("south korea")).isEqualTo(Locale.KOREA); // Space in name
  }

  @Test
  void shouldHandleVariousAustralianAndOceaniaSpellings() {
    assertThat(LocaleMapper.map("australia")).isEqualTo(Locale.of("en", "AU"));
    assertThat(LocaleMapper.map("australian")).isEqualTo(Locale.of("en", "AU"));
    assertThat(LocaleMapper.map("en-au")).isEqualTo(Locale.of("en", "AU"));
    assertThat(LocaleMapper.map("new zealand")).isEqualTo(Locale.of("en", "NZ"));
  }

  @Test
  void shouldMapPortugueseVariants() {
    assertThat(LocaleMapper.map("portugal")).isEqualTo(Locale.of("pt", "PT"));
    assertThat(LocaleMapper.map("portuguese")).isEqualTo(Locale.of("pt", "PT"));
    assertThat(LocaleMapper.map("brazil")).isEqualTo(Locale.of("pt", "BR"));
    assertThat(LocaleMapper.map("pt-br")).isEqualTo(Locale.of("pt", "BR"));
  }
}
