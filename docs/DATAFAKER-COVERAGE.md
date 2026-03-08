# Datafaker Coverage Analysis

**Project:** SeedStream  
**Datafaker Version:** 2.5.4  
**Analysis Date:** March 8, 2026

---

## Currently Implemented Types (28 total)

### Person & Identity (7 types)
- ✅ `NAME` → `faker.name().name()`
- ✅ `FIRST_NAME` → `faker.name().firstName()`
- ✅ `LAST_NAME` → `faker.name().lastName()`
- ✅ `FULL_NAME` → `faker.name().fullName()`
- ✅ `USERNAME` → custom (firstName + number)
- ✅ `TITLE` → `faker.name().title()`
- ✅ `OCCUPATION` → `faker.job().title()`

###Address (7 types)
- ✅ `ADDRESS` → `faker.address().fullAddress()`
- ✅ `STREET_NAME` → `faker.address().streetName()`
- ✅ `STREET_NUMBER` → `faker.address().streetAddressNumber()`
- ✅ `CITY` → `faker.address().city()`
- ✅ `STATE` → `faker.address().state()`
- ✅ `POSTAL_CODE` → `faker.address().zipCode()`
- ✅ `COUNTRY` → `faker.address().country()`

### Contact (2 types)
- ✅ `EMAIL` → `faker.internet().emailAddress()`
- ✅ `PHONE_NUMBER` → `faker.phoneNumber().phoneNumber()`

### Finance & Business (5 types)
- ✅ `COMPANY` → `faker.company().name()`
- ✅ `CREDIT_CARD` → `faker.finance().creditCard()`
- ✅ `IBAN` → `faker.finance().iban()`
- ✅ `CURRENCY` → `faker.money().currencyCode()`
- ✅ `PRICE` → `faker.commerce().price()`

### Internet (5 types)
- ✅ `DOMAIN` → `faker.internet().domainName()`
- ✅ `URL` → `faker.internet().url()`
- ✅ `IPV4` → `faker.internet().ipV4Address()`
- ✅ `IPV6` → `faker.internet().ipV6Address()`
- ✅ `MAC_ADDRESS` → `faker.internet().macAddress()`

### Codes & Identifiers (2 types)
- ✅ `ISBN` → `faker.code().isbn13()`
- ✅ `UUID` → `faker.internet().uuid()`

---

## Available But NOT Implemented

### Person & Identity Extensions
- ❌ `PASSWORD` → `faker.internet().password()`
- ❌ `SSN` / `SOCIAL_SECURITY` → `faker.idNumber().valid()` or `ssnValid()`
- ❌ `BLOOD_TYPE` → `faker.medical().bloodType()`
- ❌ `PREFIX` → `faker.name().prefix()` (Mr., Mrs., Dr., etc.)
- ❌ `SUFFIX` → `faker.name().suffix()` (Jr., Sr., III, etc.)

### Address Extensions
- ❌ `LATITUDE` → `faker.address().latitude()`
- ❌ `LONGITUDE` → `faker.address().longitude()`
- ❌ `BUILDING_NUMBER` → `faker.address().buildingNumber()`
- ❌ `STREET_ADDRESS` → `faker.address().streetAddress()`
- ❌ `SECONDARY_ADDRESS` → `faker.address().secondaryAddress()` (Apt, Suite)
- ❌ `CITY_PREFIX` → `faker.address().cityPrefix()`
- ❌ `CITY_SUFFIX` → `faker.address().citySuffix()`
- ❌ `COUNTRY_CODE` → `faker.address().countryCode()` (US, IT, FR, etc.)
- ❌ `TIME_ZONE` → `faker.address().timeZone()`

### Finance & Business Extensions
- ❌ `BIC` / `SWIFT` → `faker.finance().bic()`
- ❌ `CREDIT_CARD_CVV` → `faker.finance().creditCardCvv()`
- ❌ `CREDIT_CARD_TYPE` → `faker.finance().creditCardType()` (Visa, Mastercard)
- ❌ `STOCK_MARKET` → `faker.stock().nyseSymbol()`, `nasdaqSymbol()`
- ❌ `BITCOIN_ADDRESS` → `faker.crypto().sha256()` or Bitcoin
- ❌ `COMPANY_SUFFIX` → `faker.company().suffix()` (Inc., LLC, Corp.)
- ❌ `INDUSTRY` → `faker.company().industry()`
- ❌ `PROFESSION` → `faker.company().profession()`
- ❌ `BUZZWORD` → `faker.company().buzzword()`
- ❌ `CATCHPHRASE` → `faker.company().catchPhrase()`
- ❌ `BS` → `faker.company().bs()` (business speak)

### Internet Extensions
- ❌ `SLUG` → `faker.internet().slug()`
- ❌ `USER_AGENT` → `faker.internet().userAgentAny()`
- ❌ `HTTP_METHOD` → "GET", "POST", "PUT", "DELETE", etc.
- ❌ `HTTP_STATUS_CODE` → 200, 404, 500, etc.
- ❌ `PORT` → `faker.internet().port()`
- ❌ `PUBLIC_IPV4` → `faker.internet().publicIpV4Address()`
- ❌ `PRIVATE_IPV4` → `faker.internet().privateIpV4Address()`

### Commerce (entire category missing)
- ❌ `PRODUCT_NAME` → `faker.commerce().productName()`
- ❌ `DEPARTMENT` → `faker.commerce().department()`
- ❌ `MATERIAL` → `faker.commerce().material()`
- ❌ `PROMOTION_CODE` → `faker.commerce().promotionCode()`
- ❌ `COLOR` → `faker.color().name()`

### Code & Identifiers Extensions
- ❌ `EAN8` → `faker.code().ean8()`
- ❌ `EAN13` → `faker.code().ean13()`
- ❌ `ISBN10` → `faker.code().isbn10()`
- ❌ `IMEI` → `faker.code().imei()`
- ❌ `ASIN` → `faker.code().asin()`
- ❌ `BARCODE` → Generic barcode generation

### DateTime (entire category missing)
- ❌ `DAY_OF_WEEK` → `faker.date().dayOfWeek()`
- ❌ `MONTH_NAME` → `faker.date().monthName()`
- ❌ `TIMEZONE_NAME` → `faker.date().timeZoneName()`
- ❌ `PAST_DATE` → `faker.date().past()`
- ❌ `FUTURE_DATE` → `faker.date().future()`
- ❌ `BIRTHDAY` → `faker.date().birthday()`

### Medical (entire category missing)
- ❌ `MEDICINE_NAME` → `faker.medical().medicineName()`
- ❌ `DISEASE` → `faker.medical().diseaseName()`
- ❌ `HOSPITAL` → `faker.medical().hospitalName()`
- ❌ `SYMPTOMS` → `faker.medical().symptoms()`

### Education (entire category missing)
- ❌ `UNIVERSITY` → `faker.university().name()`
- ❌ `DEGREE` → `faker.university().degree()`  
- ❌ `MAJOR` → `faker.university().major()`

### Entertainment (entire category missing)
- ❌ `BOOK_TITLE` → `faker.book().title()`
- ❌ `BOOK_AUTHOR` → `faker.book().author()`
- ❌ `BOOK_GENRE` → `faker.book().genre()`
- ❌ `MOVIE_TITLE` → `faker.movie().title()`
- ❌ `MUSIC_GENRE` → `faker.music().genre()`
- ❌ `ARTIST` → `faker.artist().name()`

### Games & Pop Culture (entire category missing)
- ❌ `POKEMON` → `faker.pokemon().name()`
- ❌ `SUPERHERO` → `faker.superhero().name()`
- ❌ `GAME_TITLE` → `faker.videoGame().title()`
- ❌ `ESPORTS_TEAM` → `faker.esports().team()`

### Food & Drink (entire category missing)
- ❌ `FOOD_DISH` → `faker.food().dish()`
- ❌ `FOOD_INGREDIENT` → `faker.food().ingredient()`
- ❌ `BEER_NAME` → `faker.beer().name()`
- ❌ `BEER_STYLE` → `faker.beer().style()`
- ❌ `COCKTAIL` → `faker.drink().name()`

### Nature & Science (entire category missing)
- ❌ `ANIMAL` → `faker.animal().name()`
- ❌ `PLANT` → `faker.plant().name()`
- ❌ `ELEMENT` → `faker.chemistr().element()`
- ❌ `PLANET` → `faker.space().planet()`

### Vehicles & Aviation (entire category missing)
- ❌ `CAR_MAKE` → `faker.vehicle().manufacturer()`
- ❌ `CAR_MODEL` → `faker.vehicle().model()`
- ❌ `VIN` → `faker.vehicle().vin()`
- ❌ `LICENSE_PLATE` → `faker.vehicle().licensePlate()`
- ❌ `AIRCRAFT` → `faker.aviation().aircraft()`
- ❌ `AIRPORT` → `faker.aviation().airport()`

### Text & Lorem (entire category missing)
- ❌ `LOREM_WORD` → `faker.lorem().word()`
- ❌ `LOREM_SENTENCE` → `faker.lorem().sentence()`
- ❌ `LOREM_PARAGRAPH` → `faker.lorem().paragraph()`

---

## Summary Statistics

| Category | Implemented | Available | Coverage |
|----------|-------------|-----------|----------|
| **Person & Identity** | 7 | ~12 | 58% |
| **Address** | 7 | ~16 | 44% |
| **Contact** | 2 | ~2 | 100% |
| **Finance & Business** | 5 | ~17 | 29% |
| **Internet** | 5 | ~13 | 38% |
| **Codes & Identifiers** | 2 | ~7 | 29% |
| **Commerce** | 0 | ~5 | 0% |
| **DateTime** | 0 | ~6 | 0% |
| **Medical** | 0 | ~4 | 0% |
| **Education** | 0 | ~3 | 0% |
| **Entertainment** | 0 | ~6 | 0% |
| **Games & Pop Culture** | 0 | ~4 | 0% |
| **Food & Drink** | 0 | ~5 | 0% |
| **Nature & Science** | 0 | ~4 | 0% |
| **Vehicles & Aviation** | 0 | ~5 | 0% |
| **Text & Lorem** | 0 | ~3 | 0% |
| **TOTAL** | **28** | **~110+** | **25%** |

---

## Priority Recommendations

### High Priority (commonly needed in testing)
1. **Commerce**: `PRODUCT_NAME`, `DEPARTMENT`, `MATERIAL`, `COLOR`
2. **Text**: `LOREM_SENTENCE`, `LOREM_PARAGRAPH` (for content fields)
3. **Person Extensions**: `PASSWORD`, `SSN`, `PREFIX`, `SUFFIX`
4. **Finance Extensions**: `BIC`, `CREDIT_CARD_CVV`, `STOCK_MARKET`
5. **Address Extensions**: `LATITUDE`, `LONGITUDE`, `COUNTRY_CODE`

### Medium Priority (industry-specific)
1. **Medical**: Useful for healthcare testing
2. **Education**: Useful for academic/learning systems
3. **Vehicles**: Useful for automotive/logistics testing
4. **DateTime Extensions**: More flexible date generation

### Low Priority (niche use cases)
1. **Games & Pop Culture**: Pokemon, Superhero names
2. **Food & Drink**: Beer, Cocktails
3. **Nature & Science**: Animals, Plants, Elements
4. **Entertainment**: Books, Movies, Music

---

## Implementation Impact

### Effort to Add Support (per category)
- **Easy** (1-2 hours): Commerce, Text, Person Extensions
  - Just add enum values and switch cases
  - Direct Datafaker method calls
  
- **Medium** (2-4 hours): Finance Extensions, Address Extensions, DateTime
  - May need custom logic or formatting
  - Testing locale behavior

- **Low Value** (questionable ROI): Games, Food, Nature
  - Very specific use cases
  - Limited demand

### Breaking Changes: None
All additions would be new enum values. Existing code unaffected.

---

## Recommendation

**Phase 1** (Next sprint): Add high-priority types (~15 new types)
- Commerce (5): product, department, material, color, promotion_code
- Text (3): lorem_word, lorem_sentence, lorem_paragraph
- Person (4): password, ssn, prefix, suffix
- Finance (3): bic, cvv, credit_card_type

**Phase 2** (Future): Add medium-priority categories based on user demand
- Medical (4 types)
- Education (3 types)
- Vehicles (4 types)
- DateTime extensions (6 types)

**Phase 3** (On-demand): Niche categories only if requested
