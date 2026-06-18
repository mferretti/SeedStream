# Determinism Demo — recording storyboard (asciinema / GIF)

The runnable proof is [`scripts/determinism-demo.sh`](../../scripts/determinism-demo.sh).
This file is the storyboard for turning it into the asciinema cast / GIF that
goes near the top of the README and into launch posts (campaign T2).

Goal: a ~20-second clip where a viewer sees **the same seed produce the same
SHA-256 at different thread counts** — determinism made visible.

## Record

```bash
# one-time
brew install asciinema agg   # or: pipx install asciinema ; cargo install --git https://github.com/asciinema/agg

# pre-build so the recording isn't dominated by Gradle
./gradlew -q :cli:installDist

# record (keep it tight; the build is already warm)
asciinema rec determinism.cast --idle-time-limit 1.5 --command ./scripts/determinism-demo.sh

# convert to GIF for README/social embeds
agg --font-size 22 --theme monokai determinism.cast determinism.gif
```

Upload the `.cast` to asciinema.org for an interactive embed; commit the `.gif`
under `docs/assets/` for the README and Markdown-only platforms.

## Shot list (what the viewer should see)

1. `$ ./scripts/determinism-demo.sh`
2. `==> Generating 5000 records with --seed 12345, three thread counts…`
3. Three hash lines printing in sequence — **identical** strings:
   ```
   threads=1  sha256=d87c2c64…fa2c44
   threads=4  sha256=d87c2c64…fa2c44
   threads=8  sha256=d87c2c64…fa2c44
   ```
4. The green verdict: `✅ DETERMINISTIC — identical SHA-256 across 1, 4, and 8 threads.`

The punch is the eye seeing three identical hashes stack up under different
thread counts. Don't trim that beat.

## Caption / alt-text (reuse in posts)

> Same seed, three thread counts, one hash. SeedStream's output is byte-for-byte
> reproducible regardless of how the work is parallelised.

## Notes

- The hash above is from the baseline box with `config/jobs/quickstart.yaml`. The
  *value* isn't the point — that all three **match** is. If you change the job or
  record count the digest changes; re-capture so the README text and the GIF agree.
- For a longer "two machines" cut, run the same command on a second host (or a
  different `--threads`) and show the digests still match.
