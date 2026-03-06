---
name: Bug Report
about: Report a bug or unexpected behavior
title: '[BUG] '
labels: bug
assignees: ''
---

## Bug Description

A clear and concise description of what the bug is.

## Steps to Reproduce

1. Configuration used (attach `config/jobs/*.yaml` and `config/structures/*.yaml` if relevant)
2. Command executed (e.g., `./gradlew :cli:run --args="execute --job ..."`)
3. What happened vs. what you expected

## Expected Behavior

What you expected to happen.

## Actual Behavior

What actually happened. Include error messages, stack traces, or unexpected output.

```
Paste error messages or logs here
```

## Environment

- **SeedStream Version**: (e.g., v0.2.0, or commit SHA)
- **Java Version**: (run `java -version`)
- **OS**: (e.g., Ubuntu 22.04, macOS 14, Windows 11)
- **Destination**: (e.g., File, Kafka, Database)
- **Format**: (e.g., JSON, CSV)

## Configuration Files

<details>
<summary>Job Definition (click to expand)</summary>

```yaml
# Paste your config/jobs/*.yaml here
```

</details>

<details>
<summary>Data Structure (click to expand)</summary>

```yaml
# Paste your config/structures/*.yaml here
```

</details>

## Additional Context

Any other context about the problem (e.g., data volume, performance issues, intermittent vs. consistent failure).

## Workaround

If you found a workaround, please share it to help others.
