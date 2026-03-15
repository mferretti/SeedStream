# TASK-036: Security - File Permission Checks

**Status**: ✅ Complete
**Completion Date**: March 15, 2026
**Priority**: P2 (Medium)  
**Phase**: 9 - Security & Compliance  
**Dependencies**: TASK-016 (File Destination)  
**Human Supervision**: LOW

---

## Objective

Implement file permission validation to ensure configuration files and seed files have appropriate restricted permissions (readable only by owner).

---

## Implementation Details

### Permission Checks

```java
public class FilePermissionValidator {
    
    public void validateConfigFile(Path configFile) {
        // Check permissions on Unix-like systems
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(configFile);
            
            // Warn if readable by group or others
            if (perms.contains(PosixFilePermission.GROUP_READ) || 
                perms.contains(PosixFilePermission.OTHERS_READ)) {
                log.warn("Configuration file {} has permissive permissions. " +
                    "Consider restricting to owner-only (chmod 600)", configFile);
            }
        }
    }
    
    public void validateSeedFile(Path seedFile) {
        // Seed files should be 600 (owner read/write only)
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(seedFile);
        
        if (perms.contains(PosixFilePermission.GROUP_READ) || 
            perms.contains(PosixFilePermission.OTHERS_READ)) {
            throw new SecurityException(
                "Seed file has insecure permissions. Set to 600: chmod 600 " + seedFile);
        }
    }
}
```

### Check on Startup
- Validate all configuration files
- Validate seed files if type is `file`
- Log warnings for permissive permissions
- Fail on insecure seed files

### Documentation
Add security section to README:
```markdown
## Security Best Practices

### File Permissions
- Configuration files: `chmod 640` or `600`
- Seed files: `chmod 600` (required)
- Output files: `chmod 640` or `600`

### Secrets
- Never commit secrets to version control
- Use environment variables or secret managers
- Rotate secrets regularly
```

---

## Acceptance Criteria

- ✅ Permission validation on startup
- ✅ Warnings for permissive config files
- ✅ Errors for insecure seed files
- ✅ Cross-platform support (Unix/Windows)
- ✅ Documentation updated

---

**Completion Date**: [Mark when complete]
