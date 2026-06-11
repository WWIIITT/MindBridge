#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

DEFAULT_OLLAMA_BIN="$(command -v ollama || true)"
if [ -z "$DEFAULT_OLLAMA_BIN" ] && [ -x "/Applications/Ollama.app/Contents/Resources/ollama" ]; then
  DEFAULT_OLLAMA_BIN="/Applications/Ollama.app/Contents/Resources/ollama"
fi

OLLAMA_BIN="${OLLAMA_BIN:-$DEFAULT_OLLAMA_BIN}"
OLLAMA_HOST="${OLLAMA_HOST:-127.0.0.1:11434}"
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

ollama_api_available() {
  curl -fsS "$OLLAMA_BASE_URL/api/tags" >/dev/null 2>&1
}

ollama_model_exists() {
  curl -fsS "$OLLAMA_BASE_URL/api/tags" 2>/dev/null \
    | grep -F "\"name\":\"$OLLAMA_MODEL\"" >/dev/null 2>&1
}

if [ -z "${JAVA_HOME:-}" ] && [ -d "$ROOT_DIR/.tools/amazon-corretto-17.jdk/Contents/Home" ]; then
  export JAVA_HOME="$ROOT_DIR/.tools/amazon-corretto-17.jdk/Contents/Home"
fi
if [ -x "$ROOT_DIR/.tools/apache-maven-3.9.9/bin/mvn" ]; then
  DEFAULT_MAVEN_BIN="$ROOT_DIR/.tools/apache-maven-3.9.9/bin/mvn"
else
  DEFAULT_MAVEN_BIN="$(command -v mvn || true)"
fi
MAVEN_BIN="${MAVEN_BIN:-$DEFAULT_MAVEN_BIN}"

if [ ! -x "$OLLAMA_BIN" ]; then
  echo "Cannot find Ollama."
  echo "Install Ollama in this environment or set OLLAMA_BIN to the ollama executable path."
  exit 1
fi

if [ ! -x "$MAVEN_BIN" ]; then
  echo "Cannot find Maven."
  echo "Install Maven or set MAVEN_BIN to the mvn executable path."
  exit 1
fi

mkdir -p data

if ! ollama_api_available; then
  echo "Starting Ollama on $OLLAMA_HOST ..."
  OLLAMA_HOST="$OLLAMA_HOST" "$OLLAMA_BIN" serve > data/ollama.log 2>&1 &

  for _ in $(seq 1 30); do
    if ollama_api_available; then
      break
    fi
    sleep 1
  done
fi

if ! ollama_api_available; then
  echo "Ollama did not start. Check data/ollama.log."
  exit 1
fi

if ! ollama_model_exists; then
  if [ "$OLLAMA_MODEL" = "mindbridge-qwen2.5-7b-ft:latest" ] && [ -f "$MODELFILE" ]; then
    echo "Creating $OLLAMA_MODEL from models/mindbridge-qwen2.5-7b-ft/Modelfile ..."
    OLLAMA_HOST="$(ollama_cli_host)" "$OLLAMA_BIN" create "$OLLAMA_MODEL" -f "$MODELFILE"
  else
    echo "Pulling $OLLAMA_MODEL ..."
    OLLAMA_HOST="$(ollama_cli_host)" "$OLLAMA_BIN" pull "$OLLAMA_MODEL"
  fi
fi

if ! ollama_model_exists; then
  echo "Ollama is reachable at $OLLAMA_BASE_URL, but model $OLLAMA_MODEL is not available there."
  echo "Run: OLLAMA_BASE_URL=\"$OLLAMA_BASE_URL\" ./scripts/create-finetuned-model.sh"
  exit 1
fi

AI_PROVIDER=ollama \
OLLAMA_BASE_URL="$OLLAMA_BASE_URL" \
OLLAMA_MODEL="$OLLAMA_MODEL" \
  "$MAVEN_BIN" -Dmaven.repo.local=.m2/repository spring-boot:run
