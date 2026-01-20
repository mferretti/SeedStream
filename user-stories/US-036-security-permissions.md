# US-036: File Permission Security

**Status**: ⏸️ Not Started  
**Priority**: P2 (Medium)  
**Phase**: 9 - Security & Compliance  
**Dependencies**: US-016

---

## User Story

As a **security engineer**, I want **file permission validation** so that **configuration files and seed files are not readable by unauthorized users**.

---

## Acceptance Criteria

- ✅ Validate configuration file permissions on startup (Unix-like systems)
- ✅ Warn if config files readable by group or others
- ✅ Fail if seed files not restricted (chmod 600 required)
- ✅ Cross-platform support (Unix/Linux/Mac, graceful on Windows)
- ✅ Clear error messages with remediation steps
- ✅ Documentation of security best practices
- ✅ Recommended permissions documented (600 or 640)

---

## Implementation Notes

### Permission Validation
```java
public class FilePermissionValidator {
    
    public void validateConfigFile(Path configFile) {
        if (isUnix()) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(configFile);
            
            if (perms.contains(PosixFilePermission.GROUP_READ) || 
                perms.contains(PosixFilePermission.OTHERS_READ)) {
                log.warn("Config file {} has permissive permissions. " +
                    "Consider: chmod 640 {}", configFile, configFile);
            }
        }
    }
    
    public void validateSeedFile(Path seedFile) {
        if (isUnix()) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(seedFile);
            
            if (perms.contains(PosixFilePermission.GROUP_READ) || 
                perms.contains(PosixFilePermission.OTHERS_READ)) {
                throw new SecurityException(
                    "Seed file has insecure permissions. Required: chmod 600 " + seedFile);
            }
        }
    }
}
```

### When to Validate
- **Startup**: All config files (job, structure definitions)
- **Seed resolution**: Seed files (type=file)
- **Optional**: Output files (warn if world-writable)

### Recommended Permissions
```bash
# Configuration files (readable by owner and group)
chmod 640 config/jobs/*.yaml
chmod 640 config/structures/*.yaml

# Seed files (readable only by owner)
chmod 600 config/seeds/production.seed

# Output directories (writable only by owner)
chmod 700 output/
```

---

## Testing Requirements

### Unit Tests
- Permission detection on Unix systems
- Warning generation for permissive files
- Error generation for insecure seed files
- Graceful handling on Windows

### Integration Tests
- Create files with various permissions
- Verify warnings/errors generated correctly
- Test on Linux, Mac, Windows (if applicable)

### Manual Testing
- Create config with 644 permissions → Warning
- Create seed with 644 permissions → Error
- Create seed with 600 permissions → Success

---

## Definition of Done

- [ ] FilePermissionValidator class created
- [ ] Config file validation with warnings
- [ ] Seed file validation with errors
- [ ] Cross-platform support (Unix/Windows)
- [ ] Clear error messages with remediation
- [ ] Integration into file loading logic
- [ ] Unit tests for permission checking
- [ ] Integration tests with real files
- [ ] Security best practices documented
- [ ] README updated with permission requirements
- [ ] PR reviewed and approved
