# DORA / GDPR resilience testing — ISO 20022 SEPA Credit Transfer

**Persona:** a payments/platform team at an EU bank, insurer, or fintech (regulated financial entity).
**Outcome:** a reproducible, ISO 20022-shaped synthetic SEPA dataset for load- and resilience-testing
a payment pipeline — **without** copying the production ledger into a test environment.

## The scenario

Before a release, the bank must load- and resilience-test its payment-processing and fraud-scoring
pipeline at production volume. Cloning the production ledger drags real customer names and IBANs into
non-prod — a GDPR liability and an ICT-confidentiality risk. Instead, SeedStream generates
ISO 20022-conformant synthetic **SEPA Credit Transfers** from a versioned YAML spec. The job file
*is* the data specification, and the deterministic seed lets an auditor regenerate the exact dataset
independently.

## Why this supports DORA and GDPR

> **Not legal advice, and not a compliance certification.** SeedStream *supports* the principles
> below; it does not make you compliant. Neither regulation imposes a literal ban on real personal
> data in non-production — the benefit derives from the underlying principles and testing
> expectations.

| Capability here | Regulatory principle it supports |
|---|---|
| The YAML job/structure files are the auditable data specification (versioned, reviewable) | **DORA** Art. 24–25 — documented, repeatable digital operational-resilience testing |
| Deterministic seed → same `--seed` reproduces the exact dataset, byte-for-byte | **DORA** Art. 24–25 — reproducible test evidence an auditor can independently regenerate |
| Data is synthetic — no real person's details are processed | **GDPR** Recital 26 — anonymous/synthetic data is not personal data, so it falls outside GDPR scope |
| Realistic data generated instead of copying production | **GDPR** Art. 5(1)(c) data minimisation; **Art. 25** data protection by design and by default |
| Real customer PII stays out of the test environment | **DORA** Art. 9 — protection and prevention (ICT confidentiality in test contexts) |

## Schema → ISO 20022 / EPC mapping

Grain is **one row per credit transfer** (one `CdtTrfTxInf`) — how a payments ledger / test table is
actually stored. Batch-envelope aggregates (`GrpHdr/NbOfTxs`, `CtrlSum`) are intentionally omitted:
they must equal the batch contents, and a per-row generator cannot correlate them.

`structures/sepa_credit_transfer.yaml`:

| field | datatype | ISO 20022 / EPC element | notes |
|---|---|---|---|
| `msg_id` | `sepa_msg_id` (`regex:`) | `GrpHdr/MsgId` | max 35 — structured issuer scheme |
| `end_to_end_id` | `sepa_e2e_id` (`regex:`) | `CdtTrfTxInf/PmtId/EndToEndId` | max 35 — structured debtor ref |
| `creation_date_time` | `timestamp[...]` | `GrpHdr/CreDtTm` | ISO datetime |
| `requested_execution_date` | `date[...]` | `PmtInf/ReqdExctnDt` | YYYY-MM-DD |
| `payment_method` | `enum[TRF]` | `PmtInf/PmtMtd` | SEPA allows **TRF** only |
| `charge_bearer` | `enum[SLEV]` | `PmtInf/ChrgBr` | SEPA is always **SLEV** |
| `instructed_amount` | `decimal[0.01..50000.00]` | `Amt/InstdAmt` | EUR, 2 dp (demo cap) |
| `currency` | `enum[EUR]` | `Amt/InstdAmt/@Ccy` | SEPA is **EUR** |
| `purpose_code` | `enum[SALA,SUPP,TAXS,TRAD,RENT,LOAN,PENS,SSBE]` | `Purp/Cd` | ISO ExternalPurpose |
| `category_purpose_code` | `enum[SALA,SUPP,TAXS,TRAD,CASH]` | `CtgyPurp/Cd` | ISO ExternalCategoryPurpose |
| `remittance_info` | `sepa_remittance` (`regex:`) | `RmtInf/Ustrd` | invoice-style free text, ≤ 140 chars |
| `status` | `enum[ACTC,ACCP,ACSP,ACSC,ACWC,PDNG,RJCT]` | `pain.002` `TxSts` | ISO ExternalPaymentTransactionStatus |
| `debtor` | `object[sct_party]` | `Dbtr` + `DbtrAcct` + `DbtrAgt` | Italian originator |
| `creditor` | `object[sct_creditor]` | `Cdtr` + `CdtrAcct` + `CdtrAgt` | SEPA-wide destination |

`structures/sct_party.yaml` (debtor / originator) and `structures/sct_creditor.yaml` (creditor /
destination) share the same fields; they differ only in the IBAN scope:

| field | debtor `datatype` | creditor `datatype` | ISO 20022 element |
|---|---|---|---|
| `name` | `full_name` | `full_name` | `Nm` (max 70) |
| `iban` | `iban` (locale → `IT`) | `sepa_iban` (random SEPA country) | `Acct/Id/IBAN` |
| `bic` | `bic` | `bic` | `Agt/FinInstnId/BICFI` (8 or 11 chars) |
| `country` | `country_code` | `country_code` | `PstlAdr/Ctry` (ISO 3166 alpha-2) |

**`pain.002` status codes:** `ACTC` AcceptedTechnicalValidation · `ACCP` AcceptedCustomerProfile ·
`ACSP` AcceptedSettlementInProcess · `ACSC` AcceptedSettlementCompleted · `ACWC` AcceptedWithChange ·
`PDNG` Pending · `RJCT` Rejected.

A SEPA credit transfer originates domestically but can settle anywhere in the SEPA zone. The
**debtor** uses the locale-aware `iban` type (`geolocation: italy` → Italian `IT…` account,
matching the Italian name/BIC). The **creditor** uses `sepa_iban` — a random IBAN from a SEPA-zone
country (DE, FR, ES, NL, …, occasionally IT for a domestic transfer). For accounts *outside* SEPA,
the `random_iban` type (any country worldwide) is also available.

## Run it

No infrastructure required — writes JSON to a file. **Run from the repository root** (the output
path and the auto-resolved `structures/` directory are relative to the job file):

```bash
./gradlew :cli:installDist
./cli/build/install/seedstream/bin/seedstream execute \
  --job use-cases/dora-gdpr-sepa-payments/jobs/file_sepa_credit_transfer.yaml \
  --faker-types use-cases/dora-gdpr-sepa-payments/faker-types.yaml --count 20
# → build/run-output/sepa_credit_transfers.json  (one JSON object per line)
```

The job omits `structures_path` on purpose: because it lives in a directory ending in `jobs`, the
CLI auto-resolves the sibling `../structures/` directory. That makes this folder self-contained and
relocatable.

**`--faker-types` is required here.** `msg_id`, `end_to_end_id` and `remittance_info` use *custom*
types (`sepa_msg_id`, `sepa_e2e_id`, `sepa_remittance`) defined in `faker-types.yaml` alongside this
use case — structured `regex:` patterns that shape the ISO 20022 identifiers instead of plain random
text. Custom types are registered explicitly via the flag; omit it and generation fails fast with an
unknown-type error. (A future job-declarable `faker_types:` key —
[#190](https://github.com/mferretti/SeedStream/issues/190) — would remove the need for the flag.)

**Reproducibility:** re-run with the same seed and the output is byte-for-byte identical — hand the
job file + seed to an auditor and they regenerate exactly your dataset.

**Production-scale target:** `jobs/db_sepa_credit_transfer.yaml` retargets the *same* structures to a
Postgres `payments` table (seed from the `SEED` env var, password from `${DB_PASSWORD}`) — load
millions of rows into a real test database instead of a file.

## Honest limits

- **Models the payment *data*, not the `pain.001` XML envelope.** SeedStream emits JSON/CSV/Avro,
  not ISO 20022 XML. A `pain.001` XML serializer would be a separate feature.
- **`msg_id` / `end_to_end_id` shapes are an *example* issuer scheme, not a standard.** ISO 20022
  constrains these only by max length, character set and uniqueness — the format is set by the
  initiating party. The `regex:` patterns in `faker-types.yaml` are one plausible scheme. Because
  each value is generated independently they are not guaranteed unique (ISO expects uniqueness per
  initiating party for a period); at these entropies collisions are negligible at demo scale, but a
  truly unique/sequential ID generator is a separate concern.
- **`remittance_info` is unstructured (`Ustrd`) invoice-style text, not a valid RF creditor
  reference.** A structured ISO 11649 "RF" reference needs a mod-97 check digit, which a regex
  cannot compute — a real generator for that is tracked in
  [#191](https://github.com/mferretti/SeedStream/issues/191).
- **Fields are independent** — there is no cross-field correlation (e.g. `requested_execution_date`
  is not guaranteed ≥ `creation_date_time`; the creditor's `name`/`country` are Italian-locale and
  need not match its `sepa_iban` country).
