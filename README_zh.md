# Assistant Agent

[English](README.md) | [中文](README_zh.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.0-blueviolet.svg)](https://spring.io/projects/spring-ai)
[![GraalVM](https://img.shields.io/badge/GraalVM-Polyglot-red.svg)](https://www.graalvm.org/)

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

### 核心演示链路

打开页面：

```text
http://localhost:18080/ecommerce-analysis-trace.html
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
git clone https://github.com/spring-ai-alibaba/AssistantAgent.git
cd AssistantAgent
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

## 🤝 贡献

欢迎贡献！请参阅 [CONTRIBUTING.md](CONTRIBUTING.md) 了解指南。

## 📄 许可证

本项目采用 Apache License 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 🙏 致谢

- [Spring AI](https://github.com/spring-projects/spring-ai)
- [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba)
- [GraalVM](https://www.graalvm.org/)
