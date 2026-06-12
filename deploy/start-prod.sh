#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_17_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  if [[ -n "$JAVA_17_HOME" ]]; then
    JAVA_HOME="$JAVA_17_HOME"
  fi
  export JAVA_HOME
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if [[ ! -f .env ]]; then
  echo "Missing .env. Create one first:"
  echo "  cp deploy/.env.example .env"
  echo "  edit .env and set DASHSCOPE_API_KEY or DEEPSEEK_API_KEY"
  exit 1
fi

set -a
source .env
set +a

MODEL_PROVIDER="${MODEL_PROVIDER:-dashscope}"
SPRING_AI_MODEL_CHAT="${SPRING_AI_MODEL_CHAT:-$MODEL_PROVIDER}"

case "$MODEL_PROVIDER" in
  dashscope)
    if [[ -z "${DASHSCOPE_API_KEY:-}" || "${DASHSCOPE_API_KEY}" == "replace-me" ]]; then
      echo "Missing valid DASHSCOPE_API_KEY in .env."
      echo "Docker is available, but the Spring AI DashScope ChatModel cannot start without a real API key."
      echo "Edit .env and replace DASHSCOPE_API_KEY=replace-me, then run:"
      echo "  bash deploy/start-prod.sh"
      exit 1
    fi
    ;;
  deepseek)
    if [[ "$SPRING_AI_MODEL_CHAT" != "openai" ]]; then
      echo "DeepSeek uses Spring AI's OpenAI-compatible provider."
      echo "Set SPRING_AI_MODEL_CHAT=openai in .env, then run:"
      echo "  bash deploy/start-prod.sh"
      exit 1
    fi
    if [[ -z "${DEEPSEEK_API_KEY:-}" || "${DEEPSEEK_API_KEY}" == "replace-me" ]]; then
      echo "Missing valid DEEPSEEK_API_KEY in .env."
      echo "Edit .env and set DEEPSEEK_API_KEY, then run:"
      echo "  bash deploy/start-prod.sh"
      exit 1
    fi
    ;;
  *)
    echo "Unsupported MODEL_PROVIDER=$MODEL_PROVIDER."
    echo "Supported values: dashscope, deepseek."
    exit 1
    ;;
esac

if [[ -x ./.local-tools/apache-maven-3.9.11/bin/mvn ]]; then
  MVN=./.local-tools/apache-maven-3.9.11/bin/mvn
else
  MVN=mvn
fi

if [[ -f assistant-agent-ui/package.json ]]; then
  if [[ -f assistant-agent-ui/package-lock.json ]]; then
    (cd assistant-agent-ui && npm ci && npm run build)
  else
    (cd assistant-agent-ui && npm install && npm run build)
  fi
fi

"$MVN" -pl assistant-agent-start -am -DskipTests -Dmaven.repo.local=.m2/repository package
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build

echo "Assistant Agent is starting at http://localhost:${SERVER_PORT:-18080}/agent-console/index.html"
