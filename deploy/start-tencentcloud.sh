#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f .env.tencent ]]; then
  echo "Missing .env.tencent. Create one first:"
  echo "  cp deploy/.env.tencent.example .env.tencent"
  echo "  edit .env.tencent and set DEEPSEEK_API_KEY or DASHSCOPE_API_KEY"
  exit 1
fi

docker compose \
  --env-file .env.tencent \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.standalone.yml \
  -f deploy/docker-compose.tencentcloud.yml \
  -f deploy/docker-compose.domain.yml \
  up -d --build

SERVER_PORT="$(grep -E '^SERVER_PORT=' .env.tencent | tail -1 | cut -d= -f2-)"
SERVER_PORT="${SERVER_PORT:-18080}"

echo "Ecommerce operations Agent is starting."
echo "Console: https://compliance.anthonyai.cn/agent-console/index.html"
echo "Health:  https://compliance.anthonyai.cn/actuator/health"
echo "Local:   http://127.0.0.1:${SERVER_PORT}/agent-console/index.html"
