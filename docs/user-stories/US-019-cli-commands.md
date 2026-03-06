# US-019: Command-Line Interface

**Status**: ✅ Completed  
**Priority**: P0 (Critical)  
**Phase**: 5 - CLI & Execution  
**Dependencies**: US-013, US-016  
**Completed**: February 21, 2026  
**Implementation**: `cli/src/main/java/com/datagenerator/cli/`
**Key Classes**: DataGeneratorCli.java, ExecuteCommand.java

---

## User Story

As a **user**, I want **a command-line interface to execute generation jobs** so that **I can easily generate test data by providing a job file and optional parameters**.

---

## Acceptance Criteria

- ✅ Picocli-based CLI with clear command structure
- ✅ `execute` command to run generation jobs
- ✅ Required parameter: `--job` (job YAML file path)
- ✅ Optional parameters: `--format`, `--count`, `--seed`
- ✅ Default values: JSON format, 100 records, seed from config
- ✅ `--verbose` and `--debug` flags for logging
- ✅ Help command with usage examples
- ✅ Version command
- ✅ Clear error messages for invalid inputs
- ✅ Exit codes: 0 for success, 1 for errors
- ✅ Validate job file exists before processing

---

## Implementation Notes

### CLI Structure
```bash
seedstream execute --job config/jobs/kafka_address.yaml
seedstream execute --job config/jobs/file_users.yaml --format csv --count 10000 --seed 99999
seedstream --help
seedstream --version
```

### ExecuteCommand
- Load and parse job definition
- Resolve seed (CLI override > config > default 0)
- Load data structure
- Initialize generators, formatters, destinations
- Execute generation loop
- Report summary (records generated, time taken, throughput)

### Parameter Handling
- **--job**: Required, validate file exists
- **--format**: Optional, default "json", validate against enum
- **--count**: Optional, default 100, validate > 0
- **--seed**: Optional, overrides config seed
- **--verbose**: Enable INFO+DEBUG logging
- **--debug**: Enable ALL logging including TRACE

### Error Messages
Clear, actionable error messages:
- "Error: Job file not found: {path}"
- "Error: Invalid format '{format}'. Valid options: json, csv"
- "Error: Count must be positive, got: {count}"

---

## Testing Requirements

### Unit Tests (Picocli)
- Command parsing with valid arguments
- Default values applied correctly
- Invalid argument handling
- Required parameter validation
- Help text generation

### Integration Tests
- Execute actual job with file destination
- Verify output file created and contains records
- Test with different formats
- Test with seed override
- Test error scenarios (missing file, invalid format)

### Manual Testing
- Run CLI from terminal
- Verify help text is clear
- Test tab completion (if supported)
- Test with various job configurations

---

## Definition of Done

- ✅ Main entry point with Picocli (DataGeneratorCli)
- ✅ ExecuteCommand implemented
- ✅ All CLI parameters working (--job, --format, --count, --seed, --verbose)
- ✅ Version command (v1.0.0)
- ✅ Error handling with clear messages
- ✅ Exit codes correct
- ✅ Smart path resolution for structure files
- ✅ Integration tests executing real jobs (end-to-end)
- ✅ Manual testing completed (deterministic output verified)
- ✅ README updated with CLI usage examples
- ✅ Generation statistics (records/sec, elapsed time)
- ✅ Code follows project style guidelines
