# User Story Catalog - Quick Reference

This document provides a quick overview of all user stories for sprint planning and backlog management.

---

## Summary Statistics

- **Total Stories**: 39
- **Completed**: 13 ✅
- **Partially Complete**: 2 🔄
- **In Progress**: 0 🚧
- **Not Started**: 23 ⏸️
- **Blocked**: 1 🔒

---

## By Priority

### P0 (Critical) - 15 stories
Must complete for MVP. These block other work or are essential for core functionality.

### P1 (High) - 13 stories
Important for production readiness but can be deferred if needed.

### P2 (Medium) - 10 stories
Nice to have for v1.0 but not blocking.

---

## By Status

### ✅ Completed (13 stories)

| ID | Title | Phase | Effort | Completed |
|----|-------|-------|--------|--------|
| US-001 | Project Scaffolding | 1 | 4-6h | Jan 2026 |
| US-002 | Data Structure Parser | 1 | 3-4h | Jan 2026 |
| US-003 | Job Definition Parser | 1 | 4-5h | Jan 2026 |
| US-004 | Type System | 1 | 6-8h | Jan 2026 |
| US-005 | Seed Resolution | 1 | 4-5h | Jan 2026 |
| US-006 | Random Provider | 1 | 3-4h | Jan 2026 |
| US-007 | Primitive Generators | 1 | 5-6h | Jan 2026 |
| US-008 | Composite Generators | 1 | 6-8h | Jan 2026 |
| US-009 | Import Refactoring | 1 | 2-3h | Jan 2026 |
| US-013 | JSON Serializer | 3 | 3-4h | Feb 21, 2026 |
| US-014 | CSV Serializer | 3 | 4-5h | Feb 21, 2026 |
| US-016 | File Destination | 4 | 4-5h | Feb 21, 2026 |
| US-019 | CLI Commands | 5 | 4-5h | Feb 21, 2026 |

**Phase 1 Total**: ~40-50h ✅  
**Phase 3 Total**: ~7-9h ✅ (2/3 stories complete)  
**Phase 4 Total**: ~4-5h ✅ (1/3 stories complete)  
**Phase 5 Total**: ~4-5h 🔄 (1/3 stories complete)

---

### ⏸️ Not Started (24 stories)

#### Next Sprint (MVP Priority)
| ID | Title | Priority | Effort | Dependencies |
|----|-------|----------|--------|------------|
| US-010 | Datafaker Integration | P1 | 6-8h | US-007 |
| US-020 | Threading Engine | P0 | 8-10h | US-007, US-008 |
| US-021 | Progress Reporting | P1 | 2-3h | US-019, US-020 |

**Sprint Goal**: Multi-threaded realistic data generation with progress

---

#### Phase 2: Data Generation
| ID | Title | Priority | Effort | Dependencies |
|----|-------|----------|--------|--------------|
| US-010 | Datafaker Integration | P1 | 6-8h | US-007 |
| US-011 | Locale-Specific Data | P1 | 4-5h | US-010 |
| US-012 | Reference Generator | P2 | 8-10h | US-018 (Deferred) |

---

#### Phase 3: Output Formats
| ID | Title | Priority | Effort | Dependencies |
|----|-------|----------|--------|--------------|
| US-015 | Protobuf Serializer | P2 | 6-8h | US-007, US-008 |

**Completed**: US-013 (JSON), US-014 (CSV)

---

#### Phase 4: Destinations
| ID | Title | Priority | Effort | Dependencies |
|----|-------|----------|--------|--------------|
| US-017 | Kafka Destination | P1 | 6-8h | US-013, US-014 |
| US-018 | Database Destination | P1 | 6-8h | US-013, US-014 |

**Completed**: US-016 (File)

---

#### Phase 5: CLI & Execution
| ID | Title | Priority | Effort | Dependencies |
|----|-------|----------|--------|--------------|
| US-020 | Threading Engine | P0 | 8-10h | US-007, US-008 |
| US-021 | Progress Reporting | P1 | 2-3h | US-019, US-020 |

**Completed**: US-019 (CLI Commands)  
**Partially Complete**: US-032 (CLI Logging - --verbose flag)

---

#### Phase 6: Testing & Quality
| ID | Title | Priority | Effort | Dependencies |
|----|-------|----------|--------|--------------|
| US-022 | Integration Test Setup | P1 | 3-4h | US-016 |
| US-023 | Kafka Tests | P1 | 4-5h | US-017, US-022 |
| US-024 | Database Tests | P1 | 4-5h | US-018, US-022 |
| US-025 | File Tests | P1 | 2-3h | US-016, US-022 |
| US-026 | JMH Benchmarks | P2 | 4-6h | US-020 |
| US-027 | Memory Profiling | P2 | 3-4h | US-020 |

---

#### Phase 7: Documentation
| ID | Title | Priority | Effort | Dependencies |
|----|-------|----------|--------|--------------|
| US-028 | README Completion | P1 | 2-3h | None |
| US-029 | Example Configs | P1 | 2-3h | US-016, US-017, US-018 |
| US-030 | JavaDoc | P2 | 4-6h | None |

---

#### Phase 8: Licensing & Security
| ID | Title | Priority | Effort | Dependencies | Notes |
|----|-------|----------|--------|--------------|-------|
| US-033 | Error Handling | P1 | 4-5h | US-019 | |
| US-034 | Secret Management | P1 | 4-5h | US-017, US-018 | **Requires Human Review** |
| US-035 | Dependency Scanning | P2 | 2-3h | None | |
| US-036 | File Permissions | P2 | 2-3h | US-016 | |

**Partially Complete**: US-031 (Licensing - LICENSE file and README), US-032 (CLI Logging - --verbose)

---

#### Phase 9: Future Enhancements
| ID | Title | Priority | Effort | Dependencies | Notes |
|----|-------|----------|--------|--------------|-------|
| US-037 | REST API | P2 | 12-16h | US-019, US-020 | **Post-1.0** |
| US-038 | gRPC API | P2 | 12-16h | US-019, US-020 | **Post-1.0** |
| US-041 | Datafaker Plugin Architecture | P2 | 16-20h | US-010 | **Post-1.0** - Extensible type registry |

---

### 🔒 Blocked (1 story)

| ID | Title | Blocked By | Can Start After |
|----|-------|------------|-----------------|
| US-011 | Locale-Specific Data | US-010 | Datafaker integration complete |

---

## Sprint Planning

### Sprint 1: MVP (17-22h)
**Goal**: Working CLI with realistic data generation

Stories:
1. US-010 (Datafaker) - 6-8h
2. US-013 (JSON) - 3-4h
3. US-016 (File Dest) - 4-5h
4. US-019 (CLI) - 4-5h

**Outcome**: `seedstream execute --job address.yaml` works

---

### Sprint 2: Performance (18-23h)
**Goal**: Fast parallel generation with progress

Stories:
1. US-014 (CSV) - 4-5h
2. US-020 (Threading) - 8-10h
3. US-021 (Progress) - 2-3h
4. US-011 (Locale) - 4-5h

**Outcome**: Generate millions of records per second

---

### Sprint 3: Destinations (16-21h)
**Goal**: Support Kafka and databases

Stories:
1. US-017 (Kafka) - 6-8h
2. US-018 (Database) - 6-8h
3. US-032 (Logging) - 2-3h
4. US-033 (Errors) - 2-3h

**Outcome**: Stream to Kafka or insert into DB

---

### Sprint 4: Testing (13-18h)
**Goal**: Integration tests and benchmarks

Stories:
1. US-022 (Test Setup) - 3-4h
2. US-023 (Kafka Tests) - 4-5h
3. US-024 (DB Tests) - 4-5h
4. US-025 (File Tests) - 2-3h

**Outcome**: Full integration test coverage

---

### Sprint 5: Quality (16-23h)
**Goal**: Production-ready with docs

Stories:
1. US-026 (Benchmarks) - 4-6h
2. US-027 (Memory) - 3-4h
3. US-028 (README) - 2-3h
4. US-029 (Examples) - 2-3h
5. US-030 (JavaDoc) - 4-6h
6. US-031 (License) - 2-3h

**Outcome**: v1.0 release candidate

---

### Sprint 6: Security (10-13h)
**Goal**: Security hardening

Stories:
1. US-034 (Secrets) - 4-5h
2. US-035 (Dep Scan) - 2-3h
3. US-036 (Permissions) - 2-3h
4. US-015 (Protobuf) - 6-8h

**Outcome**: Security audit complete

---

## Effort Summary

### Total Remaining Effort
- **P0 Stories**: ~45-55h (critical path)
- **P1 Stories**: ~55-70h (high priority)
- **P2 Stories**: ~40-55h (nice to have)

**Total Project**: ~140-180h remaining (3.5-4.5 weeks at 40h/week)

### By Phase
- Phase 2 (Data): 18-23h
- Phase 3 (Formats): 13-17h
- Phase 4 (Destinations): 16-21h
- Phase 5 (CLI): 14-18h
- Phase 6 (Testing): 18-23h
- Phase 7 (Docs): 8-12h
- Phase 8 (Security): 16-21h
- Phase 9 (Future): 24-32h

---

## Dependencies Graph

```
US-001 (Scaffolding) ✅
    ↓
US-002, US-003, US-004, US-005, US-006 ✅
    ↓
US-007 (Primitives) ✅
    ↓
US-008 (Composites) ✅
    ↓
    ├─→ US-010 (Datafaker) → US-011 (Locale)
    ├─→ US-013 (JSON) ────┐
    └─→ US-014 (CSV) ─────┤
                          ├─→ US-016 (File) ──┐
                          ├─→ US-017 (Kafka) ─┤
                          └─→ US-018 (DB) ────┤
                                              ↓
                                         US-019 (CLI)
                                              ↓
                                         US-020 (Threading)
                                              ↓
                                         US-021 (Progress)
```

---

## Risk Assessment

### High Risk (needs careful review)
- **US-020** (Threading): Complex concurrency, must maintain determinism
- **US-017** (Kafka): External dependency, auth complexity
- **US-018** (Database): Connection pooling, transaction handling
- **US-034** (Secrets): Security implications

### Medium Risk
- **US-010** (Datafaker): Large dependency, locale edge cases
- **US-015** (Protobuf): Complex schema handling
- **US-026** (Benchmarks): JMH setup complexity

### Low Risk
- Most Phase 7 (Docs) stories
- US-013, US-014 (Serializers)
- US-016 (File destination)

---

## Human Decisions Required

These stories cannot be completed without human judgment:

1. **US-031** (Licensing)
   - Decision: Apache 2.0, MIT, or GPL?
   - Impact: Legal, adoption, compatibility
   - Recommendation: Apache 2.0 (enterprise-friendly)

2. **US-034** (Secret Management)
   - Decision: Environment vars, key vault, config files?
   - Impact: Security posture, deployment complexity
   - Recommendation: Environment vars + external vault support

---

## Version Roadmap

### v0.1.0 (MVP) - Sprints 1-2
- Basic CLI
- Realistic data generation
- JSON/CSV formats
- File destination
- Single-threaded

### v0.2.0 (Performance) - Sprint 3
- Multi-threading
- Progress reporting
- Locale support
- Kafka destination

### v0.3.0 (Enterprise) - Sprint 4
- Database destination
- Integration tests
- Error handling
- Secret management

### v1.0.0 (Production) - Sprints 5-6
- Benchmarks
- Complete documentation
- Security hardening
- License applied

### v2.0.0 (Future)
- REST API
- gRPC API
- Reference generator
- Protobuf support

---

**Last Updated**: February 21, 2026  
**For Detailed Stories**: See individual US-*.md files  
**For AI Tasks**: See tasks/ directory

---

## Recent Completions (Feb 21, 2026)

**Sprint Summary**: Completed formats, file destination, and CLI interface

- ✅ **US-013**: JSON Serializer - NDJSON format with field aliases (16 tests)
- ✅ **US-014**: CSV Serializer - RFC 4180 compliant with always-quoted fields (17 tests)
- ✅ **US-016**: File Destination - NIO-based with gzip compression and append mode (16 tests)
- ✅ **US-019**: CLI Commands - Picocli-based interface with all options

**Impact**: End-to-end pipeline now functional (parse → generate → serialize → write)
**Total Tests**: 165 passing across 6 modules
**Verified**: Deterministic output (SHA-256 matching across runs)
