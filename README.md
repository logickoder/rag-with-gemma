# Local Medical RAG with Gemma & sqlite-vec

Fully on-device, section-aware Medical RAG for Android. Mirrors the iOS app:
chat UI, population-filtered retrieval (Adult / Pediatric / Geriatric),
drug-drug interaction module (stubbed), and a user-selectable runtime:

- **AI Mode** — quantized Google Gemma (~2.6 GB) via MediaPipe LLM Inference.
- **Semantic Mode** — no LLM at all; extractive sentence ranking over MobileBERT
  embeddings rendered as structured markdown. The default on a fresh install.

Switchable at first launch and anytime from **Settings**.

## Architecture

- **UI:** Jetpack Compose + `navigation-compose`. Onboarding → Home → Chat / Settings.
- **DI:** Hand-rolled `AppContainer` on a custom `Application` (`RagApplication`).
- **State:** `BootstrapViewModel` owns asset resolution, ingestion progress, and
  the active `Consultant`. It rebuilds the consultant reactively when
  `UserPrefs.modeFlow` changes.
- **Embedder:** MediaPipe Text Embedder, `mobile_bert.tflite`, 512-dim.
- **Database:** Room 2.8 with `BundledSQLiteDriver` (coroutine connection API).
  Entities: `drug`, `drug_chunk`. Four `vec0` virtual tables — one per
  population — to keep filtered KNN cheap:
  `vss_chunks_adult`, `vss_chunks_pediatric`, `vss_chunks_geriatric`,
  `vss_chunks_general`.
- **LLM:** MediaPipe `tasks-genai:0.10.33` with Gemma chat-template
  `PromptTemplates`. iOS-parity structured prompt + post-stream scrub + missing
  critical-section appendix + deterministic fallback when LLM output is too short.

## Data

Source switched from openFDA to Medscape:
<https://img.staging.medscape.com/pi/iphone/medscapeapp/UE/u-drugcontent-json-template-139.zip>
(~55 MB, 3146 drug JSON files). Section numbers map to population + clinical
label (see [SectionMap.kt](app/src/main/java/dev/logickoder/ragwithgemma/data/ingestion/SectionMap.kt)):

| SectionNumber | Population | Label |
|---|---|---|
| 0 | Adult | Dosing |
| 1 | Pediatric | Dosing |
| 13 | Geriatric | Dosing |
| 3 | General | Drug Interactions |
| 4 | General | Adverse Effects |
| 5 | General | Warnings & Contraindications |
| 6 | General | Pregnancy & Lactation |
| 10 | General | Pharmacology |
| 11 | General | Administration |

Ingestion: `MedscapeIngestor` walks each JSON, groups items under their h3/h4
sub-headers, embeds each chunk with MobileBERT, and stores embeddings into the
population-specific `vec0` table. Progress streams to the UI.

## Prerequisites

- **Physical Android device (ARM64).** Emulators crash with `SIGILL` inside
  XNNPACK — SIMD (NEON/SVE/SME2) not mapped.
- **≥ 6 GB free internal storage** when using AI Mode (2.6 GB model + XNNPACK cache).
- `adb` with USB debugging enabled.
- `bash`, `curl`, `unzip` on the dev machine.

## Setup (debug)

```bash
chmod +x scripts/*.sh
./scripts/setup.sh    # interactive menu: download LLM, push LLM, sideload drugs
```

The scripts also work individually:

| Script | What it does |
|---|---|
| `scripts/download_llm.sh` | Prompts for an HTTPS URL or absolute local path to the Gemma `.litertlm` and stores it at `artifacts/gemma-4-E2B-it.litertlm`. |
| `scripts/push_llm.sh` | `adb push` the model to `/data/local/tmp/`. |
| `scripts/sideload_drugs.sh` | `curl` the Medscape zip (override with `DRUGS_ZIP_URL=…`), unzip into `artifacts/medscape_drug_jsons/`, then `adb push` to `/data/local/tmp/`. Skips work already done. |

In a **release** build, the Medscape zip is downloaded on first launch into
`filesDir/medscape_drug_jsons/` instead (requires `INTERNET` permission).

### Native vector engine

Android uses Bionic libc, not glibc — use the Android aarch64 build of
sqlite-vec:

1. Download from <https://github.com/asg017/sqlite-vec/releases>.
2. Rename to `libsqlite_vec.so` and place at
   `app/src/main/jniLibs/arm64-v8a/libsqlite_vec.so`.

`v0.1.10-alpha.3` does **not** declare the `k` hidden column on vec0 tables.
Use a stable release (e.g. `v0.1.9`) if you hit `no such column: k` or
persistent `unable to use function MATCH` errors.

### Embedder asset

Place `mobile_bert.tflite` at `app/src/main/assets/mobile_bert.tflite`.

## Running

```bash
./gradlew installDebug && adb shell am start -n dev.logickoder.ragwithgemma/.MainActivity
```

First launch:
1. **Onboarding** card picker — choose **AI Mode** or **Semantic Mode**.
2. App resolves embedder, creates vec0 tables, parses Medscape JSONs, embeds
   each chunk, and inserts into the population-specific `vss_chunks_*` table.
   Progress is shown on the Home screen.
3. Active consultant is built. AI Mode loads Gemma; Semantic Mode is instant.

Subsequent launches skip ingestion (gated by an `ingestion_complete` flag in
DataStore).

Verify schema:

```bash
adb shell run-as dev.logickoder.ragwithgemma \
  sqlite3 databases/medical_rag.db ".tables"
# drug, drug_chunk, vss_chunks_adult, vss_chunks_pediatric,
# vss_chunks_geriatric, vss_chunks_general
```

## Chat behaviour

- Type a bare drug name → satisfactory full clinical profile
  (`getSatisfactorySummary`).
- Type a question containing a drug name → semantic answer
  (`getSemanticAnswer`) with detected population (Adult/Pediatric/Geriatric),
  top-K vec0 hits in the per-population table, hybrid keyword-name boost,
  grouped by section, sent to the active `Consultant`.
- Follow-up question without a drug name → carries the last mentioned drug from
  history.
- AI Mode: streams tokens as they arrive, then replaces the final assistant
  message with the cleaned + appendix-augmented version.
- Semantic Mode: one-shot rendered markdown (`### drug` + `#### section` +
  bullet sentences), no LLM.

## Critical gotchas

### Little-endian vector serialization

`ByteBuffer` defaults to big-endian. sqlite-vec reads blobs as little-endian
`float32`. Without `order(ByteOrder.LITTLE_ENDIAN)`, distances are computed on
garbage memory → random RAG results.

### XNNPACK cache location

MediaPipe caches weights in `context.cacheDir` by default. For a 2.6 GB model
this blows the internal partition quota (`SIGABRT: Inserting data in the cache
failed`). `LlmConsultant` overrides `getCacheDir()` via a `ContextWrapper` to
redirect to `externalCacheDir`.

### Don't let Room manage the vec0 tables

If you register a vec0 table as a Room `@Entity`, Room's schema validation
(`fallbackToDestructiveMigration`) drops the virtual table and recreates it as
a plain `CREATE TABLE`, at which point `MATCH` falls through to SQLite's core
`matchFunc` and throws "unable to use function MATCH in the requested
context". Keep all `vss_chunks_*` tables out of `@Database(entities=…)` and
create them explicitly in `AppDatabase.createVecTables()`.

### Room + vec0 query shape

Room's query verifier rejects `MATCH` and unknown hidden columns. All
`vss_chunks_*` queries are annotated `@SkipQueryVerification`. Query shape
must be recognizable to vec0's `xBestIndex`:

```sql
SELECT rowid, distance FROM vss_chunks_<pop>
WHERE embedding MATCH :query
ORDER BY distance LIMIT :topK
```

Join the metadata table in Kotlin on the returned rowids, not in SQL — joining
vec0 and a rowid table in one query confuses the planner and resurrects the
`MATCH` error.

### Gemma chat-template stop handling

MediaPipe `tasks-genai:0.10.33` does **not** expose `setStopTokens`. Even with
`PromptTemplates` configured, the runtime can leak markers into streamed
output and keep generating. `LlmConsultant` buffers partials, scans for
`<end_of_turn>` / `<eos>`, and closes the callback flow + session manually.

### MediaPipe/LiteRT log spam

MediaPipe floods logcat and trips Android's per-process logd quota (`LOGS
OVER PROC QUOTA(300)`), dropping your own `Log.d`. Raise logcat buffer size
in **Developer options → Logger buffer sizes** (16 M) and reboot, or dump
`adb logcat > file` and grep locally.

## Open items

- **Drug-drug interaction DB** — stubbed (`InteractionRepository.findInteractions`
  returns a failure with a "pending" message). On iOS the sqlite was built from
  separate per-table CSVs joined with Python + Langchain (per Madhu, the
  generator). The embedding column on iOS turned out to be unused — Android
  should ingest the CSVs (or a joined sqlite) and **drop** the embedding field
  from `data/model/Interaction.kt` when wiring. AI insight is generated per row
  from the joined text (`SubjectName`/`ObjectName`/`Direction`/`Effect`/
  `Strength`/`Comment`/`MechScript`).
- **Release-time zip download** — `AssetBootstrap.resolveDrugJsons` performs
  it on first launch with raw `URL.openConnection()`. Replace with OkHttp +
  resumable downloads if the zip URL becomes flaky in the field.
- **Token-budget enforcement for history** — currently a fixed 6-turn window;
  refine if prompt + history exceeds Gemma's 1024-token limit.
