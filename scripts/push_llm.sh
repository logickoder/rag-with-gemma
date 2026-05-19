#!/usr/bin/env bash
# Push the Gemma .litertlm model from ./artifacts/ to /data/local/tmp/ on the connected device.

set -euo pipefail

print_help() {
    cat <<EOF
Usage: $(basename "$0") [-h]

Pushes artifacts/gemma-4-E2B-it.litertlm to /data/local/tmp/ on the connected
Android device via adb. Requires the artifact to exist (run download_llm.sh first)
and adb to be on PATH with a device authorised for debugging.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    print_help
    exit 0
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACTS_DIR="$REPO_ROOT/artifacts"
MODEL="$ARTIFACTS_DIR/gemma-4-E2B-it.litertlm"
DEVICE_PATH="/data/local/tmp/gemma-4-E2B-it.litertlm"

if ! command -v adb >/dev/null 2>&1; then
    echo "✗ adb not found on PATH." >&2
    exit 1
fi

if [[ ! -f "$MODEL" ]]; then
    echo "✗ Model missing at $MODEL" >&2
    echo "  Run scripts/download_llm.sh first." >&2
    exit 1
fi

echo "→ Waiting for device..."
adb wait-for-device

echo "→ Pushing $(du -h "$MODEL" | cut -f1) to $DEVICE_PATH ..."
adb push "$MODEL" "$DEVICE_PATH"

echo "→ Verifying..."
adb shell ls -lh "$DEVICE_PATH"

echo "✓ Model on device."
