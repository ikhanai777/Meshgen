#!/usr/bin/env bash
# Fetches the pinned llama.cpp source (vendored in the llama-cpp-python sdist on PyPI, verified by SHA-256)
# into app/src/main/cpp/llama.cpp. The same source is used for desktop evaluation (tools/llm_eval).
set -euo pipefail
VERSION="0.3.36"
SHA256="832db0699007f1be95a7e41ef12e88926b02ba836461e36a36372db2760c1a2e"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/llama.cpp"
STAMP="$DEST/.meshgen-version"
if [ -f "$STAMP" ] && [ "$(cat "$STAMP")" = "$VERSION" ]; then exit 0; fi
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
URL="$(curl -fsSL "https://pypi.org/pypi/llama-cpp-python/$VERSION/json" | python3 -c 'import json,sys; print([u["url"] for u in json.load(sys.stdin)["urls"] if u["packagetype"]=="sdist"][0])')"
echo "Downloading llama.cpp (llama-cpp-python $VERSION sdist)…"
curl -fsSL -o "$TMP/src.tar.gz" "$URL"
echo "$SHA256  $TMP/src.tar.gz" | sha256sum -c -
tar -xzf "$TMP/src.tar.gz" -C "$TMP"
rm -rf "$DEST"
mv "$TMP/llama_cpp_python-$VERSION/vendor/llama.cpp" "$DEST"
# Drop what the Android build never uses (models/vocabs, docs, examples, tests, tools, python).
for d in models docs media examples tests tools gguf-py scripts benches conversion pocs requirements ci app skills; do rm -rf "$DEST/$d"; done
echo "$VERSION" > "$STAMP"
du -sh "$DEST"
