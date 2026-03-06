# TASK-009: Code Quality - Import Refactoring

**Status**: ✅ Complete  
**Priority**: P1 (High)  
**Phase**: 1 - Core Foundation  
**Dependencies**: TASK-001 through TASK-008  
**Human Supervision**: NONE (automated refactoring)

---

## Objective

Refactor all Java source files to eliminate wildcard imports and use explicit individual imports for each class, improving code readability and maintainability.

---

## Background

Current codebase may contain wildcard imports (`import java.util.*;`) which should be replaced with explicit imports for better:
- Code readability
- IDE performance
- Dependency tracking
- Avoiding naming conflicts

**Exception**: Static test assertion methods can use wildcards (e.g., `import static org.assertj.core.api.Assertions.*;`)

---

## Implementation Details

### Step 1: Configure IDE Import Settings

For IntelliJ IDEA, set:
- **Settings → Editor → Code Style → Java → Imports**
- "Class count to use import with '*'": 999
- "Names count to use static import with '*'": 999
- This prevents auto-generation of wildcard imports

### Step 2: Run Automated Refactoring

Use IDE's "Optimize Imports" feature:

**IntelliJ IDEA**:
```
1. Select project root in Project view
2. Code → Optimize Imports (Ctrl+Alt+O)
3. Select "Optimize imports for all files in directory"
4. Confirm
```

**Eclipse**:
```
1. Select project root
2. Source → Organize Imports
3. Apply to all files
```

### Step 3: Manual Verification

Check for any remaining wildcard imports:
```bash
grep -r "import .*\.\*;" --include="*.java" core/ schema/ generators/ formats/ destinations/ cli/
```

Expected: Only static test imports like `import static org.assertj.core.api.Assertions.*;`

### Step 4: Remove Unused Imports

Run code inspection:
```bash
./gradlew clean build
```

Fix any "unused import" warnings.

---

## Acceptance Criteria

- ✅ No wildcard imports in production code
- ✅ Static test assertion wildcards are allowed
- ✅ No unused imports
- ✅ All code compiles and passes tests
- ✅ Spotless formatting maintained

---

## Testing

Verify no wildcard imports:
```bash
# Should only find static test imports
grep -r "import .*\.\*;" --include="*.java" src/
```

Build and test:
```bash
./gradlew clean build test
```

---

**Completion Date**: [Mark when complete]
