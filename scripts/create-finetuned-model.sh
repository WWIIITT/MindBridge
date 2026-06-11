#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_OLLAMA_BIN="$(command -v ollama || true)"
if [ -z "$DEFAULT_OLLAMA_BIN" ] && [ -x "/Applications/Ollama.app/Contents/Resources/ollama" ]; then
  DEFAULT_OLLAMA_BIN="/Applications/Ollama.app/Contents/Resources/ollama"
fi

OLLAMA_BIN="${OLLAMA_BIN:-$DEFAULT_OLLAMA_BIN}"
OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://127.0.0.1:11434}"
OLLAMA_MODEL="${OLLAMA_MODEL:-mindbridge-qwen2.5-7b-ft:latest}"
MODELFILE="$ROOT_DIR/models/mindbridge-qwen2.5-7b-ft/Modelfile"

ollama_cli_host() {
  local url="$OLLAMA_BASE_URL"
  url="${url#http://}"
  url="${url#https://}"
  url="${url%%/*}"
  echo "$url"
}

if [ ! -x "$OLLAMA_BIN" ]; then
  echo "Cannot find Ollama."
  echo "Install Ollama in this environment or set OLLAMA_BIN to the ollama executable path."
  exit 1
fi

if [ ! -f "$MODELFILE" ]; then
  echo "Cannot find Modelfile: $MODELFILE"
  exit 1
fi

echo "Creating $OLLAMA_MODEL on $OLLAMA_BASE_URL"
OLLAMA_HOST="$(ollama_cli_host)" "$OLLAMA_BIN" create "$OLLAMA_MODEL" -f "$MODELFILE"

echo "Created $OLLAMA_MODEL"
echo "Verify with: curl $OLLAMA_BASE_URL/api/tags"
