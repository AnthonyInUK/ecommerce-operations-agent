# Assistant Agent

[English](README.md) | [中文](README_zh.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.0-blueviolet.svg)](https://spring.io/projects/spring-ai)
[![GraalVM](https://img.shields.io/badge/GraalVM-Polyglot-red.svg)](https://www.graalvm.org/)

## 🎯 关于这个 Fork（作品集项目）

> 这是一个**作品集 fork**，构建在开源框架 [Spring AI Alibaba — AssistantAgent](https://github.com/spring-ai-alibaba/AssistantAgent) 之上。底层框架（Code-as-Action 引擎，以及 experience / learning / trigger 等模块）来自上游项目；下面列出的是我在这个 fork 中新增的工作。

**我在框架之上做的：**

- **电商运营异常诊断 Agent** —— 一条端到端的工作流（`发现异常 → 归因分析 → 责任分发 → 通知草稿`），而不是通用聊天机器人。包含数仓数据层（`raw / dwd / dim / ads`）、Olist 公开数据集接入，以及多步骤的 GMV 下跌归因图。
- **12 个运营诊断工具**，全部从数仓**实时计算**（无写死答案）：GMV / 订单 / 用户 / 品类 / 漏斗 / 退款 / 区域下钻，外加三个贴合真实运营工作流的工具：
  - **ReleaseImpactTool** —— 围绕发布日期对比前后的订单量、GMV、客单价，判断这次上线是帮了还是害了。
  - **AbTestJudgeTool** —— 从真实行为数据聚合 A/B 实验的转化率/订单/GMV，给出胜者和相对提升幅度。
  - **OrderAbandonmentTool** —— 从订单状态计算弃单率和支付失败率，区分「用户放弃」和「支付环节故障」。
- **把经验与学习系统打磨到生产标准** —— 补齐遗留项（经验检索、Cron 表达式校验），增加提示词注入防护和 OpenTelemetry Span 生命周期管理，并为改动到的模块建立 **127 个测试（0 失败）**。

本 fork 改动规模：**约 168 个文件，+31k / −1.1k 行**，覆盖电商层和产品化加固工作。

## 电商运营分析 Agent 扩展

这个项目在 AssistantAgent 框架上补了一条电商运营分析主线。目标不是再做一个“能聊天的 BI 助手”，而是把运营分析里更关键的链路接起来：

```text
发现异常 -> 拆解维度 -> 形成解释 -> 责任分发 -> 生成通知草稿
```

当前主场景是 GMV 下跌分析：系统通过 trigger 主动巡检 GMV 异常，命中异常后自动跑 root cause，生成关键证据、主要原因排序、责任分发和飞书通知草稿。

### 为什么做这个

普通自然语言问数适合回答“昨天 GMV 多少”，但真实电商运营经常需要连续动作：查 GMV、拆订单量和客单价、看区域/品类、检查用户规模、漏斗和退款，再把结论同步给负责人。

这个项目选择 **Text-to-Code / Code-as-Action** 路线，而不是只做 Text-to-SQL：

- **标准问数**：走 FastIntent + ads 快照，快速回答高频问题。
- **复杂归因**：走 deep path，多 Tool 串联完成 root cause。
- **主动巡检**：走 trigger，不等人问，自动发现异常。
- **评测闭环**：verified cases / runtime bad cases 防止链路退化。

背后是两条互补的路径：高频运营问题走**确定性快路径**（规则路由 + 直接调工具——低延迟、低成本、可复现、无需大模型 key）；开放式问题走**大模型驱动的 Code-as-Action 路径**（`POST /api/ecommerce/agent-run`）——模型自己写 Python 并在 GraalVM 沙箱中执行。下图是一次真实运行（DeepSeek，经 OpenAI 兼容 API）：模型生成函数、沙箱返回 `2550`，整条 LLM 调用由韧性层包装。

![LLM Code-as-Action 真实运行](images/codeact-deepseek.png)

### 核心演示链路

打开页面：

```text
http://localhost:18080/agent-console/index.html
```

点击 **运行 GMV 异常巡检**，页面会调用：

```http
POST /api/ecommerce/triggers/gmv-drop-watch/run-once
```

当前用 `demo-report-date=2018-08-29` 固定到一条可复现的 Olist 异常样本，返回内容包括：

- 路径标签：`trigger -> deep`
- 耗时
- 数据来源
- Tool 调用摘要
- 关键证据
- 主要原因排序
- 责任分发
- 通知草稿

也可以用脚本做端到端烟测，确认“手动 root cause + 主动异常巡检”两条链路都还可用：

```bash
python3 scripts/smoke_ecommerce_demo.py --base-url http://localhost:18080
```

如果当前没有启用 trigger，可先只测问答链路：

```bash
python3 scripts/smoke_ecommerce_demo.py --skip-trigger
```

### 架构图

```mermaid
flowchart LR
    U["用户问题 / 定时 Trigger"] --> R["意图路由"]
    R --> F["FastIntent 标准快路径"]
    R --> D["Root Cause 深路径"]
    R --> T["GMV 异常巡检 Trigger"]
    F --> Tools["电商分析 Tools"]
    D --> Tools
    T --> Tools
    Tools --> W["数仓分层 raw / dwd / dim / ads"]
    W --> O["Olist 公开数据"]
    W --> S["Demo 补齐口径: 用户 / 漏斗 / 退款"]
    Tools --> B["RootCauseAnalysisResult Builder"]
    B --> UI["分析轨迹页面"]
    B --> N["责任分发 + 通知草稿"]
    B --> E["Verified Cases / Runtime Snapshot"]
```

### 混合数据口径说明

当前不会把公开数据包装成“真实生产全量数据”：

- **Olist 公开数据**：用于区域、订单、品类、商品/商家下钻。
- **Demo 补齐口径**：用于用户规模、漏斗、退款，因为 Olist 缺少完整行为流和售后明细。

页面会显式展示数据来源，避免演示时过度承诺。

### 本地运行电商演示

```bash
JAVA_HOME=/path/to/java17 \
mvn -Dmaven.repo.local=.m2/repository -pl assistant-agent-start spring-boot:run \
  -Dspring-boot.run.arguments="\
--server.port=18080 \
--app.datasource.olist-raw-import-dir=/absolute/path/to/data/olist-csv \
--app.datasource.bootstrap-olist-analytics=true \
--app.datasource.prefer-olist-analytics=true \
--app.operations.gmv-drop-watch.enabled=true \
--app.operations.gmv-drop-watch.demo-report-date=2018-08-29"
```

### 部署入口

第一版按单机 Spring Boot 服务部署：

- `assistant-agent-start/src/main/resources/application-prod.yml`
- `deploy/.env.example`
- `deploy/Dockerfile`
- `deploy/docker-compose.yml`
- `deploy/start-prod.sh`
- `deploy/stop-prod.sh`
- `deploy/logs.sh`

复制 `deploy/.env.example` 为 `.env` 并填好密钥后，可用 `bash deploy/start-prod.sh` 启动。

### 可用性与韧性（LLM 调用保护）

Agent 的瓶颈和故障点在 **LLM provider 调用**，而不在本地 CPU——所以这里的“高并发”是指扛住 provider 超时、5xx/429 和成本飙升，而不是追求裸 QPS。每次 LLM 调用都会经过一个手写、零依赖的韧性层（`assistant-agent-core/.../resilience`），按如下顺序编排，且每一层都有明确动机：

```text
限流  →  并发上限  →  熔断  →  重试(退避+抖动)  →  单次超时  →  LLM 调用
(成本/配额) (保护线程)  (快速失败) (补救瞬时错误)   (给每次尝试设预算)
```

- **熔断器** —— CLOSED / OPEN / HALF_OPEN 三态状态机，基于计数滑动窗口的失败率触发，半开态用少量流量探活。provider 不健康时直接快速失败，避免重试风暴把故障放大。
- **指数退避 + 抖动的重试** —— 只对可重试的瞬时错误重试；抖动打散并发重试时间点，避免同步重试风暴。
- **令牌桶限流 + 并发隔板** —— 控制请求速率（成本/配额）与在途并发数。
- **优雅降级** —— 请求被卸载或最终失败时，返回可读的降级文案，而不是抛异常崩溃。

通过 `app.llm.resilience.*` 接入（默认开启；单次超时默认关闭，因为真实 LLM 调用本就耗时较长）。

配套的可复现压测脚本用假模型驱动并发流量（不烧 API），打印真实数字：

```bash
mvn test -pl assistant-agent-core -Dtest=LlmResilienceLoadTest
```

| 场景 | 结果 |
|------|------|
| 健康（provider 全成功） | 约 3,900 QPS，p50 3.8ms / p95 5.0ms / p99 8.9ms —— 包装开销很薄 |
| 降级（provider 严重故障，约 80% 失败） | 熔断器在约 35 次调用后打开，把剩余约 2,960 个请求**瞬间短路到降级文案**——只有约 1% 的流量还在打那个挂掉的 provider，而不是重试风暴；零异常泄漏 |

除 LLM 链路外，项目还内置数仓读缓存（TTL + 缓存命中统计）和经验库语义召回的三级降级，使向量库故障时是“降级”而非“崩溃”。

**可观测性与 SQL 透明（Java 原生、可自托管，不依赖外国 SaaS）：**

- **指标** —— 韧性层计数（`llm_resilience_*`）和 SQL 审计仪表通过 Micrometer 暴露在 `/actuator/prometheus`。[`deploy/observability/`](deploy/observability/README.md) 提供一键起的 Prometheus + Grafana 栈（含预置仪表盘，`docker compose up -d`）。
- **链路追踪** —— Agent 全生命周期（代码生成、代码执行、工具调用、Hook、拦截器）已用 OpenTelemetry 埋点；配置 OTLP exporter 指向 Jaeger/Tempo 即可查看完整请求链路。（想看 LLM 维度的 prompt/token/成本，OTel 数据也能接入 Langfuse —— 开源可自托管的 LangSmith 替代品。）
- **SQL 透明** —— Agent 每次打到数仓的查询（SQL、绑定参数、行数、耗时）都会被记录，可在 `GET /api/ecommerce/sql-audit/recent` 查看，分析师能核验「某个数字到底是哪条 SQL 算出来的」，把 Text-to-Code 从黑盒变成可审计的过程。
- **自检 / 健康探针** —— `POST /api/ecommerce/llm-selftest?times=N` 用 N 次探测调用走一遍同一个韧性执行器：线上是探活金丝雀（provider 故障时指标先于用户告警），演示时则点亮失败/重试/降级/熔断曲线。

![Grafana 可观测仪表盘](images/grafana-observability.png)

*模拟 provider 故障时的实时仪表盘（无有效 API key 的探测）。读图：130 次 LLM 调用中，前 10 次真打到 provider 并失败（各重试一次）；熔断器随即打开，**把剩余 120 次直接短路**（快速失败，而非重试风暴）；但 **130 次全部返回降级兜底**，零异常泄漏。左下角是 SQL 审计在记录真实数仓查询。所有数字都从 `/actuator/prometheus` 实时抓取。*

## ✨ 技术特性

- 🚀 **代码即行动（Code-as-Action）**：Agent 通过生成并执行代码来完成任务，而非仅仅调用预定义工具，可以在代码中灵活组合多个工具，实现复杂流程
- 🔒 **安全沙箱**：AI 生成的代码在 GraalVM 多语言沙箱中安全运行，具备资源隔离能力
- 📊 **多维评估**：通过评估图（Graph）进行多层次意图识别，精准指导 Agent 行为
- 🔄 **Prompt 动态组装**：根据场景及前置评估结果，动态注入运行时上下文、预取经验候选和稳定指导信息到 Prompt 中，灵活处理不同任务
- 🧠 **统一经验体系**：以统一模型管理 COMMON / REACT / TOOL 三类经验，支持与 Skills 模型互相转化，并通过渐进式披露思想提高经验使用效率与效果
- 🗂️ **管理后台**：通过独立管理模块提供经验检索、CRUD、统计，以及 SKILL 预览 / 导入 / 导出等能力，便于统一维护可复用经验与技能资产
- ⚡ **快速响应**：熟悉场景下，跳过 LLM 推理过程，基于经验快速响应

## 📖 简介

**Assistant Agent** 是一个基于 [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba) 构建的企业级智能助手框架，采用代码即行动（Code-as-Action）范式，通过生成和执行代码来编排工具、完成任务。它是一个**能理解、能行动、能学习**的智能助手解决方案。

### Assistant Agent 能帮你做什么？

Assistant Agent 是一个功能完整的智能助手，具备以下核心能力：

- 🔍 **智能问答**：支持多数据源统一检索架构（通过 SPI 可扩展知识库、Web 等数据源），提供准确、可溯源的答案
- 🛠️ **工具调用**：支持 MCP、HTTP API（OpenAPI）等协议，既可在 React 阶段直接调用工具，也可在生成代码中组合多个工具完成复杂业务流程
- ⏰ **主动服务**：支持定时任务、延迟执行、事件回调，让助手主动为你服务
- 📬 **多渠道触达**：内置 IDE 回复，通过 SPI 可扩展钉钉、飞书、企微、Webhook 等渠道
- 🧩 **运维与经验管理**：支持经验管理、租户维度检索，以及经验模型与 SKILL 包之间的双向转换，方便沉淀并复用业务能力

### 为什么选择 Assistant Agent？

| 价值 | 说明 |
|------|------|
| **降低成本** | 7×24 小时智能客服，大幅减少人工客服成本 |
| **快速接入** | 业务平台通过简单配置即可接入，无需大量开发投入 |
| **灵活定制** | 配置知识库、接入企业工具，打造专属业务助手 |
| **持续优化** | 自动学习积累经验，助手越用越聪明 |

### 适用场景

- **智能客服**：接入企业知识库，智能解答用户咨询
- **运维助手**：对接监控、工单系统，自动处理告警、查询状态、执行操作
- **业务助理**：连接 CRM、ERP 等业务系统，辅助员工完成日常工作

> 💡 以上仅为典型场景示例。通过配置知识库和接入工具，Assistant Agent 可适配更多业务场景，欢迎探索。

![QA_comparison.png](images/QA_comparison.png)
![Tool_comparison.png](images/Tool_comparison.png)

### 整体工作原理

以下是 Assistant Agent 处理一个完整请求的端到端流程示例：

![workflow.png](images/workflow.png)

### 项目结构

```
AssistantAgent/
├── assistant-agent-common          # 通用工具、枚举、常量
├── assistant-agent-core            # 核心引擎：GraalVM 执行器、工具注册表
├── assistant-agent-extensions      # 扩展模块：
│   ├── dynamic/               #   - 动态工具（MCP、HTTP API）
│   ├── experience/            #   - 统一经验运行时、经验披露与快速意图配置
│   ├── learning/              #   - 学习提取与存储
│   ├── search/                #   - 统一搜索能力
│   ├── reply/                 #   - 多渠道回复
│   ├── trigger/               #   - 触发器机制
│   └── evaluation/            #   - 评估集成
├── assistant-agent-prompt-builder  # Prompt 动态组装
├── assistant-agent-evaluation      # 评估引擎
├── assistant-agent-management      # 经验管理与 SKILL 转换 API
├── assistant-agent-autoconfigure   # Spring Boot 自动配置
└── assistant-agent-start           # 启动模块
```

### Week E 运行规则

如果你关心这个电商分析 Agent 在“主动日报 / 异常巡检”场景下如何尽量不失控，可以直接看：

- [SECURITY.md](SECURITY.md)
- [ROBUSTNESS.md](ROBUSTNESS.md)
- [DEMO_DAILY_REPORT_CHAIN.md](DEMO_DAILY_REPORT_CHAIN.md)
- [DEMO_ROOT_CAUSE_CHAIN.md](DEMO_ROOT_CAUSE_CHAIN.md)

## 🚀 快速启动

### 前置要求

- Java 17+
- Maven 3.8+
- DashScope API Key

### 1. 克隆并构建

```bash
git clone https://github.com/AnthonyInUK/ecommerce-operations-agent.git
cd ecommerce-operations-agent
mvn clean install -DskipTests
```

### 2. 配置 API Key

```bash
export DASHSCOPE_API_KEY=your-api-key-here
```

### 3. 最小配置

项目已内置默认配置，只需确保 API Key 正确即可。如需自定义，可编辑 `assistant-agent-start/src/main/resources/application.yml`：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-max
```

### 4. 启动应用

```bash
cd assistant-agent-start
mvn spring-boot:run
```

所有扩展模块默认开启并采用合理的配置，无需额外配置即可快速启动。

### 5. 配置知识库（接入业务知识）

> 💡 框架默认提供 Mock 知识库实现用于演示测试。**生产环境需要接入真实知识源**（如向量数据库、Elasticsearch、企业知识库 API 等），以便 Agent 能够检索并回答业务相关问题。

#### 方式一：快速体验（使用内置 Mock 实现）

默认配置已启用知识库搜索，可直接体验：

```yaml
spring:
  ai:
    alibaba:
      codeact:
        extension:
          search:
            enabled: true
            knowledge-search-enabled: true  # 默认开启
```

#### 方式二：接入真实知识库（推荐）

实现 `SearchProvider` SPI 接口，接入你的业务知识源：

```java
package com.example.knowledge;

import com.alibaba.assistant.agent.extension.search.spi.SearchProvider;
import com.alibaba.assistant.agent.extension.search.model.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component  // 添加此注解，Provider 会自动注册
public class MyKnowledgeSearchProvider implements SearchProvider {

    @Override
    public boolean supports(SearchSourceType type) {
        return SearchSourceType.KNOWLEDGE == type;
    }

    @Override
    public List<SearchResultItem> search(SearchRequest request) {
        List<SearchResultItem> results = new ArrayList<>();
        
        // 1. 从你的知识源查询（向量数据库、ES、API 等）
        // 示例：List<Doc> docs = vectorStore.similaritySearch(request.getQuery());
        
        // 2. 转换为 SearchResultItem
        // for (Doc doc : docs) {
        //     SearchResultItem item = new SearchResultItem();
        //     item.setId(doc.getId());
        //     item.setSourceType(SearchSourceType.KNOWLEDGE);
        //     item.setTitle(doc.getTitle());
        //     item.setSnippet(doc.getSummary());
        //     item.setContent(doc.getContent());
        //     item.setScore(doc.getScore());
        //     results.add(item);
        // }
        
        return results;
    }

    @Override
    public String getName() {
        return "MyKnowledgeSearchProvider";
    }
}
```

#### 常见知识源接入示例

| 知识源类型 | 接入方式 |
|-----------|---------|
| **向量数据库**（阿里云 AnalyticDB、Milvus、Pinecone） | 在 `search()` 方法中调用向量相似度检索 API |
| **Elasticsearch** | 使用 ES 客户端执行全文检索或向量检索 |
| **企业知识库 API** | 调用内部知识库 REST API |
| **本地文档** | 读取并索引本地 Markdown/PDF 文件 |

> 📖 更多细节请参考：[知识检索模块文档](assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/search/README.md)

## 🧩 核心模块

各模块的详细文档请访问 [文档站点](https://java2ai.com/agents/assistantagent/quick-start)。

### 核心模块

| 模块 | 说明 | 文档 |
|------|------|------|
| **评估模块** | 通过评估图（Graph）进行多维度意图识别，支持 LLM 和规则引擎 | [快速开始](https://java2ai.com/agents/assistantagent/features/evaluation/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/evaluation/advanced) |
| **Prompt Builder** | 根据评估结果和运行时上下文动态组装 Prompt | [快速开始](https://java2ai.com/agents/assistantagent/features/prompt-builder/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/prompt-builder/advanced) |

### 工具扩展

| 模块 | 说明 | 文档 |
|------|------|------|
| **MCP 工具** | 接入 Model Context Protocol 服务器，复用 MCP 工具生态 | [快速开始](https://java2ai.com/agents/assistantagent/features/mcp/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/mcp/advanced) |
| **动态 HTTP 工具** | 通过 OpenAPI 规范接入 REST API | [快速开始](https://java2ai.com/agents/assistantagent/features/dynamic-http/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/dynamic-http/advanced) |
| **自定义 CodeAct 工具** | 通过 CodeactTool 接口构建自定义工具 | [快速开始](https://java2ai.com/agents/assistantagent/features/custom-codeact-tool/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/custom-codeact-tool/advanced) |

### 智能能力

| 模块 | 说明 | 文档 |
|------|------|------|
| **经验模块** | 基于统一 COMMON / REACT / TOOL 模型管理经验，支持快速意图、与 Skills 模型互转、渐进式披露，以及通过 `search_exp` / `read_exp` 进行运行时检索 | [快速开始](https://java2ai.com/agents/assistantagent/features/experience/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/experience/advanced) |
| **学习模块** | 从 Agent 执行历史中自动提取有价值的 COMMON / REACT / TOOL 经验 | [快速开始](https://java2ai.com/agents/assistantagent/features/learning/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/learning/advanced) |
| **搜索模块** | 多数据源统一检索引擎，支持知识问答 | [快速开始](https://java2ai.com/agents/assistantagent/features/search/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/search/advanced) |

### 交互能力

| 模块 | 说明 | 文档 |
|------|------|------|
| **回复渠道** | 多渠道消息回复，支持渠道路由 | [快速开始](https://java2ai.com/agents/assistantagent/features/reply/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/reply/advanced) |
| **触发器** | 定时任务、延迟执行、事件回调触发 | [快速开始](https://java2ai.com/agents/assistantagent/features/trigger/quickstart) ｜ [高级特性](https://java2ai.com/agents/assistantagent/features/trigger/advanced) |

### 管理能力

| 能力               | 说明                                         | 入口 |
|------------------|--------------------------------------------|------|
| **经验管理 API**     | 提供面向租户的经验列表、搜索、统计与 CRUD 能力                 | [ExperienceManagementController](assistant-agent-management/src/main/java/com/alibaba/assistant/agent/management/controller/ExperienceManagementController.java) |
| **SKILL 转换 API** | 提供与 SKILL 的转换能力，支撑 Skills 模型与统一经验模型之间的双向转换 | [SkillExchangeController](assistant-agent-management/src/main/java/com/alibaba/assistant/agent/management/controller/SkillExchangeController.java) |

### 更多资源

| 资源 | 链接 |
|------|------|
| 快速开始指南 | [AssistantAgent 快速开始](https://java2ai.com/agents/assistantagent/quick-start) |
| 二次开发指南 | [开发指南](https://java2ai.com/agents/assistantagent/secondary-development) |

---

## 📚 参考文档

- [完整配置参考](assistant-agent-start/src/main/resources/application-reference.yml)
- [Spring AI Alibaba 文档](https://github.com/alibaba/spring-ai-alibaba)

## 📄 许可证

本项目采用 Apache License 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 🙏 致谢

- [Spring AI](https://github.com/spring-projects/spring-ai)
- [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba)
- [GraalVM](https://www.graalvm.org/)
