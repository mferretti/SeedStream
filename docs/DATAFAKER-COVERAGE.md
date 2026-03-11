# Datafaker Coverage Analysis

**Project:** SeedStream
**Datafaker Version:** 2.5.4
**Analysis Date:** March 10, 2026 (updated to reflect TASK-041 completion)

---

## Currently Implemented Types (48 canonical types + 32 aliases)

All types are registered in `DatafakerRegistry` (`core/src/main/java/.../registry/DatafakerRegistry.java`).
Aliases are case-insensitive and normalized at lookup time.

### Person & Identity (11 types)

| Type | Datafaker call | Aliases |
|---|---|---|
| `name` | `faker.name().name()` | — |
| `first_name` | `faker.name().firstName()` | `firstname` |
| `last_name` | `faker.name().lastName()` | `lastname` |
| `full_name` | `faker.name().fullName()` | `fullname` |
| `username` | `faker.credentials().username()` | — |
| `title` | `faker.name().title()` | — |
| `occupation` | `faker.job().title()` | — |
| `prefix` | `faker.name().prefix()` | — |
| `suffix` | `faker.name().suffix()` | — |
| `password` | `faker.credentials().password()` | — |
| `ssn` | `faker.idNumber().valid()` | — |

### Address (11 types)

| Type | Datafaker call | Aliases |
|---|---|---|
| `address` | `faker.address().fullAddress()` | — |
| `street_name` | `faker.address().streetName()` | `streetname` |
| `street_number` | `faker.address().streetAddressNumber()` | `streetnumber` |
| `city` | `faker.address().city()` | — |
| `state` | `faker.address().state()` | — |
| `postal_code` | `faker.address().zipCode()` | `postalcode`, `zipcode`, `zip` |
| `country` | `faker.address().country()` | — |
| `latitude` | `faker.address().latitude()` | `lat` |
| `longitude` | `faker.address().longitude()` | `lon`, `lng`, `long` |
| `country_code` | `faker.address().countryCode()` | `countrycode` |
| `time_zone` | `faker.address().timeZone()` | `timezone` |

### Contact (2 types)

| Type | Datafaker call | Aliases |
|---|---|---|
| `email` | `faker.internet().emailAddress()` | — |
| `phone_number` | `faker.phoneNumber().phoneNumber()` | `phonenumber`, `phone` |

### Finance & Business (9 types)

| Type | Datafaker call | Aliases |
|---|---|---|
| `company` | `faker.company().name()` | — |
| `credit_card` | `faker.finance().creditCard()` | `creditcard` |
| `iban` | `faker.finance().iban()` | — |
| `currency` | `faker.money().currencyCode()` | — |
| `price` | `faker.commerce().price()` | — |
| `bic` | `faker.finance().bic()` | `swift` |
| `cvv` | `faker.number().numberBetween(100, 999)` | `cvc` |
| `credit_card_type` | `faker.finance().creditCard().split(" ")[0]` | `creditcardtype` |
| `stock_market` | `faker.stock().nsdqSymbol()` | `stockmarket`, `stock`, `ticker` |

### Internet (5 types)

| Type | Datafaker call | Aliases |
|---|---|---|
| `domain` | `faker.internet().domainName()` | — |
| `url` | `faker.internet().url()` | — |
| `ipv4` | `faker.internet().ipV4Address()` | — |
| `ipv6` | `faker.internet().ipV6Address()` | — |
| `mac_address` | `faker.internet().macAddress()` | `macaddress` |

### Commerce (5 types)

| Type | Datafaker call | Aliases |
|---|---|---|
| `product_name` | `faker.commerce().productName()` | `productname`, `product` |
| `department` | `faker.commerce().department()` | — |
| `color` | `faker.color().name()` | — |
| `material` | `faker.commerce().material()` | — |
| `promotion_code` | `faker.commerce().promotionCode()` | `promotioncode`, `promo`, `coupon` |

### Text / Lorem (3 types)

| Type | Datafaker call | Aliases |
|---|---|---|
| `lorem_word` | `faker.lorem().word()` | `loremword` |
| `lorem_sentence` | `faker.lorem().sentence()` | `loremsentence` |
| `lorem_paragraph` | `faker.lorem().paragraph()` | `loremparagraph` |

### Codes & Identifiers (2 types)

| Type | Datafaker call | Aliases |
|---|---|---|
| `isbn` | `faker.code().isbn13()` | — |
| `uuid` | `faker.internet().uuid()` | — |

---

## Available But NOT Yet Implemented

These types are available in Datafaker but not pre-registered in `DatafakerRegistry`. They can be added
as custom types at runtime via `DatafakerRegistry.register(name, fn)` — no code changes required.

### Person & Identity Extensions
- ❌ `blood_type` → `faker.medical().bloodType()`
- ❌ `social_security` → `faker.idNumber().ssnValid()` (note: `ssn` IS implemented via `faker.idNumber().valid()`)

### Address Extensions
- ❌ `building_number` → `faker.address().buildingNumber()`
- ❌ `street_address` → `faker.address().streetAddress()`
- ❌ `secondary_address` → `faker.address().secondaryAddress()` (Apt, Suite)
- ❌ `city_prefix` → `faker.address().cityPrefix()`
- ❌ `city_suffix` → `faker.address().citySuffix()`

### Finance & Business Extensions
- ❌ `bitcoin_address` → `faker.crypto().sha256()` (approximation)
- ❌ `company_suffix` → `faker.company().suffix()` (Inc., LLC, Corp.)
- ❌ `industry` → `faker.company().industry()`
- ❌ `profession` → `faker.company().profession()`
- ❌ `buzzword` → `faker.company().buzzword()`
- ❌ `catchphrase` → `faker.company().catchPhrase()`

### Internet Extensions
- ❌ `slug` → `faker.internet().slug()`
- ❌ `user_agent` → `faker.internet().userAgentAny()`
- ❌ `http_method` → `"GET"`, `"POST"`, `"PUT"`, `"DELETE"`, etc.
- ❌ `http_status_code` → 200, 404, 500, etc.
- ❌ `port` → `faker.internet().port()`
- ❌ `public_ipv4` → `faker.internet().publicIpV4Address()`
- ❌ `private_ipv4` → `faker.internet().privateIpV4Address()`

### Code & Identifiers Extensions
- ❌ `ean8` → `faker.code().ean8()`
- ❌ `ean13` → `faker.code().ean13()`
- ❌ `isbn10` → `faker.code().isbn10()`
- ❌ `imei` → `faker.code().imei()`
- ❌ `asin` → `faker.code().asin()`

### DateTime (entire category missing)
- ❌ `day_of_week` → `faker.date().dayOfWeek()`
- ❌ `month_name` → `faker.date().monthName()`
- ❌ `past_date` → `faker.date().past()`
- ❌ `future_date` → `faker.date().future()`
- ❌ `birthday` → `faker.date().birthday()`

### Medical (entire category missing)
- ❌ `medicine_name` → `faker.medical().medicineName()`
- ❌ `disease` → `faker.medical().diseaseName()`
- ❌ `hospital` → `faker.medical().hospitalName()`
- ❌ `symptoms` → `faker.medical().symptoms()`

### Education (entire category missing)
- ❌ `university` → `faker.university().name()`
- ❌ `degree` → `faker.university().degree()`
- ❌ `major` → `faker.university().major()`

### Entertainment (entire category missing)
- ❌ `book_title` → `faker.book().title()`
- ❌ `book_author` → `faker.book().author()`
- ❌ `book_genre` → `faker.book().genre()`
- ❌ `movie_title` → `faker.movie().title()`
- ❌ `music_genre` → `faker.music().genre()`
- ❌ `artist` → `faker.artist().name()`

### Games & Pop Culture (entire category missing)
- ❌ `pokemon` → `faker.pokemon().name()`
- ❌ `superhero` → `faker.superhero().name()`
- ❌ `game_title` → `faker.videoGame().title()`
- ❌ `esports_team` → `faker.esports().team()`

### Food & Drink (entire category missing)
- ❌ `food_dish` → `faker.food().dish()`
- ❌ `food_ingredient` → `faker.food().ingredient()`
- ❌ `beer_name` → `faker.beer().name()`
- ❌ `beer_style` → `faker.beer().style()`
- ❌ `cocktail` → `faker.drink().name()`

### Nature & Science (entire category missing)
- ❌ `animal` → `faker.animal().name()`
- ❌ `plant` → `faker.plant().name()`
- ❌ `element` → `faker.chemistry().element()`
- ❌ `planet` → `faker.space().planet()`

### Vehicles & Aviation (entire category missing)
- ❌ `car_make` → `faker.vehicle().manufacturer()`
- ❌ `car_model` → `faker.vehicle().model()`
- ❌ `vin` → `faker.vehicle().vin()`
- ❌ `license_plate` → `faker.vehicle().licensePlate()`
- ❌ `aircraft` → `faker.aviation().aircraft()`
- ❌ `airport` → `faker.aviation().airport()`

---

## Summary Statistics

| Category | Implemented | Available | Coverage |
|----------|-------------|-----------|----------|
| **Person & Identity** | 11 | ~13 | 85% |
| **Address** | 11 | ~16 | 69% |
| **Contact** | 2 | ~2 | 100% |
| **Finance & Business** | 9 | ~17 | 53% |
| **Internet** | 5 | ~13 | 38% |
| **Codes & Identifiers** | 2 | ~7 | 29% |
| **Commerce** | 5 | ~5 | 100% |
| **Text & Lorem** | 3 | ~3 | 100% |
| **DateTime** | 0 | ~5 | 0% |
| **Medical** | 0 | ~4 | 0% |
| **Education** | 0 | ~3 | 0% |
| **Entertainment** | 0 | ~6 | 0% |
| **Games & Pop Culture** | 0 | ~4 | 0% |
| **Food & Drink** | 0 | ~5 | 0% |
| **Nature & Science** | 0 | ~4 | 0% |
| **Vehicles & Aviation** | 0 | ~6 | 0% |
| **TOTAL** | **48** | **~113+** | **42%** |

---

## Priority Recommendations

### Already Done (Phase 1 — Complete ✅ March 2026)

All high-priority types from the original Phase 1 plan were added in TASK-041:
- ✅ Commerce: `product_name`, `department`, `material`, `color`, `promotion_code`
- ✅ Text: `lorem_word`, `lorem_sentence`, `lorem_paragraph`
- ✅ Person extensions: `password`, `ssn`, `prefix`, `suffix`
- ✅ Finance extensions: `bic`/`swift`, `cvv`/`cvc`, `credit_card_type`, `stock_market`
- ✅ Address extensions: `latitude`, `longitude`, `country_code`, `time_zone`

### High Priority (Phase 2 — commonly needed in testing)
1. **DateTime extensions**: `past_date`, `future_date`, `birthday`, `day_of_week`, `month_name`
2. **Internet extensions**: `user_agent`, `slug`, `http_method`, `http_status_code`
3. **Codes extensions**: `ean13`, `ean8`, `isbn10`, `imei`
4. **Finance extensions**: `company_suffix`, `industry`, `profession`

### Medium Priority (industry-specific)
1. **Medical**: Useful for healthcare testing (`medicine_name`, `disease`, `hospital`)
2. **Education**: Useful for academic/learning systems (`university`, `degree`, `major`)
3. **Vehicles**: Useful for automotive/logistics (`car_make`, `car_model`, `vin`, `license_plate`)

### Low Priority (niche use cases)
1. **Games & Pop Culture**: `pokemon`, `superhero`, `game_title`
2. **Food & Drink**: `food_dish`, `beer_name`, `cocktail`
3. **Nature & Science**: `animal`, `planet`, `element`
4. **Entertainment**: `book_title`, `movie_title`, `artist`

---

## How to Add Types

### Option A: Register at runtime (no code changes)

Users can register custom types via `DatafakerRegistry.register()` before job execution:

```java
DatafakerRegistry.register("pokemon", (faker, random) -> faker.pokemon().name());
DatafakerRegistry.register("hospital", (faker, random) -> faker.medical().hospitalName());
```

### Option B: Add to built-ins (code change — one line)

To permanently add a type to the built-in set, add one line to `registerBuiltIns()` in
`DatafakerRegistry.java`:

```java
register("pokemon", (faker, random) -> faker.pokemon().name());
```

No interface changes, no schema changes, no breaking changes. Existing YAML configs are unaffected.

### Option C: External JAR via `extras/` directory (planned — TASK-044)

Once TASK-044 is implemented, users will be able to drop a JAR containing pre-registered types
into the `extras/` directory. The JAR uses Datafaker's Service Loader mechanism, and `DatafakerRegistry`
picks up any pre-registered custom types on startup.

---

**Last Updated**: March 10, 2026
