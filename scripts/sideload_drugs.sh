#!/usr/bin/env bash
# Download the Medscape drug-content zip, unzip locally, and push the JSON
# directory to /data/local/tmp/medscape_drug_jsons/ on the connected device.

set -euo pipefail

print_help() {
    cat <<EOF
Usage: $(basename "$0") [-h]

Downloads and sideloads the Medscape drug content for dev builds.

  1. curl  -> artifacts/drugs.zip   (skipped if already present)
  2. unzip -> artifacts/medscape_drug_jsons/
  3. adb push artifacts/medscape_drug_jsons/  -> /data/local/tmp/

Override the zip URL via DRUGS_ZIP_URL=... environment variable.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    print_help
    exit 0
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACTS_DIR="$REPO_ROOT/artifacts"
ZIP="$ARTIFACTS_DIR/drugs.zip"
UNZIP_DIR="$ARTIFACTS_DIR/medscape_drug_jsons"
DEVICE_PATH="/data/local/tmp/medscape_drug_jsons"

DRUGS_ZIP_URL="${DRUGS_ZIP_URL:-https://img.staging.medscape.com/pi/iphone/medscapeapp/UE/u-drugcontent-json-template-139.zip}"

mkdir -p "$ARTIFACTS_DIR"

for tool in curl unzip adb; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "✗ $tool not found on PATH." >&2
        exit 1
    fi
done

if [[ -f "$ZIP" ]]; then
    echo "✓ Zip already present: $ZIP ($(du -h "$ZIP" | cut -f1))"
else
    echo "→ Downloading drug content from $DRUGS_ZIP_URL ..."
    curl -L --progress-bar -o "$ZIP" "$DRUGS_ZIP_URL"
fi

if [[ -d "$UNZIP_DIR" ]] && [[ -n "$(ls -A "$UNZIP_DIR" 2>/dev/null)" ]]; then
    echo "✓ Already unzipped at: $UNZIP_DIR"
else
    echo "→ Unzipping..."
    mkdir -p "$UNZIP_DIR"
    unzip -q -o "$ZIP" -d "$UNZIP_DIR"
fi

JSON_COUNT="$(find "$UNZIP_DIR" -maxdepth 1 -name '*.json' | wc -l | tr -d ' ')"
echo "  $JSON_COUNT JSON files ready."

echo "→ Waiting for device..."
adb wait-for-device

echo "→ Pushing to $DEVICE_PATH (this may take a minute)..."
adb push "$UNZIP_DIR" "/data/local/tmp/"

echo "→ Verifying..."
adb shell "ls /data/local/tmp/medscape_drug_jsons | wc -l"

echo "✓ Drugs on device."
