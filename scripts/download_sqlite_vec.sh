#!/usr/bin/env bash
# Download sqlite-vec Android aarch64 loadable extension and place it under
# app/src/main/jniLibs/arm64-v8a/libsqlite_vec.so (bundled into APK).

set -euo pipefail

print_help() {
    cat <<EOF
Usage: $(basename "$0") [-h]

Downloads the sqlite-vec Android aarch64 loadable extension and places it at:
  app/src/main/jniLibs/arm64-v8a/libsqlite_vec.so

The tarball ships a single 'vec0.so' which is renamed to 'libsqlite_vec.so'
(Android's NDK only loads files named libXXX.so).

Default version: v0.1.9 (stable). v0.1.10-alpha.3 is known to drop the 'k'
hidden column on vec0 tables — do not use.

Overrides:
  SQLITE_VEC_VERSION=v0.1.9
  SQLITE_VEC_URL=<full URL to .tar.gz>
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    print_help
    exit 0
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_DIR="$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"
TARGET="$JNI_DIR/libsqlite_vec.so"

SQLITE_VEC_VERSION="${SQLITE_VEC_VERSION:-v0.1.9}"
DEFAULT_URL="https://github.com/asg017/sqlite-vec/releases/download/${SQLITE_VEC_VERSION}/sqlite-vec-${SQLITE_VEC_VERSION#v}-loadable-android-aarch64.tar.gz"
SQLITE_VEC_URL="${SQLITE_VEC_URL:-$DEFAULT_URL}"

for tool in curl tar; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "✗ $tool not found on PATH." >&2
        exit 1
    fi
done

mkdir -p "$JNI_DIR"

if [[ -f "$TARGET" ]]; then
    echo "✓ Native lib already present: $TARGET ($(du -h "$TARGET" | cut -f1))"
    read -r -p "Re-download / overwrite? [y/N] " yn
    if [[ ! "$yn" =~ ^[Yy]$ ]]; then
        exit 0
    fi
    rm -f "$TARGET"
fi

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

echo "→ Downloading $SQLITE_VEC_URL ..."
curl -L --fail --progress-bar -o "$tmpdir/svec.tar.gz" "$SQLITE_VEC_URL"

echo "→ Extracting..."
tar -xzf "$tmpdir/svec.tar.gz" -C "$tmpdir"

SO_SRC="$(find "$tmpdir" -maxdepth 2 -type f -name "*.so" | head -1)"
if [[ -z "$SO_SRC" ]]; then
    echo "✗ No .so found inside tarball." >&2
    exit 1
fi

mv "$SO_SRC" "$TARGET"

echo "✓ Stored at: $TARGET ($(du -h "$TARGET" | cut -f1))"
echo "  Rebuild the app for the lib to be packaged into the APK."
