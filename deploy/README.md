# Ecommerce Agent Deployment

This is the first production-oriented deployment shape for the ecommerce analysis Agent.

## What It Runs

- Spring Boot app on `SERVER_PORT`, default `18080`
- React operations console: `/agent-console/`
- Health check: `/actuator/health`
- GMV anomaly trigger endpoint: `/api/ecommerce/triggers/gmv-drop-watch/run-once`
- File-based H2 by default, mounted under `/app/data`
- Olist CSV directory mounted read-only under `/app/olist-csv`

## Start

```bash
cp deploy/.env.example .env
# edit .env and set DASHSCOPE_API_KEY
bash deploy/start-prod.sh
```

`DASHSCOPE_API_KEY=replace-me` is only a placeholder. The production startup script
fails fast when the key is missing because the AssistantAgent runtime needs a real
Spring AI `ChatModel` bean.

## Switch To DeepSeek

DeepSeek uses an OpenAI-compatible API, so keep the business Agent code unchanged
and switch only the model provider layer:

```env
MODEL_PROVIDER=deepseek
SPRING_AI_MODEL_CHAT=openai
MODEL_NAME=deepseek-chat
DEEPSEEK_API_KEY=replace-me
DEEPSEEK_BASE_URL=https://api.deepseek.com
```

Use `deepseek-reasoner` only when you want to compare reasoning quality and can
accept slower responses/cost changes. The ecommerce Tool chain, anomaly workflow,
and root cause DTO stay the same.

Then open:

```text
http://localhost:18080/agent-console/index.html
```

## Smoke Test

After the app is up, run:

```bash
python3 scripts/smoke_ecommerce_demo.py --base-url http://localhost:18080
```

The script checks:

- `/api/ecommerce/runtime`
- `/api/ecommerce/answer`
- `/api/ecommerce/triggers/gmv-drop-watch/run-once`
- root cause summary, action routing, notification draft, confidence and data lineage

## Stop

```bash
bash deploy/stop-prod.sh
```

## Logs

```bash
bash deploy/logs.sh
```

## Demo vs Real Daily Mode

For stable demos, keep:

```env
APP_OPERATIONS_GMV_DROP_WATCH_DEMO_REPORT_DATE=2018-08-29
```

For real daily mode, clear it:

```env
APP_OPERATIONS_GMV_DROP_WATCH_DEMO_REPORT_DATE=
```

Then the trigger falls back to `today - 1`.

## Data Source Notes

- Region / order / category analysis prefers Olist analytics when `APP_PREFER_OLIST_ANALYTICS=true`.
- User / funnel / refund analysis still uses demo-completed logic because public Olist lacks full behavior-stream and after-sales detail.
- The UI exposes this lineage so the deployment does not overclaim data coverage.
