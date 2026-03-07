# US-021: Real-Time Progress Reporting

**Status**: ✅ Complete  
**Priority**: P1 (High)  
**Phase**: 5 - CLI & Execution  
**Dependencies**: US-019, US-020  
**Completion Date**: March 6, 2026

---

## User Story

As a **user**, I want **real-time progress updates during generation** so that **I can monitor progress, estimate completion time, and track throughput performance**.

---

## Acceptance Criteria

- ✅ Progress bar showing percentage complete
- ✅ Records generated counter (current/total)
- ✅ Throughput display (records/second)
- ✅ Estimated time to completion (ETA)
- ✅ Update frequency: every 100ms or 10,000 records
- ✅ Auto-disable progress bar in non-TTY environments (CI/CD)
- ✅ Final summary with total time and average throughput
- ✅ Optional `--no-progress` flag to disable
- ✅ Memory usage stats (optional)

---

## Implementation Notes

### Progress Bar Format
```
Generating 1,000,000 records...
[=========>          ] 45% | 450,000/1,000,000 | 25,000 rec/s | ETA: 22s
```

### Implementation Approach
- Use ANSI escape codes for cursor control
- Update in-place by moving cursor to line start
- Detect TTY with `System.console() != null`
- Disable in CI/CD environments (non-interactive)

### Progress Updates
- Worker threads update atomic counter
- Progress reporter thread reads counter periodically
- Calculate throughput from counter deltas
- Estimate ETA based on current throughput

### Final Summary
```
Generation complete!
Total records: 1,000,000
Time elapsed: 40.5s
Average throughput: 24,691 records/second
Peak throughput: 28,342 records/second
```

---

## Testing Requirements

### Unit Tests
- ETA calculation logic
- Throughput calculation
- Progress percentage calculation
- TTY detection logic

### Integration Tests
- Run generation with progress enabled
- Verify progress updates appear
- Verify final summary correct
- Test in non-TTY environment (progress disabled)

### Manual Testing
- Run in terminal (see progress bar)
- Run in CI/CD (no progress bar)
- Test with `--no-progress` flag
- Verify ETA accuracy

---

## Definition of Done

- [ ] Progress reporter thread implemented
- [ ] ANSI escape codes for cursor control
- [ ] TTY detection working
- [ ] Progress bar rendering
- [ ] Throughput and ETA calculation
- [ ] Final summary report
- [ ] `--no-progress` CLI flag
- [ ] Unit tests for calculations
- [ ] Integration tests in TTY and non-TTY
- [ ] Manual testing completed
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
