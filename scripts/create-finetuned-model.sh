#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
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
MODELFILE="$ROOT_DIR/models/mindbridge-qwen2.5-7b-ft/Modelfile"

if [[ "$OLLAMA_BIN" == *.exe ]] && command -v wslpath >/dev/null 2>&1; then
  MODELFILE="$(wslpath -w "$MODELFILE")"
fi

if [ ! -x "$OLLAMA_BIN" ]; then
  echo "Cannot find Ollama."
  echo "Install Ollama or set OLLAMA_BIN to the ollama executable path."
  exit 1
fi

"$OLLAMA_BIN" create mindbridge-qwen2.5-7b-ft:latest -f "$MODELFILE"

echo "Created mindbridge-qwen2.5-7b-ft:latest"
echo "Run MindBridge with: ./scripts/run-dev.sh"
