#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_URL="${SHADY_REPO_URL:-}"
VERSION="${SHADY_VERSION:-latest}"
INSTALL_ROOT="${SHADY_INSTALL_ROOT:-$HOME/.local/share/shady}"
BIN_DIR="${SHADY_BIN_DIR:-$HOME/.local/bin}"

if [[ -z "$REPOSITORY_URL" ]]; then
  printf 'SHADY_REPO_URL is required until the final GitHub repository is configured.\n' >&2
  printf 'Example: curl -fsSL <raw-install-url> | SHADY_REPO_URL=https://github.com/org/shady bash\n' >&2
  exit 2
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  printf 'This Shady release supports macOS only.\n' >&2
  exit 2
fi
PLATFORM="macos"

case "$(uname -m)" in
  arm64|aarch64) ARCH="aarch64" ;;
  x86_64|amd64) ARCH="x64" ;;
  *) printf 'Unsupported CPU architecture.\n' >&2; exit 2 ;;
esac

ASSET="shady-${PLATFORM}-${ARCH}.tar.gz"
if [[ "$VERSION" == "latest" ]]; then
  DOWNLOAD_URL="${REPOSITORY_URL%/}/releases/latest/download/$ASSET"
else
  DOWNLOAD_URL="${REPOSITORY_URL%/}/releases/download/$VERSION/$ASSET"
fi

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

printf 'Downloading Shady from %s\n' "$DOWNLOAD_URL"
curl -fsSL "$DOWNLOAD_URL" -o "$TEMP_DIR/shady.tar.gz"
curl -fsSL "$DOWNLOAD_URL.sha256" -o "$TEMP_DIR/shady.tar.gz.sha256"
(
  cd "$TEMP_DIR"
  expected="$(awk '{print $1}' shady.tar.gz.sha256)"
  actual="$(shasum -a 256 shady.tar.gz | awk '{print $1}')"
  if [[ -z "$expected" || "$expected" != "$actual" ]]; then
    printf 'Shady archive checksum verification failed.\n' >&2
    exit 1
  fi
)
tar -xzf "$TEMP_DIR/shady.tar.gz" -C "$TEMP_DIR"

DIST_LAUNCHER="$(find "$TEMP_DIR" -type f -path '*/bin/shady' -print -quit)"
DIST_DIR=""
if [[ -n "$DIST_LAUNCHER" ]]; then
  DIST_DIR="$(dirname "$(dirname "$DIST_LAUNCHER")")"
fi
if [[ -z "$DIST_DIR" || ! -x "$DIST_DIR/bin/shady" ]]; then
  printf 'Downloaded archive does not contain a Shady distribution.\n' >&2
  exit 1
fi

RELEASE_ID="${VERSION}-$(date +%Y%m%d%H%M%S)-$$"
TARGET="$INSTALL_ROOT/releases/$RELEASE_ID"
STAGING="$INSTALL_ROOT/releases/.${RELEASE_ID}.new"
LINK_STAGING="$BIN_DIR/.shady.new.$$"
mkdir -p "$INSTALL_ROOT/releases" "$BIN_DIR"
cp -R "$DIST_DIR" "$STAGING"
mv "$STAGING" "$TARGET"
ln -s "$TARGET/bin/shady" "$LINK_STAGING"
mv -f "$LINK_STAGING" "$BIN_DIR/shady"

printf 'Installed Shady at %s\n' "$TARGET"
printf 'Launcher: %s/shady\n' "$BIN_DIR"
case ":$PATH:" in
  *":$BIN_DIR:"*) ;;
  *) printf 'Add %s to PATH to use shady globally.\n' "$BIN_DIR" ;;
esac
