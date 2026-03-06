# License Discussion for SeedStream

## Executive Summary

This document analyzes different open-source license options for SeedStream, considering the project's goals, use cases, and dependency compatibility.

**Current Status**: Evaluating license choice  
**Recommendation**: See analysis below based on your intended use case

---

## Project Scope

SeedStream is a **high-performance test data generator** designed for:
- Generating millions of primitive records per second (in-memory), or thousands of realistic records per second
- Supporting multiple destinations (Kafka, databases, files)
- Reproducible data generation using seed-based algorithms
- Locale-aware realistic data (names, addresses, financial data)
- Enterprise-scale testing and development environments

**Target Users**: Developers, QA engineers, DevOps teams, data engineers

---

## License Options Analysis

### 1. Apache License 2.0

**Key Points:**
- Very permissive open-source license
- Allows commercial use without restrictions
- Requires preservation of copyright and license notices
- Provides explicit patent grant
- Compatible with GPL v3 (one-way: Apache → GPL)
- Most popular license for enterprise software

**Pros:**
- ✅ Maximum adoption - companies prefer permissive licenses
- ✅ Simple compliance - just keep copyright notices
- ✅ No copyleft obligations
- ✅ Patent protection for contributors and users
- ✅ Corporate-friendly - used by Apache Foundation, Google, etc.

**Cons:**
- ❌ Companies can build proprietary products without giving back
- ❌ No protection against commercial exploitation
- ❌ Derivatives can be closed-source
- ❌ No mechanism to ensure improvements are shared

**Compatibility with SeedStream Dependencies:**
- ✅ Datafaker (Apache 2.0) - Perfect match
- ✅ Jackson (Apache 2.0) - Perfect match
- ✅ Logback (EPL 1.0/LGPL 2.1) - Compatible
- ✅ Lombok (MIT) - Compatible
- ✅ JUnit 5 (EPL 2.0) - Compatible (test only)
- ✅ Mockito (MIT) - Compatible (test only)
- ✅ AssertJ (Apache 2.0) - Perfect match

---

### 2. GNU General Public License v3.0 (GPL v3)

**Key Points:**
- Strong copyleft license
- Requires derivative works to be open source under GPL v3
- Distribution of modified binaries requires source code release
- Provides patent protection
- **SaaS loophole**: Running modified GPL software as a service does NOT require source release

**Pros:**
- ✅ Ensures modifications remain open source
- ✅ Prevents proprietary forks
- ✅ Strong community contribution incentive
- ✅ Patent protection included
- ✅ Well-established and legally tested

**Cons:**
- ❌ **SaaS loophole**: Companies can run it as a service without releasing code
- ❌ Corporate adoption barrier - many companies avoid GPL
- ❌ Incompatible with Apache 2.0 dependencies (GPL is one-way compatible)
- ❌ Cannot link with proprietary libraries
- ❌ Licensing complexity for users

**Compatibility with SeedStream Dependencies:**
- ⚠️ Datafaker (Apache 2.0) - Can incorporate Apache code into GPL project
- ⚠️ Jackson (Apache 2.0) - Can incorporate Apache code into GPL project
- ⚠️ Others (MIT, EPL) - Generally compatible but requires careful analysis
- ⚠️ **One-way street**: Can use Apache/MIT libraries, but others cannot use your GPL code in Apache projects

---

### 3. GNU Affero General Public License v3.0 (AGPL v3)

**Key Points:**
- Strongest copyleft license
- Like GPL v3, but **closes the SaaS loophole**
- Network use (e.g., running as a web service) triggers copyleft
- Requires source code release even for SaaS/cloud deployments
- Used by: MongoDB (historically), Grafana, GitLab (some components)

**Pros:**
- ✅ **Closes SaaS loophole** - network use = distribution
- ✅ Prevents commercial exploitation without giving back
- ✅ Ensures all modifications (even SaaS) remain open source
- ✅ Strong community protection
- ✅ Patent protection included
- ✅ Perfect for anti-commercial-exploitation goals

**Cons:**
- ❌ **Most restrictive** - lowest corporate adoption
- ❌ Many companies ban AGPL software entirely
- ❌ Complexity for users (must understand network use obligations)
- ❌ May limit ecosystem growth
- ❌ Harder to build integrations (companies avoid AGPL dependencies)

**Compatibility with SeedStream Dependencies:**
- ⚠️ Datafaker (Apache 2.0) - Can incorporate, but result is AGPL
- ⚠️ Jackson (Apache 2.0) - Can incorporate, but result is AGPL
- ⚠️ All dependencies - Technically compatible, but "infects" the combined work
- ⚠️ **Isolation effect**: Other Apache 2.0 projects cannot use AGPL code

---

## Use Case Analysis

### Personal Projects

**Apache 2.0:**
- ✅ Simple to use and share
- ✅ Can be integrated into personal portfolio
- ✅ No compliance burden

**GPL v3:**
- ✅ Ensures your work stays open
- ⚠️ Limits reuse if you switch to commercial projects

**AGPL v3:**
- ✅ Maximum open-source protection
- ⚠️ Limits reuse if you switch to commercial projects
- ⚠️ Network use obligations may surprise users

**Verdict**: Apache 2.0 or GPL v3 are best for personal flexibility

---

### Open Source Projects

**Apache 2.0:**
- ✅ Maximum adoption and contributions
- ✅ Easy integration into other projects
- ✅ Encourages ecosystem growth
- ❌ Proprietary forks possible

**GPL v3:**
- ✅ Ensures contributions stay open
- ✅ Builds strong community
- ⚠️ Smaller potential contributor base
- ⚠️ SaaS companies can use without contributing

**AGPL v3:**
- ✅ Strongest community protection
- ✅ Forces SaaS companies to contribute
- ❌ Lowest adoption rate
- ❌ May limit ecosystem growth

**Verdict**: Apache 2.0 for growth, AGPL v3 for protection

---

### Small Companies (1-50 employees)

**Apache 2.0:**
- ✅ Can use freely for internal tools
- ✅ Can modify and keep private
- ✅ Can build products on top
- ✅ No legal complexity

**GPL v3:**
- ✅ Can use internally without releasing source
- ⚠️ Must open-source if distributing modified binaries
- ⚠️ Can run as SaaS without open-sourcing

**AGPL v3:**
- ✅ Can use internally on-premise
- ❌ **Must open-source if offering as SaaS**
- ❌ Legal/compliance burden
- ❌ May require legal review

**Verdict**: Apache 2.0 preferred, GPL v3 acceptable, AGPL v3 avoided

---

### Medium Companies (50-500 employees)

**Apache 2.0:**
- ✅ No compliance concerns
- ✅ Can integrate into products
- ✅ Can build commercial services
- ✅ Procurement approved

**GPL v3:**
- ⚠️ Procurement review required
- ⚠️ Cannot distribute modified binaries as proprietary
- ✅ Can run as SaaS without disclosure

**AGPL v3:**
- ❌ **Often banned by policy**
- ❌ Cannot offer as SaaS without open-sourcing
- ❌ Risk of "viral" contamination
- ❌ Expensive legal review

**Verdict**: Apache 2.0 strongly preferred, GPL v3 risky, AGPL v3 blocked

---

### Enterprises (500+ employees)

**Apache 2.0:**
- ✅ Pre-approved by legal
- ✅ No restrictions on use
- ✅ Can build products and services
- ✅ Patent protection

**GPL v3:**
- ⚠️ Requires case-by-case legal approval
- ⚠️ Cannot redistribute modified code as proprietary
- ⚠️ Seen as risky by legal teams
- ✅ Internal use is fine

**AGPL v3:**
- ❌ **Almost always banned**
- ❌ "Viral" nature is unacceptable risk
- ❌ SaaS obligations too complex
- ❌ Cannot use in cloud services
- ❌ Legal costs too high

**Verdict**: Only Apache 2.0 is viable for enterprise adoption

---

## SeedStream Use Case Implications

### For SeedStream Specifically

**Typical Use Cases:**
1. **Test Data Generation for CI/CD** (internal use)
2. **Seeding Development Databases** (internal use)
3. **Load Testing Kafka/Databases** (internal use)
4. **Demo/POC Data Creation** (internal use)
5. **Test-Data-as-a-Service Offering** (network/SaaS use)

**License Impact:**

| Use Case | Apache 2.0 | GPL v3 | AGPL v3 |
|----------|-----------|--------|---------|
| Internal testing | ✅ No obligations | ✅ No obligations | ✅ No obligations |
| Modified version internally | ✅ Keep private | ✅ Keep private | ✅ Keep private |
| Distribute modified binary | ✅ Keep private | ❌ Must open-source | ❌ Must open-source |
| Offer as SaaS product | ✅ Keep private | ✅ Keep private | ❌ **Must open-source** |
| Build proprietary product | ✅ Allowed | ❌ Not allowed | ❌ Not allowed |

---

## Recommendation Matrix

### Choose Apache 2.0 If:
- ✅ You want maximum adoption
- ✅ You're okay with commercial use
- ✅ You want ecosystem growth
- ✅ You want enterprise users
- ✅ You prioritize simplicity

### Choose GPL v3 If:
- ✅ You want modifications to stay open
- ✅ You're okay with SaaS use without contribution
- ⚠️ You accept lower adoption rate
- ⚠️ You don't need enterprise users

### Choose AGPL v3 If:
- ✅ **You want to prevent commercial SaaS without giving back**
- ✅ You prioritize ideology over adoption
- ✅ You're okay with very limited corporate use
- ✅ You want strongest open-source protection
- ❌ You accept it will limit growth significantly

---

## Author's Stated Requirements

Based on your requirements:
> "I don't care to make money but I don't want companies to use it for making money. Also consider the SaaS loophole."

**Analysis:**
- You explicitly want to prevent commercial exploitation ✅
- You want to close the SaaS loophole ✅
- You don't need wide corporate adoption ✅

**Conclusion**: **AGPL v3 is the best fit for your goals**

However, be aware of the trade-offs:
- ⚠️ Will limit adoption significantly
- ⚠️ May prevent integration into larger ecosystems
- ⚠️ Companies may build competing Apache 2.0 alternatives instead
- ✅ But it's the ONLY license that truly prevents commercial SaaS exploitation

---

## Final Recommendation

### For Your Goals: **AGPL v3**

**Why:**
1. Closes SaaS loophole completely
2. Prevents commercial exploitation
3. Forces any network-based use to be open source
4. Aligns with your anti-commercial-exploitation stance
5. Compatible with all your dependencies (one-way)

**Alternative Path:**
If you later decide you want wider adoption, you can:
1. **Dual license**: AGPL v3 + commercial license (sell exceptions)
2. **Relicense to Apache 2.0** (requires all contributors to agree)
3. **MIT/Apache for libraries, AGPL for CLI** (component-based licensing)

---

## Next Steps

1. **Choose License**: AGPL v3 (recommended) or reconsider based on adoption goals
2. **Add LICENSE File**: Full license text
3. **Update README Badge**: Change from Apache 2.0 to AGPL v3
4. **Add Headers** (optional but recommended): Copyright + AGPL notice in each file
5. **Document in README**: Explain license choice and implications
6. **Add CONTRIBUTING.md**: Explain contributors grant AGPL rights

---

## Additional Resources

- [AGPL v3 Full Text](https://www.gnu.org/licenses/agpl-3.0.en.html)
- [GPL v3 Full Text](https://www.gnu.org/licenses/gpl-3.0.en.html)
- [Apache 2.0 Full Text](https://www.apache.org/licenses/LICENSE-2.0)
- [Choose A License](https://choosealicense.com/)
- [TLDRLegal - License Summaries](https://tldrlegal.com/)
- [GNU License Compatibility Matrix](https://www.gnu.org/licenses/license-list.html)

---

**Document Version**: 1.0  
**Last Updated**: January 19, 2026  
**Author**: Marco Ferretti
