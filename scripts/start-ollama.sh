#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

DEFAULT_OLLAMA_BIN="$(command -v ollama || true)"
if [ -z "$DEFAULT_OLLAMA_BIN" ] && [ -x "/Applications/Ollama.app/Contents/Resources/ollama" ]; then
  DEFAULT_OLLAMA_BIN="/Applications/Ollama.app/Contents/Resources/ollama"
fi
if [ -z "$DEFAULT_OLLAMA_BIN" ] && grep -qi microsoft /proc/version 2>/dev/null; then
  WSL_USER="${USERPROFILE:-}"
  if [ -z "$WSL_USER" ]; then
    WSL_USER="/mnt/c/Users/$(cmd.exe /c "echo %USERNAME%" 2>/dev/null | tr -d '\r')"
  else
    WSL_USER="$(wslpath "$WSL_USER" 2>/dev/null || true)"
  fi
  if [ -n "$WSL_USER" ] && [ -x "$WSL_USER/AppData/Local/Programs/Ollama/ollama.exe" ]; then
    DEFAULT_OLLAMA_BIN="$WSL_USER/AppData/Local/Programs/Ollama/ollama.exe"
  fi
fi
OLLAMA_BIN="${OLLAMA_BIN:-$DEFAULT_OLLAMA_BIN}"
OLLAMA_HOST="${OLLAMA_HOST:-127.0.0.1:11434}"
OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://127.0.0.1:11434}"

if [ ! -x "$OLLAMA_BIN" ]; then
  echo "Cannot find Ollama."
  echo "Install Ollama or set OLLAMA_BIN to the ollama executable path."
  exit 1
fi

if curl -fsS "$OLLAMA_BASE_URL/api/tags" >/dev/null 2>&1; then
  echo "Ollama is already running at $OLLAMA_BASE_URL"
  exit 0
fi

echo "Starting Ollama at $OLLAMA_HOST ..."
exec env OLLAMA_HOST="$OLLAMA_HOST" "$OLLAMA_BIN" serve
