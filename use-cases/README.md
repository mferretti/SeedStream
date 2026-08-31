# Use Cases

Curated, runnable examples that map SeedStream to a concrete business problem. Each subdirectory is
self-contained — its own `structures/` and `jobs/` — so you can copy one folder, run a single
command, and forward it to a colleague as a complete demo.

These are buyer-facing scenarios. For the exhaustive type/config reference and the kitchen-sink of
sample structures, see [`config/`](../config/) and the [root README](../README.md).

| Use case | Persona | Outcome | Status | Link |
|---|---|---|---|---|
| DORA / GDPR resilience testing | Regulated finance (bank / insurer / fintech) | Reproducible ISO 20022 SEPA dataset for load/resilience tests — no production PII in non-prod | **Ready** | [`dora-gdpr-sepa-payments/`](dora-gdpr-sepa-payments/) |
| Developer environment bootstrapping | Application developer | One command fills a local DB with a linked users/orders/line-items dataset — no prod dump | **Ready** | [`dev-env-bootstrapping/`](dev-env-bootstrapping/) |
| CI pipeline database seeding | Platform / DevOps | Truncate-and-reseed a disposable DB before integration tests — identical rows, identical ids, every run | **Ready** | [`ci-pipeline-seeding/`](ci-pipeline-seeding/) |
| Performance and load testing | Performance engineer | High-volume synthetic data for benchmarks | *Planned* ([#81](https://github.com/mferretti/SeedStream/issues/81)) | — |
| SaaS demo environments | Sales / solutions engineering | One command fills a demo tenant with a linked accounts/contacts/deals/activities CRM dataset — no customer PII, identical every reseed | **Ready** | [`saas-demo-environments/`](saas-demo-environments/) |

*Planned* rows are tracked use-case issues; contributions welcome — follow the
[`dora-gdpr-sepa-payments/`](dora-gdpr-sepa-payments/) layout.
