# US-005: Seed Resolution System

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: US-003

---

## User Story

As a **DevOps engineer**, I want **flexible seed resolution from multiple sources** so that **I can ensure reproducible data generation across development, CI/CD, and production environments**.

---

## Acceptance Criteria

- ✅ Resolve seeds from embedded values in YAML
- ✅ Resolve seeds from file paths (read long value from file)
- ✅ Resolve seeds from environment variables
- ✅ Resolve seeds from remote APIs with authentication
- ✅ Support for bearer, basic, and API key authentication
- ✅ All seed sources resolve to long value
- ✅ SeedResolutionException with clear messages on failures
- ✅ Logging of resolved seed values (INFO level)
- ✅ HTTP client with 10-second timeout for remote seeds

---

## Implementation Notes

### SeedResolver Interface
Single method: `long resolve(SeedConfig config)`

### DefaultSeedResolver Implementation
Switch on seed type and delegate to type-specific methods:
- **Embedded**: Return value directly from config
- **File**: Read file content, parse as long
- **Env**: Read environment variable, parse as long
- **Remote**: HTTP GET to URL, parse response as long

### Remote Seed Authentication
Support three authentication types:
1. **Bearer token**: Add `Authorization: Bearer {token}` header
2. **Basic auth**: Base64 encode `username:password`
3. **API key**: Add custom header (default `X-API-Key`)

### Error Handling
- File not found: Clear error with path
- Invalid number format: Clear error with source
- Environment variable not set: Clear error with variable name
- Remote API failure: Include status code and response body
- Network timeout: Clear timeout message

---

## Testing Requirements

### Unit Tests
- Resolve embedded seed successfully
- Resolve file seed with valid file
- Fail on missing file with clear error
- Fail on invalid number in file
- Resolve environment variable seed
- Fail on missing environment variable
- Mock HTTP client for remote seed tests
- Test all three authentication types
- Test timeout and network errors

### Integration Tests
- Write actual seed file and read it
- Set environment variable and resolve it
- Use real HTTP client (or Testcontainers mock server)

---

## Definition of Done

- [ ] SeedResolver interface created
- [ ] DefaultSeedResolver implementation complete
- [ ] Support for all 4 seed types
- [ ] Authentication support for remote seeds
- [ ] SeedResolutionException with clear messages
- [ ] Unit tests with mocked external dependencies
- [ ] Integration tests with real file I/O and HTTP
- [ ] Test coverage >= 90%
- [ ] Proper logging at INFO level
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
