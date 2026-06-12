#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_BIN="$ROOT_DIR/.local-tools/apache-maven-3.9.11/bin/mvn"
JDK_HOME="$ROOT_DIR/.local-tools/jdk-17.0.19+10/Contents/Home"
DEFAULT_IMPORT_DIR="assistant-agent-start/src/main/resources/demo-data/olist/raw/sample-csv"

IMPORT_DIR="${1:-$DEFAULT_IMPORT_DIR}"

if [[ ! -d "$ROOT_DIR/$IMPORT_DIR" && ! -d "$IMPORT_DIR" ]]; then
  echo "Olist import directory not found: $IMPORT_DIR" >&2
  echo "Usage: scripts/run_olist_pipeline.sh [relative-or-absolute-import-dir]" >&2
  exit 1
fi

if [[ -d "$ROOT_DIR/$IMPORT_DIR" ]]; then
  IMPORT_DIR="$ROOT_DIR/$IMPORT_DIR"
fi

export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$ROOT_DIR"

echo "==> Using JAVA_HOME=$JAVA_HOME"
echo "==> Importing Olist CSV from: $IMPORT_DIR"
echo "==> Running raw import + dwd/dim/ads pipeline smoke"

"$MAVEN_BIN" -q \
  -Dmaven.repo.local=.m2/repository \
  -pl assistant-agent-start \
  spring-boot:run \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none --app.datasource.olist-raw-import-dir=$IMPORT_DIR --app.datasource.bootstrap-olist-analytics=true"
