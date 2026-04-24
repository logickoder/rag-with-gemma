# Local Medical RAG with Gemma & sqlite-vec

Fully on-device Retrieval-Augmented Generation (RAG) for Android. Runs a quantized Google Gemma
model (~2.6 GB) and a semantic vector search engine (sqlite-vec) locally, no cloud.

## Architecture

- **LLM Engine:** MediaPipe LLM Inference API (`gemma-4-E2B-it.litertlm`), wrapped with Gemma
  chat-template `PromptTemplates` on a per-query `LlmInferenceSession`.
- **Text Embedder:** MediaPipe Text Embedder (`mobile_bert.tflite`, 512-dim output).
- **Database:** Room 2.8 with `BundledSQLiteDriver` (new coroutine connection API, not legacy
  SupportSQLite).
- **Vector DB:** sqlite-vec loadable C extension. `vec0` virtual table holds 512-dim `float[]`
  embeddings; KNN via `MATCH` + `ORDER BY distance LIMIT k`.

## Prerequisites

- **Physical Android device (ARM64).** Emulators crash with `SIGILL` inside XNNPACK — SIMD (
  NEON/SVE/SME2) not mapped.
- **≥ 6 GB free internal storage** on the device (2.6 GB model + ~2.5 GB XNNPACK cache).
- **adb** with USB debugging enabled.

## Setup

### 1. Download assets

Place in `app/src/main/assets/`:

- `mobile_bert.tflite` — text embedder
- `drugs.json` — FDA payload for initial ingestion

Do **not** put the Gemma model in `assets/`. The APK would balloon to 2.6 GB and the package
installer crashes with `java.io.IOException: Requested internal only, but not enough space`.

### 2. Vector engine native lib

Android uses Bionic libc, not glibc — use the Android aarch64 build of sqlite-vec:

1. Download the Android aarch64 tarball from https://github.com/asg017/sqlite-vec/releases.
2. Extract the `.so`.
3. Rename to exactly `libsqlite_vec.so`.
4. Place at `app/src/main/jniLibs/arm64-v8a/libsqlite_vec.so`.

**Known-broken:** `v0.1.10-alpha.3` does not declare the `k` hidden column on vec0 tables. Use a
stable release (e.g. `v0.1.9`) if you hit `no such column: k` or persistent
`unable to use function MATCH` errors.

### 3. Sideload the Gemma model

Keep the model out of the APK. Push it to a location that survives app uninstall:

```bash
adb push /path/to/gemma-4-E2B-it.litertlm /data/local/tmp/
adb shell ls -lh /data/local/tmp/gemma-4-E2B-it.litertlm
```

The engine reads directly from `/data/local/tmp/gemma-4-E2B-it.litertlm`. App uid has execute
permission on that dir on most AOSP builds; if your device blocks it, copy the file into `filesDir`:

```bash
adb shell "cat /data/local/tmp/gemma-4-E2B-it.litertlm | run-as dev.logickoder.ragwithgemma sh -c 'cat > files/gemma-4-E2B-it.litertlm'"
```

…and change the path
in [MedicalRagEngine.initialize](app/src/main/java/dev/logickoder/ragwithgemma/domain/MedicalRagEngine.kt)
back to `File(context.filesDir, ...)`.

### 4. Run

On first launch the engine:

1. Creates the `vec0` virtual table via `AppDatabase.createVecTable()` (
   `useWriterConnection { conn -> conn.execSQL("CREATE VIRTUAL TABLE ...") }`).
2. Parses `drugs.json`.
3. Embeds each drug's indications with MobileBERT.
4. Inserts into `vss_drug_embeddings` with the original rowid linking back to `drug_metadata`.

## Querying

Try drug names present in `drugs.json`: Betadine, Naproxen, ofloxacin, povidone-iodine, benzalkonium
chloride, sertraline, acetaminophen, tamsulosin, glimepiride, etodolac, etc. Example:
`"What is Betadine used for?"`.

## Critical gotchas

### Little-endian vector serialization

`ByteBuffer` defaults to big-endian. sqlite-vec reads blobs as little-endian float32. Without
`order(ByteOrder.LITTLE_ENDIAN)` in `floatArrayToByteArray`, distances are computed on garbage
memory → random RAG results.

### XNNPACK cache location

MediaPipe caches weights in `context.cacheDir` by default. For a 2.6 GB model this blows the
internal partition quota (`SIGABRT: Inserting data in the cache failed`). The engine overrides
`getCacheDir()` via a `ContextWrapper` to redirect to `externalCacheDir`.

### Don't let Room manage the vec0 table

If you register the vec0 table as a Room `@Entity`, Room's schema validation (
`fallbackToDestructiveMigration`) drops the virtual table and recreates it as a plain
`CREATE TABLE`, at which point `MATCH` falls through to SQLite's core `matchFunc` and throws "unable
to use function MATCH in the requested context". Keep `vss_drug_embeddings` out of
`@Database(entities=...)` and create it explicitly via `useWriterConnection`.

### Room + vec0 query shape

Room's query verifier rejects `MATCH` and unknown hidden columns. Annotate vec queries with
`@SkipQueryVerification`. Query shape must be recognizable to vec0's `xBestIndex` — simplest
pattern:

```sql
SELECT rowid, distance FROM vss_drug_embeddings
WHERE indicationEmbedding MATCH :queryEmbedding
ORDER BY distance LIMIT :topK
```

(Requires SQLite ≥ 3.41 on the vec0 side; `androidx.sqlite:sqlite-bundled:2.6.2` ships 3.50.1.) Join
the metadata table in Kotlin on the returned rowids rather than in SQL — joining vec0 and a rowid
table in one query confuses the planner and resurrects the `MATCH` error.

### Gemma chat-template stop handling

MediaPipe `tasks-genai:0.10.33` does **not** expose `setStopTokens`. Even with `PromptTemplates`
configured for `<start_of_turn>` / `<end_of_turn>`, the runtime can leak those markers into streamed
output and keep generating until `maxTokens`. Mitigation: buffer partials in the stream callback,
scan for `<end_of_turn>` / `<eos>`, and close the callback flow + session manually when hit. See
`askMedicalQuestion`
in [MedicalRagEngine.kt](app/src/main/java/dev/logickoder/ragwithgemma/domain/MedicalRagEngine.kt).

### MediaPipe/LiteRT log spam

MediaPipe floods logcat and trips Android's per-process logd quota (`LOGS OVER PROC QUOTA(300)`),
dropping your own `Log.d`. Raise logcat buffer size in **Developer options → Logger buffer sizes** (
16 M) and reboot, or dump `adb logcat > file` and grep locally.
