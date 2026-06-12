# Project Repo Cleanup Guide

This project is now closer to an interview-ready AI Agent portfolio project, so the repository should separate product assets from local runtime artifacts.

## Keep In Repo

- Core Spring Boot ecommerce Agent code under `assistant-agent-start/src/main/java`.
- React dashboard source under `assistant-agent-ui`.
- Demo resources under `assistant-agent-start/src/main/resources/demo-data` and `experiences`.
- Deployment files under `deploy`.
- Evaluation scripts under `scripts`.
- Model comparison reports such as `model_comparison_open_summary.md` and `model_comparison_codeact_deepseek.md`.
- Product and learning documents: `README.md`, `README_zh.md`, `ROADMAP.md`, `DEVLOG.md`, `TOOL_CATALOG.md`, `SECURITY.md`, `ROBUSTNESS.md`.

## Do Not Commit

- `.env` and `.env.*`: contains local API keys and webhook secrets.
- `.local-tools/`: local JDK/Maven runtime.
- `.m2/`: local Maven cache.
- `logs/` and `assistant-agent-start/logs/`: runtime logs.
- `data/` and `assistant-agent-start/data/`: imported Olist CSVs and local H2 database files.
- `assistant-agent-ui/node_modules/`: frontend dependencies.
- `assistant-agent-start/src/main/resources/static/agent-console/`: generated frontend build output.

## Interview Framing

If asked why these files are ignored, the answer is:

> The repo keeps source code, deployment scripts, demo resources and evaluation assets. Secrets, local databases, imported raw CSVs, generated frontend bundles and build caches stay out of git. This keeps the project reproducible without leaking local runtime state.

## Current CodeAct Benchmark Status

`scripts/codeact_planning_comparison.py` is the stricter model-planning benchmark. It calls the model provider directly and asks the model to produce:

- selected tools
- execution plan
- cause ranking
- action routing
- notification decision

This is different from `scripts/model_comparison.py`, which validates the product workflow through `/api/ecommerce/answer`.
