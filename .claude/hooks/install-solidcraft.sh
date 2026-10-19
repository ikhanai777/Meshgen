#!/usr/bin/env bash
# SessionStart hook: installs solidcraft-cli so the `solidcraft` MCP server in .mcp.json can start.
# Runs only in Claude Code cloud sessions; locally, install SolidCraft from https://solidcraft.io/download.
set -euo pipefail

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

VERSION="0.16.2"
SHA256="d3b4ecb74fbcdcf077c82d2fcb649e47a0abea77b552112f908089c840b2ecf5"
URL="https://github.com/tangency-labs/solidcraft/releases/download/v${VERSION}/solidcraft-linux-x86_64.deb"
DEST="/usr/local/bin/solidcraft-cli"

if [ -x "$DEST" ] && "$DEST" --version 2>/dev/null | grep -qx "solidcraft-cli ${VERSION}"; then
  exit 0
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

curl -sSfL --retry 4 -o "$work/solidcraft.deb" "$URL"
echo "${SHA256}  $work/solidcraft.deb" | sha256sum -c --quiet -
dpkg-deb -x "$work/solidcraft.deb" "$work/x"
install -m755 "$work/x/usr/bin/solidcraft-cli" "$DEST"
"$DEST" --version
