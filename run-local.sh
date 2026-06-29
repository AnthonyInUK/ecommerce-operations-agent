#!/usr/bin/env bash
# 本地一键启动脚本（学习/调试用）
# 用法：./run-local.sh
# 只从 .env 取 DEEPSEEK_API_KEY，其余全部用 application-local.yml 的本地默认值。

set -e
cd "$(dirname "$0")"

# 1. 用 Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 2. 只取 DeepSeek 相关的 key（不 source 整个 .env，避免容器路径变量污染）
if [ -f .env ]; then
  export DEEPSEEK_API_KEY=$(grep -E '^DEEPSEEK_API_KEY=' .env | cut -d= -f2-)
  export DEEPSEEK_BASE_URL=$(grep -E '^DEEPSEEK_BASE_URL=' .env | cut -d= -f2-)
fi

if [ -z "$DEEPSEEK_API_KEY" ]; then
  echo "❌ 没找到 DEEPSEEK_API_KEY（检查 .env）"
  exit 1
fi
echo "✅ Java: $(java -version 2>&1 | head -1)"
echo "✅ DEEPSEEK_API_KEY 前6位: ${DEEPSEEK_API_KEY:0:6}"

# 3. 用 local profile 启动
mvn spring-boot:run -pl assistant-agent-start -DskipTests -Dspring-boot.run.profiles=local
