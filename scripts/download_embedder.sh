#!/usr/bin/env bash
# Download MobileBERT text embedder (.tflite) into app/src/main/assets/.
# Bundled into the APK at build time — no adb push needed.

set -euo pipefail

print_help() {
    cat <<EOF
Usage: $(basename "$0") [-h]

Downloads the MediaPipe MobileBERT text embedder and places it at:
  app/src/main/assets/mobile_bert.tflite

This asset ships inside the APK, so this script is only needed once per
clone (or when changing the embedder). Override the source URL via
MOBILE_BERT_URL=... environment variable.

Default source:
  https://storage.googleapis.com/mediapipe-models/text_embedder/mobilebert_embedder/float32/1/mobilebert_embedder.tflite
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    print_help
    exit 0
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS_DIR="$REPO_ROOT/app/src/main/assets"
TARGET="$ASSETS_DIR/mobile_bert.tflite"

MOBILE_BERT_URL="${MOBILE_BERT_URL:-https://storage.googleapis.com/mediapipe-models/text_embedder/mobilebert_embedder/float32/1/mobilebert_embedder.tflite}"

if ! command -v curl >/dev/null 2>&1; then
    echo "✗ curl not found on PATH." >&2
    exit 1
fi

mkdir -p "$ASSETS_DIR"

if [[ -f "$TARGET" ]]; then
    echo "✓ Embedder already present: $TARGET ($(du -h "$TARGET" | cut -f1))"
    read -r -p "Re-download / overwrite? [y/N] " yn
    if [[ ! "$yn" =~ ^[Yy]$ ]]; then
        exit 0
    fi
    rm -f "$TARGET"
fi

echo "→ Downloading MobileBERT from $MOBILE_BERT_URL ..."
curl -L --progress-bar -o "$TARGET" "$MOBILE_BERT_URL"

echo "✓ Stored at: $TARGET ($(du -h "$TARGET" | cut -f1))"
echo "  Rebuild the app for the asset to be packaged into the APK."
