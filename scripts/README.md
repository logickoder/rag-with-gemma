# Dev setup scripts

Dev-only helpers to sideload the Gemma LLM and Medscape drug JSONs onto a
connected Android device. Not run in CI, not shipped with the app.

## Prerequisites

- `adb` on PATH, with a physical ARM64 device authorised for USB debugging.
- `curl`, `unzip`, `bash` ≥ 4.

## Scripts

| Script | Purpose |
|---|---|
| `setup.sh` | Interactive menu wrapping the steps below. |
| `download_embedder.sh` | Downloads MediaPipe MobileBERT embedder into `app/src/main/assets/mobile_bert.tflite` (bundled into the APK at build time). |
| `download_sqlite_vec.sh` | Downloads sqlite-vec Android aarch64 tarball, extracts `vec0.so`, renames to `libsqlite_vec.so`, places at `app/src/main/jniLibs/arm64-v8a/` (bundled into APK). Defaults to `v0.1.9`. |
| `download_llm.sh` | Obtains the Gemma `.litertlm` model. Prompts for a URL or local path; stores it at `artifacts/gemma-4-E2B-it.litertlm`. |
| `push_llm.sh` | `adb push` the model to `/data/local/tmp/`. |
| `sideload_drugs.sh` | Downloads the Medscape drug zip, unzips into `artifacts/medscape_drug_jsons/`, `adb push` to `/data/local/tmp/`. Skips work already done. |

Override URLs / versions:
- Embedder:   `MOBILE_BERT_URL=… scripts/download_embedder.sh`
- sqlite-vec: `SQLITE_VEC_VERSION=v0.1.9 scripts/download_sqlite_vec.sh` or `SQLITE_VEC_URL=…`
- Drugs:      `DRUGS_ZIP_URL=… scripts/sideload_drugs.sh`

## Typical first-time run

```bash
chmod +x scripts/*.sh         # once
./scripts/setup.sh            # pick "All of the above"
```

`artifacts/` is gitignored — nothing here is committed.

## Notes

- The Gemma model is gated by Google; you must obtain a `.litertlm` build
  yourself and provide its URL or local path when prompted.
- `download_llm.sh` is idempotent; pass `y` to overwrite an existing artifact.
- `sideload_drugs.sh` skips the curl and unzip steps if the outputs already
  exist in `artifacts/`.
