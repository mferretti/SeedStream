# US-035: Dependency Vulnerability Scanning

**Status**: ⏸️ Not Started  
**Priority**: P1 (High)  
**Phase**: 9 - Security & Compliance  
**Dependencies**: US-001

---

## User Story

As a **security engineer**, I want **automated dependency vulnerability scanning** so that **we detect and address security vulnerabilities in third-party libraries before they reach production**.

---

## Acceptance Criteria

- ✅ OWASP Dependency-Check configured (already in US-001)
- ✅ Build fails on CVSS >= 7.0
- ✅ Suppression file for false positives
- ✅ CI/CD integration for automated scanning
- ✅ Weekly scheduled scans (in addition to PR scans)
- ✅ Vulnerability report generated and archived
- ✅ Process documented for addressing vulnerabilities
- ✅ Dashboard/reporting for vulnerability status

---

## Implementation Notes

### OWASP Dependency-Check
Already configured in root `build.gradle.kts`:
```kotlin
dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "config/dependency-check-suppressions.xml"
}
```

### Run Scans
```bash
# Full scan
./gradlew dependencyCheckAggregate

# Report location
build/reports/dependency-check-report.html
```

### Suppression File
For false positives:
```xml
<suppress>
  <cve>CVE-2023-12345</cve>
  <notes>False positive - does not affect this project because...</notes>
</suppress>
```

### CI/CD Integration
GitHub Actions workflow:
```yaml
- name: Run vulnerability scan
  run: ./gradlew dependencyCheckAggregate

- name: Upload vulnerability report
  if: always()
  uses: actions/upload-artifact@v3
  with:
    name: dependency-check-report
    path: build/reports/dependency-check-report.html
```

### Vulnerability Response Process
1. Scan detects vulnerability
2. Assess severity and impact
3. Check for patches/updates
4. If false positive, add to suppression file with justification
5. If real, update dependency or find alternative
6. Document decision in suppression file or upgrade notes

---

## Testing Requirements

### Verification Tests
- Scan completes successfully
- Suppression file works
- Build fails on high severity CVEs
- Reports generated correctly

### Integration Tests
- CI/CD pipeline runs scans
- Reports uploaded on failure
- Notifications sent (if configured)

---

## Definition of Done

- [ ] OWASP Dependency-Check configured and tested
- [ ] Suppression file created and documented
- [ ] CI/CD integration complete
- [ ] Weekly scheduled scans configured
- [ ] Vulnerability response process documented
- [ ] Team trained on using suppression file
- [ ] Initial scan completed and vulnerabilities addressed
- [ ] Documentation updated with security process
- [ ] PR reviewed and approved
