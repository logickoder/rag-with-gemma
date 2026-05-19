#!/usr/bin/env bash
# Download (or copy) the Gemma .litertlm model into ./artifacts/.
# Accepts a remote URL or a local filesystem path interactively.

set -euo pipefail

print_help() {
    cat <<EOF
Usage: $(basename "$0") [-h]

Interactively obtains a Gemma .litertlm model and stores it at:
  artifacts/gemma-4-E2B-it.litertlm

You can paste either:
  - an HTTPS URL (downloaded with curl), or
  - an absolute local path (copied with cp).

Idempotent: skips the work if the artifact already exists.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    print_help
    exit 0
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACTS_DIR="$REPO_ROOT/artifacts"
TARGET="$ARTIFACTS_DIR/gemma-4-E2B-it.litertlm"

mkdir -p "$ARTIFACTS_DIR"

if [[ -f "$TARGET" ]]; then
    echo "✓ Model already present: $TARGET"
    echo "  Size: $(du -h "$TARGET" | cut -f1)"
    read -r -p "Re-download / overwrite? [y/N] " yn
    if [[ ! "$yn" =~ ^[Yy]$ ]]; then
        exit 0
    fi
    rm -f "$TARGET"
fi

echo "Enter Gemma model source (HTTPS URL or absolute local path):"
read -r -p "> " SOURCE

if [[ -z "$SOURCE" ]]; then
    echo "✗ Empty input. Aborting." >&2
    exit 1
fi

if [[ "$SOURCE" =~ ^https?:// ]]; then
    echo "→ Downloading from URL..."
    curl -L --progress-bar -o "$TARGET" "$SOURCE"
elif [[ -f "$SOURCE" ]]; then
    echo "→ Copying from local path..."
    cp "$SOURCE" "$TARGET"
else
    echo "✗ Not a URL and not an existing file: $SOURCE" >&2
    exit 1
fi

if [[ ! "$TARGET" == *.litertlm ]]; then
    echo "⚠ Warning: target filename does not end in .litertlm — engine may not load it."
fi

echo "✓ Stored at: $TARGET ($(du -h "$TARGET" | cut -f1))"
