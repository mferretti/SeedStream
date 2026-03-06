# Internal Documentation

This directory contains internal planning and development documentation that is **not intended for public consumption**. These documents are kept in the repository for development team reference and project tracking purposes.

## Documents in This Directory

### REQUIREMENTS.md
**Purpose**: Detailed functional and non-functional requirements specification with internal tracking metrics.

**Contains**:
- Functional requirements (FR-1 through FR-11+) with acceptance criteria
- Non-functional requirements (NFR-1 through NFR-5) with target metrics
- Implementation status tracking (checkboxes, test counts)
- Business objectives (BO-1, BO-2, BO-3) and technical objectives (TO-1 through TO-5)
- Design decision rationale

**Audience**: Development team, project planners

**Why Internal**: Contains project planning metrics, implementation tracking, and work-in-progress specifications that are not relevant to end users or external contributors.

---

### BACKLOG.md
**Purpose**: Project task tracking and implementation progress.

**Contains**:
- Task completion checkboxes organized by phase
- Test count tracking
- Implementation notes and technical decisions
- "Before going public" checklists

**Audience**: Development team, project managers

**Why Internal**: Active project management and task tracking. External contributors should use GitHub Issues and the public ROADMAP.md instead.

---

### MEMORY-PROFILING.md
**Purpose**: Detailed memory profiling results and optimization analysis.

**Contains**:
- JFR profiling methodology
- Heap usage analysis with specific byte counts
- Garbage collection pressure measurements
- JVM tuning recommendations

**Audience**: Performance engineers, core maintainers

**Why Internal**: Highly technical QA validation results. General users only need the summary performance data in docs/PERFORMANCE.md. This level of detail is for maintainers validating changes.

---

### DATAFAKER-TEST-ENHANCEMENTS.md
**Purpose**: Internal notes on Datafaker testing improvements.

**Contains**:
- Test improvement proposals
- Integration test ideas
- Technical implementation notes

**Audience**: Test engineers, core maintainers

**Why Internal**: Work-in-progress notes and test planning. Not polished enough for public documentation.

---

### LICENSE-DISCUSSION.md
**Purpose**: License selection deliberation and rationale.

**Contains**:
- Comparison of MIT vs Apache 2.0 licenses
- Enterprise patent grant considerations
- Decision rationale for choosing Apache 2.0
- Trade-offs analysis

**Audience**: Core maintainers, project founders

**Why Internal**: Planning artifact documenting the decision-making process. The LICENSE file itself is authoritative for users. This document is historical context for the team, not relevant to external contributors.

---

## For Public Documentation

External users and contributors should refer to the **public documentation**:

- **[README.md](../../README.md)** - Quick start, features, installation
- **[CONTRIBUTING.md](../CONTRIBUTING.md)** - How to contribute to the project
- **[DESIGN.md](../DESIGN.md)** - Architecture and design decisions
- **[PERFORMANCE.md](../PERFORMANCE.md)** - Performance benchmarks and tuning
- **[CHANGELOG.md](../../CHANGELOG.md)** - Version history and changes

For feature requests or bugs, please use [GitHub Issues](https://github.com/mferretti/SeedStream/issues).

---

## Maintenance Notes

**When to update these docs:**
- Requirements change or new requirements are added → Update REQUIREMENTS.md
- Task completed or status changes → Update BACKLOG.md
- Performance optimization work → Update MEMORY-PROFILING.md

**When to retire these docs:**
- If requirements tracking moves to another system (e.g., Jira, Linear)
- If task tracking moves entirely to GitHub Projects
- These can be considered "living documents" that evolve with the project

**Git tracking:**
- These files ARE tracked in git (not in .gitignore)
- This allows team members to sync on requirements and progress
- However, they are in `docs/internal/` to signal they're not user-facing documentation
