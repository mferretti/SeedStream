# Use Cases

Curated, runnable examples that map SeedStream to a concrete business problem. Each subdirectory is
self-contained — its own `structures/` and `jobs/` — so you can copy one folder, run a single
command, and forward it to a colleague as a complete demo.

These are buyer-facing scenarios. For the exhaustive type/config reference and the kitchen-sink of
sample structures, see [`config/`](../config/) and the [root README](../README.md).

| Use case | Persona | Outcome | Status | Link |
|---|---|---|---|---|
| DORA / GDPR resilience testing | Regulated finance (bank / insurer / fintech) | Reproducible ISO 20022 SEPA dataset for load/resilience tests — no production PII in non-prod | **Ready** | [`dora-gdpr-sepa-payments/`](dora-gdpr-sepa-payments/) |
| Developer environment bootstrapping | Application developer | Realistic local dataset to run against | *Planned* ([#79](https://github.com/mferretti/SeedStream/issues/79)) | — |
| CI pipeline database seeding | Platform / DevOps | Deterministic DB seed per CI run | *Planned* ([#80](https://github.com/mferretti/SeedStream/issues/80)) | — |
| Performance and load testing | Performance engineer | High-volume synthetic data for benchmarks | *Planned* ([#81](https://github.com/mferretti/SeedStream/issues/81)) | — |
| SaaS demo environments | Sales / solutions engineering | Convincing demo data for prospect environments | *Planned* ([#82](https://github.com/mferretti/SeedStream/issues/82)) | — |

*Planned* rows are tracked use-case issues; contributions welcome — follow the
[`dora-gdpr-sepa-payments/`](dora-gdpr-sepa-payments/) layout.
