# Datafaker Coverage Analysis

**Project:** SeedStream
**Datafaker Version:** 2.5.4
**Analysis Date:** March 10, 2026 (updated to reflect TASK-041 completion)

---

## Currently Implemented Types (49 canonical types + 34 aliases)

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

### Finance & Business (10 types)

| Type | Datafaker call | Aliases |
|---|---|---|
| `company` | `faker.company().name()` | — |
| `credit_card` | `faker.finance().creditCard()` | `creditcard` |
| `iban` | `faker.finance().iban(country)` — locale-aware: uses the locale's country when Datafaker supports it, else random-country | — |
| `random_iban` | `faker.finance().iban()` — explicit random-country IBAN (e.g. cross-border destination accounts) | `random_locale_iban` |
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

## Available via `--faker-types` config (no pre-registration needed)

These types are not pre-registered in `DatafakerRegistry`, but **none of them require code**. Each one
is a no-arg Datafaker method chain, so you map it in a `--faker-types` YAML file and reference it like any
built-in:

```yaml
# config/datafaker-types.example.yaml
types:
  blood_type:   medical.bloodType        # faker.medical().bloodType()
  car_model:    vehicle.model            # faker.vehicle().model()
  pokemon:      pokemon.name             # faker.pokemon().name()
```
```bash
datagenerator execute --job ... --faker-types my-types.yaml
```

The chain is resolved + validated at load (`DatafakerRegistry.registerExpression`), so a typo fails fast.
The list below is therefore a **convenience backlog** (types worth promoting to built-ins for name-hint
auto-targeting in `inspect`), not a set of features that are unavailable today.

> **The real code-required gap** is *not* in this list. A `--faker-types` chain can only call **no-arg**
> methods, and the result is coerced to `String` via `String.valueOf`. Providers that need **arguments**
> — `number().numberBetween(min,max)`, `options().option(a,b,c)`, `regexify("[A-Z]{3}")`, bounded
> `date().past(n, UNIT)` / `date().future(...)` — or that need **non-String formatting** (e.g. a date
> rendered to a pattern instead of `Timestamp.toString()`) cannot be expressed in config. Those still
> require a Java lambda via `DatafakerRegistry.register(name, fn)`. See
> [Genuinely code-required generators](#genuinely-code-required-generators) below.

### Person & Identity Extensions
- ⚙️ `blood_type` → `faker.medical().bloodType()`
- ⚙️ `social_security` → `faker.idNumber().ssnValid()` (note: `ssn` IS implemented via `faker.idNumber().valid()`)

### Address Extensions
- ⚙️ `building_number` → `faker.address().buildingNumber()`
- ⚙️ `street_address` → `faker.address().streetAddress()`
- ⚙️ `secondary_address` → `faker.address().secondaryAddress()` (Apt, Suite)
- ⚙️ `city_prefix` → `faker.address().cityPrefix()`
- ⚙️ `city_suffix` → `faker.address().citySuffix()`

### Finance & Business Extensions
- ⚙️ `bitcoin_address` → `faker.crypto().sha256()` (approximation)
- ⚙️ `company_suffix` → `faker.company().suffix()` (Inc., LLC, Corp.)
- ⚙️ `industry` → `faker.company().industry()`
- ⚙️ `profession` → `faker.company().profession()`
- ⚙️ `buzzword` → `faker.company().buzzword()`
- ⚙️ `catchphrase` → `faker.company().catchPhrase()`

### Internet Extensions
- ⚙️ `slug` → `faker.internet().slug()`
- ⚙️ `user_agent` → `faker.internet().userAgentAny()`
- ⚙️ `http_method` → `"GET"`, `"POST"`, `"PUT"`, `"DELETE"`, etc.
- ⚙️ `http_status_code` → 200, 404, 500, etc.
- ⚙️ `port` → `faker.internet().port()`
- ⚙️ `public_ipv4` → `faker.internet().publicIpV4Address()`
- ⚙️ `private_ipv4` → `faker.internet().privateIpV4Address()`

### Code & Identifiers Extensions
- ⚙️ `ean8` → `faker.code().ean8()`
- ⚙️ `ean13` → `faker.code().ean13()`
- ⚙️ `isbn10` → `faker.code().isbn10()`
- ⚙️ `imei` → `faker.code().imei()`
- ⚙️ `asin` → `faker.code().asin()`

### DateTime (no built-ins yet — all config-resolvable via `--faker-types`)
- ⚙️ `day_of_week` → `faker.date().dayOfWeek()`
- ⚙️ `month_name` → `faker.date().monthName()`
- 🔧 `past_date` → `faker.date().past(n, UNIT)` — **code-required** (takes arguments; see below)
- 🔧 `future_date` → `faker.date().future(n, UNIT)` — **code-required** (takes arguments; see below)
- 🔧 `birthday` → `faker.date().birthday()` — no-arg, but returns a `Timestamp`; config yields raw `toString()`. Use a lambda for a formatted/bounded value.

### Medical (no built-ins yet — all config-resolvable via `--faker-types`)
- ⚙️ `medicine_name` → `faker.medical().medicineName()`
- ⚙️ `disease` → `faker.medical().diseaseName()`
- ⚙️ `hospital` → `faker.medical().hospitalName()`
- ⚙️ `symptoms` → `faker.medical().symptoms()`

### Education (no built-ins yet — all config-resolvable via `--faker-types`)
- ⚙️ `university` → `faker.university().name()`
- ⚙️ `degree` → `faker.university().degree()`
- ⚙️ `major` → `faker.university().major()`

### Entertainment (no built-ins yet — all config-resolvable via `--faker-types`)
- ⚙️ `book_title` → `faker.book().title()`
- ⚙️ `book_author` → `faker.book().author()`
- ⚙️ `book_genre` → `faker.book().genre()`
- ⚙️ `movie_title` → `faker.movie().title()`
- ⚙️ `music_genre` → `faker.music().genre()`
- ⚙️ `artist` → `faker.artist().name()`

### Games & Pop Culture (no built-ins yet — all config-resolvable via `--faker-types`)
- ⚙️ `pokemon` → `faker.pokemon().name()`
- ⚙️ `superhero` → `faker.superhero().name()`
- ⚙️ `game_title` → `faker.videoGame().title()`
- ⚙️ `esports_team` → `faker.esports().team()`

### Food & Drink (no built-ins yet — all config-resolvable via `--faker-types`)
- ⚙️ `food_dish` → `faker.food().dish()`
- ⚙️ `food_ingredient` → `faker.food().ingredient()`
- ⚙️ `beer_name` → `faker.beer().name()`
- ⚙️ `beer_style` → `faker.beer().style()`
- ⚙️ `cocktail` → `faker.drink().name()`

### Nature & Science (no built-ins yet — all config-resolvable via `--faker-types`)
- ⚙️ `animal` → `faker.animal().name()`
- ⚙️ `plant` → `faker.plant().name()`
- ⚙️ `element` → `faker.chemistry().element()`
- ⚙️ `planet` → `faker.space().planet()`

### Vehicles & Aviation (no built-ins yet — all config-resolvable via `--faker-types`)
- ⚙️ `car_make` → `faker.vehicle().manufacturer()`
- ⚙️ `car_model` → `faker.vehicle().model()`
- ⚙️ `vin` → `faker.vehicle().vin()`
- ⚙️ `license_plate` → `faker.vehicle().licensePlate()`
- ⚙️ `aircraft` → `faker.aviation().aircraft()`
- ⚙️ `airport` → `faker.aviation().airport()`

**Legend:** ⚙️ = config-resolvable today via `--faker-types` (no code). 🔧 = code-required (see next section).

---

## Genuinely code-required generators

A `--faker-types` chain calls **no-arg** Datafaker methods only and stringifies the result with
`String.valueOf` (`DatafakerRegistry.resolveChain` / `invokeChain`). Everything Datafaker exposes as a
plain no-arg provider is therefore reachable from config. Only two classes of generator still need a
Java lambda via `DatafakerRegistry.register(name, fn)`:

1. **Parameterized providers** — the method takes arguments, so it can't be named as a bare chain:
   - `faker.number().numberBetween(min, max)`, `faker.number().randomDouble(dec, min, max)`
   - `faker.options().option("A", "B", "C")`
   - `faker.regexify("[A-Z]{3}-\\d{4}")`
   - bounded dates: `faker.date().past(n, UNIT)`, `faker.date().future(n, UNIT)`, `faker.date().between(a, b)`
2. **Non-String / formatted returns** — the provider returns a non-String (e.g. `Timestamp`, `BigDecimal`)
   and you want a specific format or range rather than its raw `toString()`. `birthday()` is the common
   case: no-arg, but config emits `Timestamp.toString()` with no control over pattern or age bounds.

Everything else in the list above is a one-line YAML entry, not a code change.

---

## Summary Statistics

Coverage here means **pre-registered built-ins**, not availability — every category below is fully
reachable today via `--faker-types`, except the parameterized generators noted above. The percentages
track how many types get a built-in name-hint (used by `inspect` auto-targeting), not what the engine
can produce.

| Category | Built-in | Reachable via Datafaker | Built-in coverage |
|----------|----------|-------------------------|-------------------|
| **Person & Identity** | 11 | ~13 | 85% |
| **Address** | 11 | ~16 | 69% |
| **Contact** | 2 | ~2 | 100% |
| **Finance & Business** | 10 | ~17 | 59% |
| **Internet** | 5 | ~13 | 38% |
| **Codes & Identifiers** | 2 | ~7 | 29% |
| **Commerce** | 5 | ~5 | 100% |
| **Text & Lorem** | 3 | ~3 | 100% |
| **DateTime** | 0 | ~5 | 0% (config; `past_date`/`future_date` need code) |
| **Medical** | 0 | ~4 | 0% (config) |
| **Education** | 0 | ~3 | 0% (config) |
| **Entertainment** | 0 | ~6 | 0% (config) |
| **Games & Pop Culture** | 0 | ~4 | 0% (config) |
| **Food & Drink** | 0 | ~5 | 0% (config) |
| **Nature & Science** | 0 | ~4 | 0% (config) |
| **Vehicles & Aviation** | 0 | ~6 | 0% (config) |
| **TOTAL** | **48** | **~113+** | **42% built-in** |

> "42%" is the built-in/name-hint share. Effective availability without code is far higher — only the
> handful of parameterized generators above are genuinely out of reach until someone writes a lambda.

---

## Priority Recommendations

These are **promotion** priorities — which config-resolvable types are worth pre-registering as built-ins
so `inspect` can auto-target them by column name. They are *not* "missing features": all are usable today
via `--faker-types` (except the 🔧 parameterized ones, which need a lambda regardless).

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

### Option A: `--faker-types` YAML (no code) — preferred

Map a key to a no-arg Datafaker method chain; pass the file to `inspect` and `execute`:

```yaml
types:
  pokemon:  pokemon.name              # faker.pokemon().name()
  hospital: medical.hospitalName      # faker.medical().hospitalName()
```
```bash
datagenerator execute --job ... --faker-types my-types.yaml
```

The chain is validated at load (`DatafakerRegistry.registerExpression`). This covers every no-arg
provider — no Java required.

### Option B: Register a lambda at runtime (for parameterized / formatted values)

Needed only for the 🔧 cases above (arguments or non-String formatting):

```java
DatafakerRegistry.register("order_qty",  (faker, random) -> String.valueOf(faker.number().numberBetween(1, 100)));
DatafakerRegistry.register("past_date",  (faker, random) -> faker.date().past(30, TimeUnit.DAYS).toString());
```

### Option C: Add to built-ins (code change — one line)

To permanently add a type to the built-in set, add one line to `registerBuiltIns()` in
`DatafakerRegistry.java`:

```java
register("pokemon", (faker, random) -> faker.pokemon().name());
```

No interface changes, no schema changes, no breaking changes. Existing YAML configs are unaffected.

### Option D: External JAR via `extras/` directory

Drop a JAR containing pre-registered types into the `extras/` directory — the launch scripts prepend
`extras/*` to the classpath at startup. The JAR uses Datafaker's Service Loader mechanism, and
`DatafakerRegistry` picks up any pre-registered custom types on startup.

---

**Last Updated**: March 10, 2026
