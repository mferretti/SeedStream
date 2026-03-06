## Description

<!-- Provide a clear and concise description of your changes -->

Fixes # (issue)

## Type of Change

<!-- Mark the relevant option with an "x" -->

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Performance improvement
- [ ] Documentation update
- [ ] Code quality improvement (refactoring, formatting, etc.)
- [ ] Dependency update

## Changes Made

<!-- Describe the changes in detail -->

- 
- 
- 

## Testing

<!-- Describe how you tested your changes -->

- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed
- [ ] Benchmarks run (if performance-related)

**Test coverage:**
- Current coverage: __%
- New coverage: __%

**Commands run:**
```bash
./gradlew test
./gradlew build
# Add any other test commands
```

## Configuration Impact

<!-- If this changes configuration files, provide examples -->

<details>
<summary>Example Configuration Changes (if applicable)</summary>

```yaml
# Before
config:
  old_setting: value

# After  
config:
  new_setting: value
```

</details>

## Performance Impact

<!-- For performance-related changes, provide before/after benchmarks -->

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Throughput | X rec/s | Y rec/s | +Z% |
| Memory | X MB | Y MB | -Z% |

## Documentation

- [ ] Updated relevant documentation (README, DESIGN, PERFORMANCE, etc.)
- [ ] Updated CHANGELOG.md (if user-facing change)
- [ ] Added JavaDoc comments for public APIs
- [ ] Updated configuration examples

## Checklist

<!-- Ensure all items are checked before submitting -->

- [ ] Code follows the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [ ] Code is formatted (`./gradlew spotlessApply`)
- [ ] All tests pass (`./gradlew test`)
- [ ] Static analysis passes (`./gradlew spotbugsMain`)
- [ ] Build is successful (`./gradlew build`)
- [ ] Test coverage meets 70% minimum (if new code added)
- [ ] No wildcard imports
- [ ] Proper use of Lombok annotations
- [ ] Meaningful commit messages following [Conventional Commits](https://www.conventionalcommits.org/)

## Breaking Changes

<!-- If this is a breaking change, describe migration path for users -->

**Migration Guide:**
```
<!-- Explain how users should update their code/config -->
```

## Screenshots / Logs

<!-- If applicable, add screenshots or relevant log output -->

<details>
<summary>Logs (click to expand)</summary>

```
Paste logs here
```

</details>

## Additional Context

<!-- Any other information that reviewers should know -->

## Related Issues/PRs

<!-- Link to related issues or PRs -->

- Related to #
- Depends on #
- Blocks #
