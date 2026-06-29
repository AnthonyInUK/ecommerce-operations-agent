#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

docker compose \
  --env-file .env.tencent \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.standalone.yml \
  -f deploy/docker-compose.tencentcloud.yml \
  -f deploy/docker-compose.domain.yml \
  logs -f "${1:-assistant-agent}"
