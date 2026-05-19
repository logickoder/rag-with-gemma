#!/usr/bin/env bash
# Interactive entrypoint for dev sideloading.
# Wraps download_llm.sh, push_llm.sh, sideload_drugs.sh.

set -euo pipefail

print_help() {
    cat <<EOF
Usage: $(basename "$0") [-h]

Interactive setup for testing the Medical RAG Android app on a connected device.
Walks you through:
  - downloading the Gemma .litertlm model into ./artifacts/
  - pushing it to /data/local/tmp/ via adb
  - downloading + unzipping the Medscape drug JSON zip and pushing it too

You can also run the sub-scripts directly:
  scripts/download_llm.sh
  scripts/push_llm.sh
  scripts/sideload_drugs.sh
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    print_help
    exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

run_all() {
    "$SCRIPT_DIR/download_llm.sh"
    "$SCRIPT_DIR/push_llm.sh"
    "$SCRIPT_DIR/sideload_drugs.sh"
}

echo "═══ Medical RAG — Dev Setup ═══"
echo

PS3="
Choose a step (number, or Ctrl-D to quit): "
options=(
    "Download LLM into ./artifacts/"
    "Push LLM to connected device"
    "Sideload Medscape drugs zip (download + unzip + push)"
    "All of the above"
    "Quit"
)

select opt in "${options[@]}"; do
    case "$REPLY" in
        1) "$SCRIPT_DIR/download_llm.sh" ;;
        2) "$SCRIPT_DIR/push_llm.sh" ;;
        3) "$SCRIPT_DIR/sideload_drugs.sh" ;;
        4) run_all ;;
        5) echo "Bye."; exit 0 ;;
        *) echo "Unknown selection: $REPLY"; continue ;;
    esac
    echo
    echo "─── Done. Pick another step or Ctrl-D to quit. ───"
done
