# TASK-032: CLI Module - Verbose Logging Modes

**Status**: ⏸️ Not Started  
**Priority**: P2 (Medium)  
**Phase**: 5 - CLI & Execution  
**Dependencies**: TASK-019 (CLI Commands)  
**Human Supervision**: NONE

---

## Objective

Implement verbose and debug logging modes for troubleshooting, controlled via CLI flags `--verbose` and `--debug`.

---

## Implementation Details

### Logging Levels
- **Default**: INFO (errors, warnings, progress)
- **Verbose** (`--verbose`): INFO + DEBUG (configuration, major operations)
- **Debug** (`--debug`): All levels including TRACE (every operation)

### Configuration
Use Logback with programmatic level switching:

```java
Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
if (debug) {
    root.setLevel(Level.DEBUG);
} else if (verbose) {
    root.setLevel(Level.INFO);
}
```

### Output Format
```
2026-01-20 10:15:30 INFO  [main] - Starting generation: 100,000 records
2026-01-20 10:15:30 DEBUG [main] - Resolved seed: 12345
2026-01-20 10:15:30 DEBUG [main] - Loaded structure: address (5 fields)
2026-01-20 10:15:31 TRACE [worker-0] - Generated record: {name=John, city=Milan}
```

---

## Acceptance Criteria

- ✅ `--verbose` enables INFO/DEBUG logging
- ✅ `--debug` enables all logging including TRACE
- ✅ Default mode shows only INFO+
- ✅ Configurable via CLI flags
- ✅ Does not impact performance significantly

---

**Completion Date**: [Mark when complete]
