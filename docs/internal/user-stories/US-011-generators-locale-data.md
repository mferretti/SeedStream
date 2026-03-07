# US-011: Locale-Specific Data Generation

**Status**: ✅ Complete  
**Priority**: P1 (High)  
**Phase**: 2 - Data Generation  
**Dependencies**: US-010  
**Completion Date**: March 2026

---

## User Story

As a **international QA engineer**, I want **locale-specific test data** so that **I can generate Italian names for Italian tests, Japanese addresses for Japanese tests, etc., making tests more realistic for each region**.

---

## Acceptance Criteria

- ✅ Support for 62+ locales including en-US, en-GB, it, de, fr, es, pt-BR, ja, zh-CN
- ✅ LocaleMapper converts geolocation strings to Java Locale objects
- ✅ Case-insensitive locale mapping (e.g., "ITALY", "Italy", "italy" all map to Locale.ITALY)
- ✅ Handle underscores and hyphens in locale strings (e.g., "en-US", "en_US")
- ✅ Default to en-US for unknown geolocations
- ✅ DatafakerGenerator uses mapped Locale when creating Faker instance
- ✅ Italian geolocation produces Italian names, addresses, phone numbers
- ✅ Japanese geolocation produces Japanese names and addresses
- ✅ Logging shows which locale is used for generation

---

## Implementation Notes

### LocaleMapper
Static utility class that maps strings to Locale objects:
- Map common locale strings to Java Locale constants
- Support country names ("italy", "japan") and codes ("it", "ja")
- Support language-region combinations ("en-us", "pt-br")
- Normalize input (lowercase, replace underscores with hyphens)
- Return Locale.US for unmapped strings

### Locale Coverage
Map at least these locales:
- **English**: en-US, en-GB, en-CA, en-AU
- **European**: it (Italy), de (Germany), fr (France), es (Spain), pt (Portugal)
- **Americas**: pt-BR (Brazil), es-MX (Mexico), es-AR (Argentina)
- **Asian**: ja (Japan), zh-CN (China), ko (Korea), th (Thailand), vi (Vietnam)
- **Other**: ar (Arabic), he (Hebrew), ru (Russia), pl (Poland)

### DatafakerGenerator Integration
Update constructor to use LocaleMapper:
```java
public DatafakerGenerator(PrimitiveType.Kind kind, String geolocation) {
    this.kind = kind;
    Locale locale = LocaleMapper.map(geolocation);
    this.faker = new Faker(locale);
}
```

---

## Testing Requirements

### Unit Tests (LocaleMapper)
- Map common locales correctly (italy → Locale.ITALY)
- Case-insensitive mapping works
- Underscore/hyphen handling works
- Default to en-US for unknown locales
- Empty/null input returns en-US

### Integration Tests (DatafakerGenerator)
- Italian geolocation generates Italian-looking names
- Japanese geolocation generates Japanese-looking names
- Brazilian geolocation generates Portuguese names
- Default geolocation generates English names
- Phone numbers respect locale format

### Visual Verification
Generate 10 records for each locale and manually verify they look appropriate

---

## Definition of Done

- [ ] LocaleMapper class implemented with 62+ locale mappings
- [ ] DatafakerGenerator integrated with LocaleMapper
- [ ] Case-insensitive and format-flexible locale matching
- [ ] Default to en-US for unknown locales
- [ ] Unit tests for LocaleMapper
- [ ] Integration tests for locale-specific generation
- [ ] Visual verification of generated data
- [ ] Test coverage >= 90%
- [ ] Documentation updated with supported locales
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
