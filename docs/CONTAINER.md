# Running SeedStream in a Container

SeedStream is a **stateless, run-to-completion CLI**: feed it a job YAML, it
generates *N* records to a destination and exits (`0` = success, non-zero =
failure). That makes it a natural fit for one-shot container primitives —
`docker run --rm`, a Kubernetes `Job`, a CI job step — not a long-running
service. There is no port, no daemon, no state between runs.

The headline property carries into the container: **same seed → byte-for-byte
identical data**, independent of machine, CPU count, or `--threads`. In CI that
means the data a test sees is reproducible and pinnable.

---

## Image

Published to GitHub Container Registry, multi-arch (`linux/amd64`,
`linux/arm64`):

```bash
docker pull ghcr.io/mferretti/seedstream:latest
```

Tags:

| Tag         | Meaning                                                |
|-------------|--------------------------------------------------------|
| `:X.Y.Z`    | Pinned release — **use this in CI** for reproducibility |
| `:X.Y`      | Latest patch of a minor line                           |
| `:latest`   | Latest release — convenient for humans, not for pipelines |
| `:edge`     | Manual pre-release build (bootstrap/testing)           |

The base image is **vendor-neutral**: no JDBC driver is baked in (by design).
DB jobs mount their driver at runtime — see [Database jobs](#database-jobs).

### Build locally instead

```bash
docker build -t seedstream:local .
docker run --rm seedstream:local --help
```

---

## The `/work` layout

`WORKDIR` is `/work`. The CLI is on `PATH` as `cli` and is the `ENTRYPOINT`, so
a run reads as `docker run <image> execute --job ... --count ...`.

```
/work
├── config/    # RO  structures/ + jobs/   (baked sample set; mount to override)
│   ├── structures/
│   └── jobs/
├── out/       # RW  file-destination output  (mount a volume here)
└── extras/    # RO  JDBC drivers, if using a DB destination (mount at runtime)
```

The image defaults `structures_path` to `/work/config/structures`, so job YAML
stays portable between laptop and container with no per-environment edits.

A sample config set (including `jobs/quickstart.yaml`) is baked in, so the
quickstart runs with **zero mounts**. To use your own configs, bind-mount over
`/work/config`:

```bash
docker run --rm \
  -v "$PWD/config:/work/config:ro" \
  -v "$PWD/out:/work/out" \
  -u "$(id -u):$(id -g)" \
  ghcr.io/mferretti/seedstream:latest \
  execute --job config/jobs/file_invoice.yaml --count 50000 --seed 42
```

> **Non-root + bind mounts.** The container runs as UID 10001. A host bind-mount
> stays owned by your host user, so pass `-u "$(id -u):$(id -g)"` to let the
> container write `out/` as you. (Named volumes / `emptyDir` don't need this.)

---

## Resource sizing → throughput

SeedStream has two performance levers, both auto-derived from the container's
limits (Java 21 is container-aware). **You tune throughput by sizing the
container, not by passing flags.**

- **Memory → heap.** Heap scales via `-XX:MaxRAMPercentage` against the cgroup
  memory limit (set in `JAVA_OPTS`). SeedStream is heap-light — 256–512Mi runs
  every scenario; memory is *not* the scaling lever.
- **CPU → threads.** Threads default to the cgroup-visible CPU count
  (`Runtime.availableProcessors()`), so no `--threads` flag is needed normally.
  `--threads` remains available as a manual override.

How much CPU actually helps depends on the destination:

| Destination | CPU       | Mem      | Notes                                       |
|-------------|-----------|----------|---------------------------------------------|
| file        | 1–2 cores | 256–512Mi | IO/overhead-bound ceiling; cores barely help |
| kafka       | 4 cores   | 256–512Mi | serialization scales with cores             |
| database    | 1 core    | 256Mi    | single JDBC write path; more cores don't help |

*(Figures from the June 2026 baseline box; a faster CPU shifts the absolutes,
not the shape. In Kubernetes set `requests.cpu == limits.cpu` to avoid CFS
throttling surprises.)*

---

## Seeds in CI — determinism as a first-class input

Seed resolution: CLI `--seed` > job YAML > default `0`. For pipelines the
`env` seed type is the natural fit. The job declares it once — `config/jobs/file_invoice_env.yaml`
uses `seed: {type: env, name: SEED}` — and you inject the value at run time:

```bash
docker run --rm -e SEED=42 ... execute --job config/jobs/file_invoice_env.yaml
```

Same seed across a PR's runs ⇒ stable golden data; bump the seed to regenerate
fixtures deliberately.

**Generating several datasets at once.** SeedStream resolves a *single* seed per
run — there is no built-in seed list. To produce several
independent-but-reproducible datasets, let your CI fan out: one run per seed,
each deterministic on its own. A GitHub Actions matrix is the idiomatic way —
the matrix launches the job once per value, in parallel:

```yaml
strategy:
  matrix:
    seed: [1, 2, 3]   # 3 parallel jobs, each a different but reproducible dataset
steps:
  - run: docker run --rm -e SEED=${{ matrix.seed }} ... execute --job config/jobs/file_invoice_env.yaml
```

The same effect locally is just a loop: `for s in 1 2 3; do docker run --rm -e SEED=$s ...; done`.

Remote-seed bearer/basic/api_key tokens are **secrets** — inject them from the
secret store via env, never bake them into the image or job YAML.

---

## Database jobs

The base image bundles no JDBC driver. Mount the one you need into
`/work/extras` (it's already on the classpath):

```bash
docker run --rm \
  -v "$PWD/config:/work/config:ro" \
  -v "$PWD/drivers:/work/extras:ro" \
  -e DB_PASSWORD \
  -e SEED=42 \
  ghcr.io/mferretti/seedstream:latest \
  execute --job config/jobs/db_invoice_env.yaml --count 100000
```

Reach the database over the container network
(`jdbc:postgresql://postgres:5432/...`), not `localhost`.

---

## Use case A — seed → test → tear down (the primary CI story)

Spin up Kafka/Postgres, generate the *same* deterministic data every run, run
tests against it, tear it down. Ephemeral, reproducible, no standing fixtures.

### `docker compose`

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment: { POSTGRES_DB: testdb, POSTGRES_USER: t, POSTGRES_PASSWORD: t }
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U t"]
      interval: 2s
      retries: 20

  seed-db:
    image: ghcr.io/mferretti/seedstream:latest
    depends_on: { postgres: { condition: service_healthy } }
    environment:
      JAVA_OPTS: "-XX:MaxRAMPercentage=75.0"
      SEED: "42"
    volumes:
      - ./config:/work/config:ro
      - ./drivers:/work/extras:ro   # postgres jdbc jar
    command: >
      execute --job config/jobs/db_invoice_env.yaml --count 100000
    deploy:
      resources:
        limits: { cpus: "4", memory: 512M }
```

`seed-db` is one-shot: it populates Postgres, exits `0`, and the test job that
`depends_on` it (`condition: service_completed_successfully`) runs against
seeded data.

### Kubernetes `Job`

```yaml
apiVersion: batch/v1
kind: Job
metadata: { name: seed-test-data }
spec:
  backoffLimit: 1            # fail fast in CI, don't retry forever
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: seedstream
          image: ghcr.io/mferretti/seedstream:0.6.1   # pin in CI
          args: ["execute", "--job", "config/jobs/kafka_invoice_env.yaml",
                 "--count", "1000000"]
          env:
            - { name: JAVA_OPTS, value: "-XX:MaxRAMPercentage=75.0" }
            - name: SEED
              valueFrom: { configMapKeyRef: { name: seed-cfg, key: seed } }
          resources:
            requests: { cpu: "4", memory: "256Mi" }
            limits:   { cpu: "4", memory: "512Mi" }
          volumeMounts:
            - { name: config, mountPath: /work/config, readOnly: true }
            - { name: out,    mountPath: /work/out }
      volumes:
        - { name: config, configMap: { name: seedstream-jobs } }
        - { name: out,    emptyDir: {} }
```

---

## Use case B — fixture files as artifacts

File destination only, tiniest setup. Generate to `/work/out`, upload the
directory as a build artifact:

```yaml
- run: |
    mkdir out
    docker run --rm \
      -e SEED=${{ github.run_number }} \
      -v "$PWD/config:/work/config:ro" -v "$PWD/out:/work/out" \
      ghcr.io/mferretti/seedstream:latest \
      execute --job config/jobs/file_invoice.yaml --count 50000 --format csv
- uses: actions/upload-artifact@v4
  with: { name: fixtures, path: out/ }
```

---

## Operational notes

- **Exit codes**: non-zero on any failure (bad config, destination unreachable,
  generation error) — pipelines fail correctly.
- **Security**: runs as UID 10001; compatible with `readOnlyRootFilesystem: true`
  with only `/work/out` writable.
- **Logs**: structured logs to stdout; quiet by default, `--verbose` opt-in.
- **Pin versions in CI** (`:0.6.1`), never `:latest`, so pipelines are
  reproducible.
