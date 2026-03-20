# SeedStream — Troubleshooting & FAQ

---

## Common Errors

### "No GeneratorContext active"

**Cause**: Using `ObjectGenerator` in a custom multi-threaded setup without initialising context per thread.

**Fix**: Wrap generation code in `GeneratorContext.enter()`:
```java
try (var ctx = GeneratorContext.enter(factory, geolocation)) {
    generator.generate(random, objectType);
}
```

---

### "Circular reference detected: A → B → A"

**Cause**: Structure definitions have circular dependencies.

```yaml
# user.yaml references profile, profile.yaml references user → cycle
data:
  profile:
    datatype: object[profile]   # ❌
```

**Fix**: Redesign structures to avoid cycles. Terminate recursion with a primitive type.

---

### "Seed resolution failed: Remote API returned 404"

**Cause**: Remote seed API endpoint is unreachable or misconfigured.

**Fix**:
- Check `url` in seed config
- Verify auth credentials
- Test manually: `curl -H "Authorization: Bearer TOKEN" https://seed-api.example.com/api/seed`
- Use `--seed` CLI override as a fallback

---

### "Failed to parse data structure: Unknown datatype 'xyz'"

**Cause**: Typo or unsupported type in the structure definition.

**Fix**: Check spelling against the [Type System Reference](../config/README.md#type-system-reference). Valid types:
- Primitives: `char`, `int`, `decimal`, `boolean`, `date`, `timestamp`, `enum`
- Semantic: `uuid`, `name`, `email`, `phone_number`, `address`, `city`, `company`, etc.
- Composite: `object[...]`, `array[...]`

---

### "FileNotFoundException: config/structures/address.yaml"

**Cause**: Referenced structure file doesn't exist at the expected path.

**Fix**:
- Confirm the file exists (paths are case-sensitive on Linux)
- Verify it is in the `structures_path` directory (default: `config/structures/`)
- Check the `source` field in the job YAML

---

### Different output with the same seed

The following must be **identical** across runs for output to match:
- Job configuration
- Structure definition
- SeedStream version
- Record count (`--count`)
- Thread count (`--threads`)

---

## Debug Mode

```bash
# Verbose: progress, seed resolution, file paths, throughput
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --verbose"

# Debug: trace sampling (default 10% of records)
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --debug"

# Adjust trace sample rate
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --debug --trace-sample-rate 50"

# JVM-level debugging
./gradlew :cli:run --args="execute --job config/jobs/file_address.yaml --verbose" --debug-jvm
```

**Verbose output includes**: seed resolution, absolute file paths, progress every 100 records, throughput metrics, worker thread activity.

---

## Performance Issues

**Symptom**: Generation slower than expected.

**Diagnostics**:
1. **Check data complexity** — Datafaker is ~1,000× slower than primitives
2. **Baseline hardware** — run `./benchmarks/run_e2e_test.sh`
3. **Profile** — add custom JMH benchmarks for your specific structures
4. **Monitor threads** — use `--verbose` to see worker activity

**Common causes**:
- Too many Datafaker fields (replace with primitives where realism isn't needed)
- Deeply nested objects (flatten where possible)
- Small batch size for Kafka/DB (`conf.batch_size`)
- Disk I/O bottleneck (`iostat`, consider disabling compression)

---

## FAQ

**Q: Can I generate data without a seed?**
Yes, but you'll get a warning. Default seed `0` is used. For reproducible output, always specify a seed.

**Q: How do I generate different data each run?**
```bash
--seed $(date +%s)   # Unix timestamp
--seed $RANDOM       # Random number
```

**Q: What is the maximum array size?**
No hard limit, but arrays > 1000 elements may affect performance and memory. Consider streaming for very large arrays.

**Q: Can I use custom Datafaker providers?**
Drop a custom provider JAR into the `extras/` directory — it is added to the classpath at startup. The `DatafakerRegistry` supports runtime type registration. Full ServiceLoader-based plugin architecture is planned for v1.0.

**Q: How do I generate timestamps relative to today?**
```yaml
created_at:
  datatype: timestamp[now-30d..now]   # last 30 days
```

**Q: What is the difference between `char` and `name`?**
`char` generates random alphanumeric strings (e.g., `"AbCdEf"`). `name` uses Datafaker to produce realistic person names (e.g., `"John Smith"`).

**Q: Can I contribute new generators or destinations?**
Yes — see [CONTRIBUTING.md](CONTRIBUTING.md). We welcome PRs for new semantic types, destinations (S3, Azure Blob, Elasticsearch), formats (Avro, Parquet), and performance optimisations.

**Q: Is there a REST API?**
Not yet. Planned for v0.6. Current interface is CLI only.

**Q: How do I handle sensitive data (PII)?**
All generated data is synthetic — not real PII. That said:
- Store seeds securely (they can reproduce the data)
- Use encryption for data at rest if needed
- Follow your organisation's data governance policies

**Q: Why does `--version` show a wrong version?**
Ensure you are running the official release JAR or a fresh build. The version is embedded in the JAR manifest at build time.
