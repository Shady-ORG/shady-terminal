#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN_DIR="${SHADY_BIN_DIR:-$HOME/.local/bin}"

cd "$PROJECT_ROOT"
./gradlew installDist
mkdir -p "$BIN_DIR"
ln -sfn "$PROJECT_ROOT/build/install/shady/bin/shady" "$BIN_DIR/shady"

printf 'Development launcher installed at %s/shady\n' "$BIN_DIR"
