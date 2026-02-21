# TASK-031: Licensing - Choose and Apply Open Source License

**Status**: 🔄 Partially Complete  
**Priority**: P2 (Medium)  
**Phase**: 8 - Licensing & Open Source  
**Dependencies**: None (can be done anytime)  
**Human Supervision**: **HIGH** (requires legal/business decision)

---

## ✅ Completion Summary (February 21, 2026)

**Completed:**
- ✅ LICENSE file created with Apache License 2.0 full text
- ✅ README updated with license badge and section
- ✅ License choice documented (Apache 2.0)

**Remaining Work:**
- ❌ License headers in source files (.java files)
- ❌ NOTICE file listing third-party dependencies
- ❌ build.gradle.kts metadata (license, organization)
- ❌ Spotless configuration to enforce license headers

**Implementation Path:**
- `LICENSE` (root directory)
- `README.md` (license badge and section)

---

## Objective

Choose an appropriate open-source license for SeedStream and apply it to the repository. This decision affects how others can use, modify, and distribute the software.

---

## Background

**Why This Requires Human Supervision**:
- Legal implications for contributors and users
- Business strategy considerations (permissive vs copyleft)
- Community preference and adoption impact
- Compatibility with project goals and dependencies

**This task CANNOT be automated** - it requires human judgment and decision-making.

---

## License Options Analysis

### Option 1: Apache License 2.0 (Recommended)

**Type**: Permissive with explicit patent grant

**Pros**:
- ✅ Very permissive (commercial use allowed)
- ✅ Explicit patent grant (protects users from patent claims)
- ✅ Widely used in enterprise (Kubernetes, Hadoop, Kafka, Spring)
- ✅ Compatible with most other licenses
- ✅ Allows proprietary derivatives
- ✅ Requires attribution and license notice

**Cons**:
- ❌ More verbose than MIT
- ❌ Requires prominent notice of changes

**Best For**: Enterprise-focused projects, libraries used in commercial products

**Compatible With**: All current dependencies (Datafaker, Jackson, Picocli, etc.)

---

### Option 2: MIT License

**Type**: Highly permissive (minimal restrictions)

**Pros**:
- ✅ Simple and short (easy to understand)
- ✅ Maximum freedom (commercial use, modification, distribution)
- ✅ Minimal requirements (just copyright notice)
- ✅ Very widely adopted
- ✅ Compatible with everything

**Cons**:
- ❌ No explicit patent grant
- ❌ Less protection for contributors

**Best For**: Simple libraries, projects prioritizing ease of adoption

**Compatible With**: All current dependencies

---

### Option 3: GNU GPL v3 (Not Recommended)

**Type**: Copyleft (requires derivative works to be GPL)

**Pros**:
- ✅ Strong copyleft (ensures derivative works stay open source)
- ✅ Explicit patent grant
- ✅ Protects user freedoms

**Cons**:
- ❌ **Incompatible with enterprise adoption** (cannot be used in proprietary software)
- ❌ May conflict with project goal of widespread adoption
- ❌ More complex license terms
- ❌ Reduces commercial use cases

**Best For**: Ideological projects that prioritize software freedom over adoption

**Not Recommended**: Based on project goals (enterprise test data generation)

---

## Decision Matrix

| Criteria | Apache 2.0 | MIT | GPL v3 |
|----------|-----------|-----|--------|
| **Enterprise Adoption** | ✅ Excellent | ✅ Excellent | ❌ Poor |
| **Commercial Use** | ✅ Yes | ✅ Yes | ⚠️ Limited |
| **Patent Protection** | ✅ Explicit | ❌ None | ✅ Explicit |
| **Simplicity** | ⚠️ Moderate | ✅ Excellent | ❌ Complex |
| **Compatibility** | ✅ High | ✅ High | ⚠️ Moderate |
| **Contributor Protection** | ✅ Good | ⚠️ Moderate | ✅ Good |
| **Requires Attribution** | ✅ Yes | ✅ Yes | ✅ Yes |
| **Copyleft** | ❌ No | ❌ No | ✅ Yes |

---

## Recommendation

**Recommended License**: **Apache License 2.0**

**Rationale**:
1. **Enterprise Focus**: SeedStream targets QA engineers, data engineers, DevOps teams in enterprises
2. **Patent Protection**: Explicit patent grant protects users and encourages corporate adoption
3. **Industry Standard**: Most popular Java libraries use Apache 2.0 (Spring, Kafka, Hadoop)
4. **Flexibility**: Allows commercial use and proprietary derivatives (maximizes adoption)
5. **Contributor Safety**: Protects contributors with patent and trademark provisions

**Alternative**: MIT if you prioritize simplicity over patent protection

---

## Implementation Steps

### Step 1: Human Decision Required

**Action**: Project owner (Marco) must decide on license

**Questions to Answer**:
1. Do you want to allow proprietary derivatives? (Yes → Apache/MIT, No → GPL)
2. Do you want explicit patent protection? (Yes → Apache/GPL, No → MIT)
3. Do you want maximum simplicity? (Yes → MIT, No → Apache)
4. Do you want copyleft? (Yes → GPL, No → Apache/MIT)

**Expected Decision**: Apache 2.0 (based on project goals documented in REQUIREMENTS.md)

---

### Step 2: Add LICENSE File

Once decision is made, create LICENSE file:

**File**: `LICENSE` (root of repository)

**For Apache 2.0**:
```text
Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

[Full Apache 2.0 license text from https://www.apache.org/licenses/LICENSE-2.0.txt]
```

**Get full text**: https://www.apache.org/licenses/LICENSE-2.0.txt

---

### Step 3: Add License Headers to Source Files

**For Apache 2.0**, add to ALL Java source files:

**Header Template**:
```java
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
package com.datagenerator.core;

// ... rest of file
```

**Automated Tool**: Use Spotless to apply headers automatically:

**Update root build.gradle.kts**:
```kotlin
subprojects {
    spotless {
        java {
            googleJavaFormat("1.18.1")
            
            // Add license header
            licenseHeaderFile(rootProject.file("config/license-header.txt"))
            
            toggleOffOn()
            target("src/**/*.java")
        }
    }
}
```

**Create config/license-header.txt**:
```text
Copyright 2026 Marco Ferretti

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

**Apply to all files**:
```bash
./gradlew spotlessApply
```

---

### Step 4: Add NOTICE File (Apache 2.0 only)

**File**: `NOTICE` (root of repository)

```text
SeedStream
Copyright 2026 Marco Ferretti

This product includes software developed by:
- The Apache Software Foundation (https://www.apache.org/)
- FasterXML Jackson (https://github.com/FasterXML/jackson)
- Datafaker (https://github.com/datafaker-net/datafaker)
- Picocli (https://github.com/remkop/picocli)
- Project Lombok (https://projectlombok.org/)

For full license information, see the LICENSE file.
```

---

### Step 5: Update README with License Badge

**File**: `README.md`

Add license badge at top:
```markdown
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
```

Or for MIT:
```markdown
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
```

---

### Step 6: Update build.gradle.kts with License Info

**File**: Root `build.gradle.kts`

Add license information (for Maven Central publishing in future):
```kotlin
subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }
    }
    
    plugins.withType<MavenPublishPlugin> {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    
                    pom {
                        name.set("SeedStream")
                        description.set("High-performance test data generator")
                        url.set("https://github.com/mferretti/seedstream")
                        
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        
                        developers {
                            developer {
                                id.set("mferretti")
                                name.set("Marco Ferretti")
                            }
                        }
                        
                        scm {
                            connection.set("scm:git:git://github.com/mferretti/seedstream.git")
                            developerConnection.set("scm:git:ssh://github.com/mferretti/seedstream.git")
                            url.set("https://github.com/mferretti/seedstream")
                        }
                    }
                }
            }
        }
    }
}
```

---

## Acceptance Criteria

- ✅ License choice documented with rationale
- ✅ LICENSE file added to repository root
- ✅ License headers added to all source files
- ✅ NOTICE file added (Apache 2.0 only)
- ✅ README updated with license badge
- ✅ build.gradle.kts includes license metadata
- ✅ All source files have correct copyright year (2026)
- ✅ Spotless configured to enforce license headers

---

## Human Decision Required

**Before proceeding with automation**, answer these questions:

1. **Which license do you choose?** (Apache 2.0 / MIT / Other)
2. **Copyright holder name?** (Default: "Marco Ferretti")
3. **Copyright year?** (Default: 2026)
4. **Do you want Spotless to enforce license headers?** (Recommended: Yes)

---

## Testing Requirements

### Verification Steps

1. **LICENSE file exists**:
   ```bash
   ls -la LICENSE
   # Should exist and contain full license text
   ```

2. **Source files have headers**:
   ```bash
   head -15 core/src/main/java/com/datagenerator/core/SeedResolver.java
   # Should show license header
   ```

3. **Spotless enforces headers**:
   ```bash
   # Remove header from a file manually
   # Run:
   ./gradlew spotlessCheck
   # Should fail
   
   ./gradlew spotlessApply
   # Should re-add header
   ```

4. **GitHub displays license**:
   - Push to GitHub
   - Check repository page shows license badge
   - Check "About" section shows license

---

## Files Created/Modified

### Created:
- `LICENSE` (full license text)
- `NOTICE` (Apache 2.0 only)
- `config/license-header.txt` (for Spotless)

### Modified:
- All `.java` files (license headers added)
- `README.md` (license badge)
- Root `build.gradle.kts` (license metadata, Spotless header enforcement)

---

## Common Issues & Solutions

**Issue**: Spotless fails after adding license headers  
**Solution**: Run `./gradlew spotlessApply` to reformat

**Issue**: Some files missing headers  
**Solution**: Verify Spotless target pattern includes all source files

**Issue**: GitHub doesn't detect license  
**Solution**: Ensure LICENSE file is at repository root (not in subdirectory)

**Issue**: License compatibility with dependencies  
**Solution**: Check dependency licenses before finalizing choice

---

## Dependencies License Check

**Current dependencies** and their licenses:

| Dependency | License | Compatible with Apache 2.0? | Compatible with MIT? |
|------------|---------|------------------------------|----------------------|
| Jackson | Apache 2.0 | ✅ Yes | ✅ Yes |
| Datafaker | Apache 2.0 | ✅ Yes | ✅ Yes |
| Hibernate Validator | Apache 2.0 | ✅ Yes | ✅ Yes |
| Picocli | Apache 2.0 | ✅ Yes | ✅ Yes |
| Lombok | MIT | ✅ Yes | ✅ Yes |
| SLF4J | MIT | ✅ Yes | ✅ Yes |
| Logback | EPL 1.0 / LGPL 2.1 | ✅ Yes | ✅ Yes |
| JUnit 5 | EPL 2.0 | ✅ Yes | ✅ Yes |
| Mockito | MIT | ✅ Yes | ✅ Yes |
| AssertJ | Apache 2.0 | ✅ Yes | ✅ Yes |

**All dependencies are compatible** with both Apache 2.0 and MIT licenses.

---

## Completion Checklist

- [ ] **HUMAN DECISION**: License chosen (Apache 2.0 / MIT / Other)
- [ ] LICENSE file created with full text
- [ ] NOTICE file created (Apache 2.0 only)
- [ ] License header template created (config/license-header.txt)
- [ ] Spotless configured to add license headers
- [ ] License headers added to all source files: `./gradlew spotlessApply`
- [ ] README updated with license badge
- [ ] build.gradle.kts includes license metadata
- [ ] Verification: LICENSE file exists in root
- [ ] Verification: Sample source file has correct header
- [ ] Verification: Spotless enforces headers (test by removing one)
- [ ] Verification: Build succeeds after license headers added
- [ ] GitHub repository shows correct license badge

---

**Estimated Effort**: 2-3 hours (after decision is made)  
**Complexity**: Low (mechanical application of license)  
**Human Decision Required**: HIGH (license choice is strategic decision)
