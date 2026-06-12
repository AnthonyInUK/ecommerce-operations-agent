# AssistantAgent 踩坑日志

> 记录在 [AssistantAgent](https://github.com/alibaba/assistant-agent) 这个多智能体框架上读源码、调试、魔改的全过程。
> 框架基于 Spring AI Alibaba，版本 **0.2.6**，采用 Code-as-Action 范式 + GraalVM 沙箱执行。

---

## 目录

- [框架速览](#框架速览)
- [模块地图](#模块地图)
- [踩坑记录](#踩坑记录)
- [魔改记录](#魔改记录)
- [源码笔记](#源码笔记)
- [待探索](#待探索)

---

## 框架速览

### 它是什么

AssistantAgent 是阿里巴巴开源的企业级多智能体框架，核心思路是 **Code-as-Action**：Agent 不是调用预定义工具返回结果，而是生成可执行代码，在 GraalVM 沙箱里跑，工具调用被包装成 Bridge 注入到沙箱上下文里。

### 和传统 ReAct Agent 的区别

| 维度 | 传统 ReAct | AssistantAgent |
|------|-----------|----------------|
| 行动方式 | 单次 tool call | 生成代码，代码里可组合多个工具 |
| 安全性 | 直接执行 | GraalVM 多语言沙箱隔离 |
| 可复用性 | 无 | 统一经验体系（COMMON/REACT/TOOL） |
| 快速路径 | 无 | FastIntent，熟悉场景跳过 LLM 推理 |

### 整体请求流

```
用户输入
  → 评估图（多层意图识别）
  → Prompt 动态组装（注入经验、上下文）
  → LLM 生成代码
  → GraalVM 沙箱执行（工具通过 Bridge 注入）
  → 结果回复（多渠道）
  → 学习提取（异步沉淀经验）
```

---

## 模块地图

```
AssistantAgent/
├── assistant-agent-common          # 通用工具、枚举、常量
├── assistant-agent-core            # 核心引擎
│   ├── executor/                   #   GraalVM 执行器
│   │   ├── GraalCodeExecutor       #   代码执行入口
│   │   ├── bridge/                 #   工具/状态/日志 Bridge
│   │   └── python/                 #   Python 环境管理
│   ├── context/                    #   Session 级代码上下文
│   ├── tool/                       #   工具注册表
│   └── observation/                #   OpenTelemetry 可观测性
├── assistant-agent-extensions/     # 扩展模块
│   ├── experience/                 #   经验体系
│   │   ├── disclosure/             #     经验披露（渐进式注入 Prompt）
│   │   ├── fastintent/             #     快速意图（跳过 LLM）
│   │   └── tool/                   #     React 阶段直接工具调用
│   ├── learning/                   #   经验学习提取
│   ├── search/                     #   统一检索
│   ├── reply/                      #   多渠道回复（钉钉/飞书等）
│   ├── trigger/                    #   触发器（定时/延迟/事件）
│   └── evaluation/                 #   评估集成
├── assistant-agent-prompt-builder  # Prompt 动态组装
├── assistant-agent-evaluation      # 评估引擎（Graph 结构）
├── assistant-agent-management      # 经验管理 API + Skill 转换
├── assistant-agent-autoconfigure   # Spring Boot 自动配置
└── assistant-agent-start           # 启动入口
```

**读源码建议顺序：**
1. `GraalCodeExecutor` — 理解代码执行核心
2. `bridge/` 三个 Bridge — 理解工具如何注入沙箱
3. `ExperienceDisclosureService` — 理解经验怎么进 Prompt
4. `FastIntentService` — 理解快速路径
5. `PromptBuilder` — 理解 Prompt 组装逻辑

---

## 踩坑记录

> 格式：`### [日期] 坑名` → 现象 → 原因 → 解法/绕过

<!-- ============================================================ -->
### [2026-05-18] 初始化环境

**环境：** Java 17, Maven 3.9.11（仓库自带 `.local-tools/`），Spring Boot 3.4，Spring AI 1.1.0

**首次构建命令：**
```bash
cd AssistantAgent
./.local-tools/apache-maven-3.9.11/bin/mvn clean package -DskipTests
```

**现象：**
> 默认 Maven 构建在当前沙箱环境下失败，原因不是代码报错，而是 Maven 试图写入 `~/.m2` 时被权限拦截。

**原因分析：**
> 当前开发环境不允许直接写用户主目录下的 Maven 仓库，因此默认依赖下载路径不可用。

**解法：**
```bash
./.local-tools/apache-maven-3.9.11/bin/mvn \
  -pl assistant-agent-start -am -DskipTests \
  -Dmaven.repo.local=.m2/repository package
```

**效果：**
> 构建成功，`assistant-agent-start` 及其依赖模块都可以在仓库内本地仓库模式下正常打包。

**教训：**
> 后续所有本地构建、CI 脚本和部署脚本都应该优先显式指定 Maven 本地仓库，避免环境差异导致的非业务失败。

---

### [2026-05-18] 生产骨架起步

**改动范围：**
- `assistant-agent-start/pom.xml`
- `assistant-agent-start/src/main/resources/application-prod.yml`
- `deploy/`

**今天做了什么：**
- 给启动模块补了 `spring-boot-starter-actuator`，为后续健康检查做准备
- 新增 `application-prod.yml`，把生产配置骨架和环境变量占位先立住
- 新增 `deploy/Dockerfile`、`docker-compose.yml`、`start-prod.sh`、`stop-prod.sh`、`logs.sh`、`.env.example`

**为什么先做这件事：**
> 这个项目最终目标不是本地 demo，而是要能在 Linux 服务器上跑起来。部署骨架越早立住，后续所有数据源、Webhook、日志和健康检查的设计就越不容易返工。

**后续要验证：**
- Actuator 健康检查端点是否满足 Docker healthcheck 需求
- Docker 镜像构建是否能拿到正确 JAR 路径
- `application-prod.yml` 后面还要接入真正的电商数据源和飞书配置

---

### [2026-05-18] 业务资产骨架落地

**改动范围：**
- `assistant-agent-start/src/main/resources/experiences/`
- `.gitignore`

**今天做了什么：**
- 新建了 `metric_dictionary.yaml`、`semantic_model.yaml`、`verified_cases.yaml`
- 同步补了 `bad_cases.yaml`、`report_templates.yaml`、`evaluation_summary.md`
- 给仓库补了 `.m2/` 和 `logs/` 忽略规则，减少本地产物污染

**为什么先做这件事：**
> 这个项目后面不只是拼 Java 类和 Tool，还要把“业务世界怎么定义”“哪些问题已经验证过”“哪些问题答错过”“系统怎么证明自己在变强”都固化下来。先把这些资产文件落地，后面的 Prompt、评测、FastIntent、面试材料才有统一依托。

**当前意义：**
- `metric_dictionary.yaml`：把业务团队常说的 GMV、DAU、退款率这些词先固定下来
- `semantic_model.yaml`：把大盘、增长、退款、活动四个分析空间先分开
- `verified_cases.yaml`：把高频标准题和复杂归因题先建成公共基线
- `bad_cases.yaml`：把最典型的失败问题先记录下来，后面才能回流修系统
- `evaluation_summary.md`：后续所有命中率、响应时间、失败分类统一收口

**后续要验证：**
- 这些资产文件如何接进 FastIntent / Experience / Prompt 注入
- 是否需要补文件加载器，避免后续继续写死在 Java 代码里
- bad case 和 verified case 能否形成一条最小自演进闭环

---

### [2026-05-18] 从 demo 经验切到电商启动主链

**改动范围：**
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/`
- `assistant-agent-start/src/main/resources/demo-data/`
- `assistant-agent-start/src/main/resources/application*.yml`

**今天做了什么：**
- 让旧的 `DemoExperienceConfig` 只在显式打开开关时才生效
- 新增 `EcommerceExperienceBootstrapConfig`，启动时把电商 YAML/MD 资产真正写进 ExperienceRepository
- 新增 `AppDataSourceProperties`、`AppDataSourceConfig`、`DemoWarehouseDataInitializer`、`JdbcWarehouseQueryService`
- 新建 `demo-data/init_schema.sql` 和 `load_data.sql`，让 H2 内存库启动后直接具备 `dwd_* / ads_*` 这类最小分析底座

**为什么这样做：**
> 如果只把业务资产放在 resources 里，它们还只是“文档”；只有真正接进启动逻辑，Agent 才会把这些东西当成自己的业务世界。数据侧也是一样，先把一条可启动、可查数的 JDBC 主链立住，后面的 Tool、归因和评测才有真实依托。

**现象 → 根因 → 解法：**
- 现象：原项目默认只会加载“你是谁”“魔力红是什么”这种 demo 经验
- 根因：启动类里没有面向业务场景的资源装载器，经验初始化是纯手写 Java 样例
- 解法：把电商资产文件转成启动时可写入仓库的 Experience，并同步接入最小数据底座

**影响：**
- 项目从“框架示例应用”开始变成“电商分析 Agent 原型”
- 经验侧、数据侧、部署侧三条主链第一次真正汇到一起

**面试可讲点：**
> 我没有停留在 roadmap 和 YAML 设计，而是把业务资产和 demo 数仓一起接进启动主链，让 Agent 启动后真的拥有一套电商分析世界，而不是只有通用样例。

---

### [2026-05-18] 启动烟测暴露 JDK 与 SQL 编码问题

**现象 1：**
> `mvn package` 在真正重新编译 `assistant-agent-start` 时失败，报 `invalid target release: 17`。

**根因：**
> 当前 shell 默认拿到的是 JDK 11，而项目父 POM 明确要求 Java 17。之前能成功只是因为很多模块没有重新编译。

**解法：**
```bash
export JAVA_HOME=/Users/anthony/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

**现象 2：**
> 无 Web 模式启动时，Experience 已成功加载，但 `demo-data/load_data.sql` 导入到 H2 时，中文区域值被读成 `????`，进而导致主键冲突。

**根因：**
> `ResourceDatabasePopulator` 没显式指定脚本编码，导致 SQL 初始化过程没有稳定按 UTF-8 读取中文内容。

**解法：**
```java
populator.setSqlScriptEncoding("UTF-8");
```

**影响：**
- 本地开发必须显式使用 JDK 17
- demo 数仓初始化必须把 UTF-8 当成硬要求，否则中文业务维度会污染数据

**面试可讲点：**
> 我在把业务 demo 接成真正启动主链后，主动跑了启动烟测，发现的不是“空想中的问题”，而是 JDK 版本和 SQL 编码这类真实交付问题，并把它们沉淀进了开发约束里。

---

### [2026-05-18] 从“资源接入”走到“真实查询主链”

**改动范围：**
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/validator/ExperienceTestValidator.java`
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/JdbcWarehouseQueryService.java`
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/tool/`

**今天做了什么：**
- 把 `ExperienceTestValidator` 从旧 demo 校验切成电商校验
- 新增 `GmvQueryTool`、`RegionPerformanceQueryTool`、`CategoryRankTool`
- 让 `JdbcWarehouseQueryService` 从底层 JDBC 包装升级成业务化查询服务

**为什么要这样拆：**
> 如果直接让 Tool 自己拼所有 SQL，短期虽然能跑，但后面会越来越像“每个分析动作都自己造一套口径”。先把数据访问层抬成业务化查询服务，再把 Tool 定位成“业务分析动作”，结构会更像真实内部分析系统。

**这次 validator 真正开始验证什么：**
- 电商 Experience 是否按预期加载了 5 条
- COMMON / REACT 两类经验是否各自命中正确标题
- `verified_cases.yaml` 是否真的作为引用挂到 `exp-ecom-verified-cases`
- demo warehouse 是否至少具备 `ads_daily_core_metrics / ads_region_daily / ads_category_daily` 这条最小查询主链

**这次 Tool 真正打通了什么业务链：**
- `GmvQueryTool`：适合日报和早盘看数
- `RegionPerformanceQueryTool`：适合区域对比和大区复盘
- `CategoryRankTool`：适合品类排行和结构拆解

**验证结果：**
- `mvn compile` 成功
- 无 Web 启动烟测成功
- 日志确认 3 个自定义 Tool 被 Spring 发现、被注册到 `CodeactToolRegistry`、并进入 Agent 工具池
- 日志确认 validator 已通过电商 Experience、verified cases 和数仓主链校验

**面试可讲点：**
> 我没有把“接了数据”停留在 DataSource 层，而是继续往前走了一步：先把经验校验改成业务资产校验，再把 GMV/区域/品类三类真实分析动作接到数仓查询主链上，让 Agent 真正开始具备“看数和拆结构”的能力。

---

### [2026-05-18] 从“有 Tool”走到“有最小问答链”

**改动范围：**
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/tool/`
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/service/EcommerceQuestionAnswerService.java`
- `assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/validator/EcommerceQuestionChainValidator.java`

**今天做了什么：**
- 第二批分析 Tool：补了 `RefundAnalysisTool` 和 `FunnelAnalysisTool`
- `CategoryRankTool` 新增按区域拆解能力
- 新建 `EcommerceQuestionAnswerService`，把标准问题路由成真实 Tool 链
- 新建 `EcommerceQuestionChainValidator`，启动时直接验证 4 类问题

**为什么要先做“最小问答链”：**
> 现在还不是完整的通用 Agent 规划阶段，如果直接追求“任意问题都能问”，很容易又回到空泛 demo。先把 4 类高频问题做成可验证链路，能证明系统已经具备“从业务问题到业务回答”的闭环，再往上迭代就更稳。

**这条最小问答链现在覆盖什么：**
- “昨天 GMV 多少？”
- “某天华东和华南哪个区域表现更差？”
- “某天品类排行怎么看？”
- “某天华东 GMV 为什么跌了？”

**深路径这次真正怎么做的：**
- 先看区域当天 vs 前一天 GMV
- 再看该区域的品类结构变化
- 再看漏斗是否掉转化
- 最后看退款是否抬升
- 再把这些事实组织成一句面向业务的解释

**验证结果：**
- 启动烟测日志已经能看到 4 个问题都返回 `success=true`
- 深路径日志里能看到完整 Tool 链：
  `RegionPerformanceQueryTool -> CategoryRankTool -> FunnelAnalysisTool -> RefundAnalysisTool`

**面试可讲点：**
> 我没有只停留在“工具已经注册进 Agent 里”，而是进一步做了一条最小问答链，证明系统已经能把标准业务问题路由成真实工具链，并输出结构化业务结论。这是从“有能力模块”走向“有产品闭环”的关键一步。

---

<!-- ============================================================ -->
<!-- 复制下面的模板来添加新的踩坑记录 -->

<!--
### [YYYY-MM-DD] 坑的标题

**模块：** `assistant-agent-xxx`
**关键类：** `com.alibaba.assistant.agent.xxx.Xxx`

**现象：**
> 描述看到了什么错误或异常行为

**原因分析：**
> 读了哪个源码，发现了什么

```java
// 关键代码片段
```

**解法：**
> 怎么修的，或者绕过方案

**教训：**
> 一句话总结

---
-->

---

## 魔改记录

> 格式：改了什么 → 为什么 → 效果

<!-- ============================================================ -->
### [2026-05-18] 魔改起点

<!-- 在这里记录第一个改动 -->

---

### [2026-05-18] 从最小问答链收回框架 FastIntent 主链

**改动文件：**
- `/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/EcommerceExperienceBootstrapConfig.java`
- `/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/validator/ExperienceTestValidator.java`
- `/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/resources/application.yml`
- `/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/resources/application-reference.yml`

**改动类型：** `feature`

**问题现象：**
最小问答链已经能回答“昨天 GMV 多少”“区域对比”“品类排行”“华东 GMV 为什么跌了”，
但那条链还主要靠 `EcommerceQuestionAnswerService` 做手写路由。这样虽然能证明业务主链是通的，
却不符合项目的长期方向：能用框架 `Experience / FastIntent / REACT plan` 解决的，不应该再长一套平行逻辑。

**为什么这样改：**
- 让标准高频问题优先命中框架快路径，而不是继续扩 `if/else`。
- 让 `gmv_root_cause` 这种复杂问题也能先沉淀成标准分析套路，再由框架触发固定 tool chain。
- 这样后面讲项目时，重点就不再是“我手写了一个问答服务”，而是“我把业务分析套路沉淀成框架内可复用经验”。

**这次具体做了什么：**
- 新增 4 条可被 FastIntent 直接命中的电商 REACT Experience：
  - 昨天 GMV 快速看数
  - 区域对比快路径
  - 品类排行快路径
  - 华东 GMV 下跌归因快深结合
- 把 `GmvQueryTool / RegionPerformanceQueryTool / CategoryRankTool / FunnelAnalysisTool / RefundAnalysisTool`
  加进 `fast-intent-allowed-tools`。
- 在启动校验里增加 FastIntent 命中验证，确认标准问题会命中正确 Experience。
- 额外锁住 `gmv_root_cause` 的深路径 plan，确保它始终是：
  `区域 -> 区域对比日 -> 品类 -> 品类对比日 -> 漏斗 -> 漏斗对比日 -> 退款`

**效果：**
- 现在“昨天 GMV 多少”“华东和华南哪个区域表现更差”“品类排行怎么看”“华东 GMV 为什么跌了”
  都已经能由框架 FastIntent 正确命中。
- `gmv_root_cause` 已经从“一次性成功回答”升级成“框架内可复用的标准归因套路”。
- 这一步让项目更贴 roadmap 里的方向：先用框架能力沉淀业务经验，再把手写服务退回到验证和兜底位置。

**面试可讲点：**
不是所有能跑的业务链都应该长期留在自定义服务里。我这里专门做了一次“收口”：
先用手写问答链证明业务价值，再把成熟路径沉淀回 `Experience + FastIntent + REACT tool plan`，
这样系统会更像一个能持续积累组织经验的 Agent，而不是一堆临时逻辑的集合。

---

<!-- ============================================================ -->
<!-- 复制下面的模板来添加新的魔改记录 -->

<!--
### [YYYY-MM-DD] 改动标题

**改动文件：** `path/to/File.java`
**改动类型：** `bugfix` / `feature` / `性能优化` / `行为调整`

**改动前：**
```java
// 原代码
```

**改动后：**
```java
// 新代码
```

**效果：**
> 改完之后有什么变化

---
-->

---

## 源码笔记

### GraalVM 沙箱执行机制

**对应日期：** Day 2

**目标文件：**
- `/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-core/src/main/java/com/alibaba/assistant/agent/core/executor/GraalCodeExecutor.java`
- `/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-core/src/main/java/com/alibaba/assistant/agent/core/context/SessionCodeManager.java`
- `/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-core/src/main/java/com/alibaba/assistant/agent/core/executor/python/PythonEnvironmentManager.java`
- `/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-autoconfigure/src/main/java/com/alibaba/assistant/agent/autoconfigure/tools/ExecuteCodeTool.java`

**验证实验：**
- `/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-core/src/test/java/com/alibaba/assistant/agent/core/executor/GraalCodeExecutorDay2ExperimentTest.java`

**验证命令：**
```bash
export JAVA_HOME=/Users/anthony/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./.local-tools/apache-maven-3.9.11/bin/mvn \
  -pl assistant-agent-core \
  -Dtest=GraalCodeExecutorDay2ExperimentTest \
  -Dmaven.repo.local=.m2/repository test
```

**验证结果：**
- 6 个实验全部通过。
- 第一次运行因为 Maven Surefire JUnit 依赖未下载，受限网络下失败；联网下载依赖后通过。
- 实验不是为了覆盖业务逻辑，而是为了把 Day 2 的源码理解变成可复现证据。

#### 问题 1：LLM 生成的代码是怎么传入沙箱的？

**我的初始猜想：**
LLM 生成的代码可能会被直接传给 GraalVM 执行。

**源码证据：**
- `GraalCodeExecutor.execute()` 先通过 `SessionCodeManager.getFunction(...)` 找到要执行的函数。
- 再通过 `SessionCodeManager.getMergedFunctions(...)` 合并 session 代码和全局 `CodeContext` 代码。
- 然后拼接 imports、预注入变量、历史函数和 `_result = xxx(...)` 调用语句。
- 最后在 `executeWithGraal()` 里执行 `context.eval("python", code)`。

**实验验证：**
- `generatedFunctionShouldExecuteInsideGraalSandbox()` 注册 `test_add(a, b)`，执行后返回 `5`。
- `sessionCodeShouldOverrideGlobalCodeBeforeSandboxExecution()` 同名函数下 session 版本覆盖 global 版本，返回 `session-version`。

**我的结论：**
代码不是“LLM 原文直接进沙箱”，而是先登记成函数，再由框架包装成完整 Python 脚本执行。
session 代码优先级高于全局代码，这也是多轮分析能复用历史函数的基础。

**对项目影响：**
我们的电商 Agent 可以利用 session 机制承接连续分析，但也要防止历史函数污染后续分析。
后面做多轮问题时，需要明确哪些函数应该进入 session，哪些只适合单次执行。

#### 问题 2：执行超时 / 内存限制是否真的生效？

**我的初始猜想：**
既然 Builder 有 `executionTimeout(30000)`，执行器应该会强制中断超时代码。

**源码证据：**
- `CodeactAgentBuilder` 有 `executionTimeoutMs = 30000`。
- `CodeactAgentConfig` 里配置了 `.executionTimeout(30000)`。
- `GraalCodeExecutor` 保存了 `executionTimeoutMs`，并在初始化日志里打印。
- 但 `executeWithGraal()` 中没有看到 `Future.get(timeout)`、`ExecutorService`、线程中断或 Graal `ResourceLimits`。
- 没看到显式内存限制、heap quota 或 CPU quota 配置。

**实验验证：**
- `timeoutAndMemoryLimitShouldBeTreatedAsFrameworkBoundaryInCurrentExecutor()` 做源码审计：
  - 确认存在 `executionTimeoutMs` 字段和赋值。
  - 确认当前执行器没有 `Future`、`ExecutorService`、`ResourceLimits`、`memoryLimit` 等强限制逻辑。

**我的结论：**
当前版本是“有 timeout 配置入口，但执行器没有真正强制执行超时”。
内存限制也没有看到明确实现。这是框架边界。

**对项目影响：**
服务器部署和深路径归因时，不能只相信框架 timeout 字段。
后面做鲁棒性时要补外层超时、工具调用限制、深路径步数限制，必要时把这作为安全/鲁棒性设计亮点讲清楚。

#### 问题 3：Python 环境和 JS 环境是怎么切换的？

**我的初始猜想：**
框架有 `Language.PYTHON / JAVASCRIPT / JAVA`，可能会按语言动态切换 GraalVM runtime。

**源码证据：**
- `Language` enum 确实定义了 `PYTHON`、`JAVASCRIPT`、`JAVA`。
- `CodeactAgentBuilder` 有 `.language(...)`。
- 但 `GraalCodeExecutor.executeWithGraal()` 写死了：
  - `Context.newBuilder("python")`
  - `context.getBindings("python")`
  - `context.eval("python", code)`
- 当前只看到 `PythonEnvironmentManager`，没有主链使用的 JavaScript environment manager。

**实验验证：**
- `executorImplementationShouldStillBePythonFirstRatherThanDynamicJsSwitching()` 做源码审计：
  - 确认执行器包含 `Context.newBuilder("python")` 和 `context.eval("python", code)`。
  - 确认没有按 `codeContext.getLanguage()` 动态构造 GraalVM context。

**我的结论：**
框架抽象层预留了多语言设计，但当前主执行链实际是 Python-first，不是完整的 Python/JS 动态切换。

**对项目影响：**
我们项目现在坚持 Python Text2Code 是合理的，不应该在当前阶段宣传“多语言代码执行”。
如果面试官追问 JS/多语言，我应该诚实说：框架有抽象，但当前主链还没完全落地。

#### 问题 4：执行结果是什么格式返回给上层？

**我的初始猜想：**
Python 返回的 dict/list 可能会原样作为结构化对象交给上层。

**源码证据：**
- `context.eval("python", code)` 返回 Graal `Value`。
- `GraalCodeExecutor` 会把 `Value` 转成 Java 类型：
  - number -> `int/long/double/BigDecimal`
  - bool -> `Boolean`
  - string -> `String`
  - list -> `List<Object>`
  - dict -> `Map<String, Object>`
- 但写进 `ExecutionRecord` 时会执行 `String.valueOf(resultWrapper.getResult())`。
- `ExecuteCodeTool.Response` 最终返回 `success/result/error/callTrace/replyToUserTrace/durationMs`。

**实验验证：**
- `executionResultShouldBeReturnedAsExecutionRecordString()` 返回一个包含 `gmv`、`regions`、`success` 的 Python dict。
- 测试确认执行成功，`ExecutionRecord.result` 中能看到 `gmv` 和 `1390`。

**我的结论：**
执行器中间能识别 Python dict/list，但上层 `ExecutionRecord.result` 主要还是字符串化结果。
真正可观测的结构化信息更多依赖 `callTrace`、`replyToUserTrace` 和 Tool 返回 JSON。

**对项目影响：**
电商分析 Tool 的返回值必须尽量稳定、JSON 化、字段明确。
不要指望任意 Python 返回对象都能被上层稳定结构化消费。

#### 问题 5：执行报错时，错误信息怎么处理？会自动重试吗？

**我的初始猜想：**
执行失败后，Executor 可能会自动把错误反馈给 LLM 并重试一轮。

**源码证据：**
- `GraalCodeExecutor.execute()` 捕获异常后：
  - `record.setSuccess(false)`
  - `record.setErrorMessage(e.getMessage())`
  - `record.setStackTrace(getStackTrace(e))`
  - `logCodeContextAroundError(...)` 打印报错行附近代码
- `ExecuteCodeTool.apply()` 看到失败记录后，返回 `Response(false, null, error, ...)`。
- 没看到 `GraalCodeExecutor` 或 `ExecuteCodeTool` 内部自动 retry。

**实验验证：**
- `executionErrorShouldBePackagedIntoExecutionRecordWithoutExecutorRetry()` 执行 `return 1 / 0`。
- 测试确认 `ExecutionRecord.success=false`，有 `errorMessage` 和 `stackTrace`，栈里能定位到 `broken_analysis`。
- 日志也打印了完整拼接代码和报错行附近上下文。

**我的结论：**
错误会被结构化包装并返回上层，但执行器本身不自动重试。
是否让 LLM 修复代码，要由外层 Agent 推理流程决定。

**对项目影响：**
复杂归因链要做降级和 bad case 回流，不能假设执行器会自动自愈。
后面可以把“失败记录 -> bad case -> 修 Experience/semantic model”作为轻量自演进闭环。

#### Day 2 面试可讲点

> 我没有只看文档判断框架能力，而是针对 `GraalCodeExecutor` 做了 6 个最小实验。
> 结果发现它的代码包装、session 合并、结果回传、错误包装这几块比较完整；
> 但 timeout/内存限制和多语言切换还没有完全落到执行器主链。
> 所以后面我在项目里选择 Python Text2Code 作为主路径，同时把超时、降级和安全边界作为单独工程能力补上。

### 经验披露（Experience Disclosure）

**目标文件：**
- [ExperiencePrefetchHook.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/experience/disclosure/ExperiencePrefetchHook.java)
- [ExperienceDisclosurePromptContributor.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/experience/disclosure/ExperienceDisclosurePromptContributor.java)
- [ExperienceDisclosureService.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/experience/disclosure/ExperienceDisclosureService.java)
- [PromptContributorModelHook.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/prompt/PromptContributorModelHook.java)
- [CodeactStateKeys.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-common/src/main/java/com/alibaba/assistant/agent/common/constant/CodeactStateKeys.java)

#### 先说业务问题

我这次真正想弄懂的不是“Experience 有哪些类”，而是：

**经验库里的业务经验，最后为什么会出现在模型眼前，而且不是一股脑全塞进去。**

对我的电商项目，这个问题特别重要，因为：

- `gmv_root_cause` 这种经验如果每次都从零想，复杂归因就不稳定
- 如果所有经验全文都塞进 prompt，token 会炸，模型注意力也会分散

所以框架必须解决两个矛盾：

1. 模型要提前知道“有哪些相关经验可用”
2. 但不是所有经验都值得直接全文注入

#### 我的初始理解

一开始我以为 Experience 要么是：

- 每次统一把全文塞进 prompt
- 要么完全不进 prompt，只等模型自己调用 `search_exp / read_exp`

后来顺着链路看下来，发现框架走的是一个更像产品系统的设计：

**先预取候选经验 -> 放进 state -> 再按披露策略选择性注入 prompt。**

#### 我现在的理解

1. `ExperiencePrefetchHook` 更像“先备料”
   - 在模型主推理前，先把当前问题附近可能相关的经验找出来
   - 这些不是最终 prompt，而是“候选经验”

2. 候选经验先放进 `state`
   - 这样后面的 PromptContributor 不需要重新检索一次
   - `state` 在这里的作用，不是存最终答案，而是存“经验流水线的中间结果”

3. `ExperienceDisclosurePromptContributor` 再从 `state` 里拿候选
   - 然后按经验类型、披露策略、当前上下文决定：
     - 哪些可以直接注入
     - 哪些只保留候选信息
     - 哪些需要模型后续再 `read_exp`

4. `DisclosureStrategy` 是关键分水岭
   - `DIRECT`
     - 更适合短、确定、高相关的经验
     - 可以直接让模型先看到
   - `PROGRESSIVE`
     - 更适合长内容、候选型、需要按需展开的经验
     - 先告诉模型“这里有这条经验”，不一定全文注入

#### 一个月后只看这几句也能捡起来

- Experience 不是“全塞”也不是“完全不塞”
- 它是：**先检索候选，放进 state，再按披露策略注入**
- `DIRECT` 更像“先直接给模型看”
- `PROGRESSIVE` 更像“先挂候选，真需要再展开”

#### 对项目的影响

这直接解释了我为什么要把：

- `metric_dictionary.yaml`
- `semantic_model.yaml`
- `verified_cases.yaml`
- `report_templates.yaml`

这些东西接进 Experience 主链，而不是自己写一套平行知识库。

对我的电商项目来说：

- 高频标准问题需要一眼命中的短经验
- 复杂归因需要候选经验 + 按需展开

也就是说，Experience 更偏：

**“模型先看到什么经验知识”**

#### 面试可讲点

> Experience 不是简单的知识注入，而是一条分阶段的经验披露链：先预取候选经验放进 state，再由 PromptContributor 按 `DIRECT / PROGRESSIVE` 策略选择性注入 prompt。  
> 这样既避免把所有经验全文塞进模型，又能让高价值经验在正确时机进入推理链。

### Bridge 三件套与 Tool 注入链

**目标文件：**
- [AgentToolBridge.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-core/src/main/java/com/alibaba/assistant/agent/core/executor/bridge/AgentToolBridge.java)
- [StateBridge.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-core/src/main/java/com/alibaba/assistant/agent/core/executor/bridge/StateBridge.java)
- [LoggerBridge.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-core/src/main/java/com/alibaba/assistant/agent/core/executor/bridge/LoggerBridge.java)
- [GraalCodeExecutor.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-core/src/main/java/com/alibaba/assistant/agent/core/executor/GraalCodeExecutor.java)
- [ToolRegistryBridge.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-core/src/main/java/com/alibaba/assistant/agent/core/tool/ToolRegistryBridge.java)

#### 先说业务问题

我这次真正要读懂的不是“Bridge 有哪几个类”，而是：

1. 为什么 Python 代码里能直接写 `reply_tools.send_success_message(...)` 或 `GmvQueryTool.query_daily_gmv(...)`
2. 这些调用最终是怎么回到 Java Tool 的
3. 调用记录为什么最后能变成 `callTrace / replyToUserTrace`

也就是说，这一轮要弄懂的是：

**业务 Tool 是怎么被“伪装成 Python 可调用对象”注入沙箱的。**

#### 我的初始猜想

一开始我以为 `AgentToolBridge` 就是全部核心，Python 代码直接统一调用：

`agent_tools.call("tool_name", args)`

后来对着源码看下来，发现它只代表“最早期/最简单”的桥接方式；现在主链真正服务 CodeactTool 的，是另一条更完整的注入链：

- `StateBridge / LoggerBridge` 负责把状态和日志能力暴露给 Python
- `ToolRegistryBridge` 负责把真正的 CodeactTool 调用、返回值观测、callTrace 记录串起来
- `generatePythonToolCode()` 负责把 Java Tool 变成 Python 方法壳子

#### 源码证据

1. **Bridge 三件套先注入 Python bindings**
   - `GraalCodeExecutor.executeWithGraal()` 会先做：
     - `context.getBindings("python").putMember("agent_tools", toolBridge)`
     - `context.getBindings("python").putMember("agent_state", stateBridge)`
     - `context.getBindings("python").putMember("logger", loggerBridge)`
   - 这说明 Python 沙箱里天然能访问：
     - `agent_tools`
     - `agent_state`
     - `logger`

2. **真正的 CodeactTool 主链不是直接走 AgentToolBridge，而是走 ToolRegistryBridge**
   - `injectCodeactTools()` 会创建 `ToolRegistryBridge`
   - 再注入：
     - `context.getBindings("python").putMember("__tool_registry__", bridge)`
   - 然后按语言筛出当前可用的 `CodeactTool`
   - 按 `targetClassName` 分组
   - 动态生成一段 Python 工具代码并 `context.eval("python", pythonCode)`

3. **动态生成的 Python 方法，本质上是在替 Java Tool 包一层 Python 外壳**
   - `generatePythonMethod()` 里会生成这样的逻辑：
     - 先把 Python 参数整理成 `args`
     - `json.dumps(args)`
     - 调 `__tool_registry__.callTool(toolName, args_json)`
     - 再把返回的 JSON 字符串 `json.loads(...)` 回 Python 对象

4. **ToolRegistryBridge 才是工具调用和追踪的关键节点**
   - `ToolRegistryBridge.callTool()` 会：
     - 从 `CodeactToolRegistry` 按名字取工具
     - 调 `tool.call(argsJson, toolContext)`
     - 记录 `callTrace`
     - 检查 `repliedToUser`
     - 观测返回值结构到 `ReturnSchemaRegistry`
   - 所以最后 `callTrace / replyToUserTrace` 不是 Python 自己记的，而是 Java bridge 在工具调用完成后记的

5. **三座 Bridge 的职责是分开的**
   - `AgentToolBridge`
     - 面向 `ToolCallback`
     - 提供简单 `agent_tools.call(...)`
     - 当前会创建一个空 `ToolContext`
   - `StateBridge`
     - 暴露 `get / set / has / getAll`
     - 让 Python 代码能访问 Agent state
   - `LoggerBridge`
     - 暴露 `info / debug / warn / error`
     - 让 Python 里的日志进入 Java 日志系统

#### 我的结论

**Tool 被 Python 调用，不是因为 Python 直接理解 Java Tool，而是框架做了两层桥接：**

1. **Java 对象注入层**
   - 把 `agent_state`、`logger`、`__tool_registry__` 这些 Java 对象放进 Python bindings

2. **Python 外壳生成层**
   - 根据 `CodeactToolRegistry` 动态生成 Python 方法/类
   - 让模型在 Python 里看见的是“像普通 Python 方法一样可调用的工具”
   - 但这些方法内部最终都回到 `__tool_registry__.callTool(...)`

所以更准确地说：

**模型不是“直接调用 Java Tool”，而是在调用“框架替 Java Tool 生成的 Python 代理方法”。**

#### 对项目的影响

这件事对我的电商项目影响很大：

1. 我们做 `GmvQueryTool / RefundAnalysisTool / FunnelAnalysisTool` 时，不需要自己发明一套 Python SDK
   - 只要注册成 `CodeactTool`
   - 框架就会自动把它生成到 Python 环境里

2. `callTrace` 的来源我现在能解释清楚了
   - 不是 LLM 自己记住“我调了哪个工具”
   - 是 `ToolRegistryBridge` 在 Java 侧统一记账
   - 所以后面做分析轨迹栏是有依据的

3. `targetClassName` 很关键
   - 它决定 Tool 在 Python 里是挂成类方法，还是全局函数
   - 这解释了为什么框架能给用户看到 `reply_tools`、`search_tools` 这种类分组体验

4. `AgentToolBridge` 不是我后面做业务 Tool 的主入口
   - 真正业务主链应该尽量走 `CodeactToolRegistry -> ToolRegistryBridge -> generatePythonToolCode`
   - 这也再次印证了我们前面定下来的原则：**框架能做的，不自己造旁路**

#### 面试可讲点

> 我后面不是只会说“Tool 能被 Python 调用”，而是能讲清楚这条链：  
> GraalCodeExecutor 先把 Bridge 对象注入 Python bindings，再从 CodeactToolRegistry 动态生成 Python 工具壳；模型在 Python 里看到的是普通方法，但方法内部会把参数 JSON 化后回调 Java 的 ToolRegistryBridge。  
> ToolRegistryBridge 再统一做工具调用、返回值观测和 callTrace 记录。  
> 所以这个框架的关键不是“把 Java 暴露给 Python”，而是“把 Java Tool 产品化成了 Python 可编排能力”。

### 评估图（Evaluation Graph）

**目标文件：**
- [BeforeModelEvaluationHook.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/evaluation/hook/BeforeModelEvaluationHook.java)
- [ReactBeforeModelEvaluationHook.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/evaluation/hook/ReactBeforeModelEvaluationHook.java)
- [DefaultEvaluationSuiteConfig.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-autoconfigure/src/main/java/com/alibaba/assistant/agent/autoconfigure/evaluation/DefaultEvaluationSuiteConfig.java)

#### 先说业务问题

我这次真正要读懂的不是“评估模块有哪些类”，而是：

**一次请求进来后，系统是不是先做任务理解和路径判断，再决定后面该给模型补什么内容。**

对我的电商 Agent，这个问题很重要，因为：

- “昨天 GMV 多少”这种高频问题不该和“华东 GMV 为什么跌了”走同一套思考成本
- 如果不先评估，就很难在后面稳定地区分快路径、深路径和额外 guidance

#### 我的初始理解

一开始我容易把 Evaluation 理解成：

- 只是一个简单分类器
- 或者只是一些运行日志，不真正影响后面的链路

后来重新梳理后，我现在更认可的理解是：

**Evaluation Graph 更像一组有顺序的判断节点，它的作用是先给当前请求贴标签、决定路径倾向，再把这些结果写进 state。**

#### 我现在的理解

1. 请求进来后，先经过 Evaluation
   - 它不是直接答题
   - 而是先判断：
     - 当前任务是什么类型
     - 更像快路径还是深路径
     - 当前阶段是否需要特殊指导

2. 它之所以叫 Graph，不是因为名字高级
   - 而是因为它更像多个评估节点按顺序/条件组合起来
   - 不是一个单点 if/else 判断

3. Evaluation 的结果会进入 `state`
   - 这样后面的 PromptContributor、FastIntent、执行链都能复用这批中间判断结果

4. 所以 Evaluation 的核心职责不是“替模型回答”
   - 而是：
     - **决定怎么理解这次请求**
     - **决定倾向走哪条路**

#### 一个月后只看这几句也能捡起来

- Evaluation 在前，PromptBuilder 在后
- Evaluation 决定“这题是什么、该走哪条路”
- 它不是单点分类器，更像多节点判断链
- 结果会写进 `state`，给后续 prompt 和执行链复用

#### 对项目的影响

这解释了为什么我的电商项目里不能只做：

- Tool
- Experience
- FastIntent

还必须理解 Evaluation 这一层，因为：

- 快路径 / 深路径分流
- root cause 问题的特殊 guidance
- 高价值经验何时进入 prompt

这些都不是“执行器”决定的，而是更早的评估层在决定。

#### 面试可讲点

> 我理解的一次请求不是直接扔给模型，而是先经过一层 Evaluation Graph。  
> 这一层会判断任务类型、路径倾向和是否需要特殊指导，并把结果写进 state。  
> 所以后面的 prompt 组装和执行链不是盲跑，而是建立在前面评估结果之上的。

### Prompt 动态组装

**目标文件：**
- [PromptContributorModelHook.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/prompt/PromptContributorModelHook.java)
- [ReactPromptContributorModelHook.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/prompt/ReactPromptContributorModelHook.java)
- [DefaultPromptContributorManager.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-prompt-builder/src/main/java/com/alibaba/assistant/agent/prompt/DefaultPromptContributorManager.java)
- [PromptContribution.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-prompt-builder/src/main/java/com/alibaba/assistant/agent/prompt/PromptContribution.java)

#### 先说业务问题

我这次要搞清楚的是：

**一次请求里，模型最终看到的 prompt 不是固定模板，那它到底是怎么被动态组装出来的。**

对电商分析 Agent 来说，这个问题的重要性在于：

- “昨天 GMV 多少”不需要太多背景
- “华东 GMV 为什么跌了”需要经验、归因 guidance、阶段提示
- 如果 prompt 不能按请求动态变化，复杂分析就会很笨

#### 我的初始理解

最早我容易把 PromptBuilder 理解成“字符串拼接器”：

- 看用户原话
- 再拼一点经验说明

但顺着链路想明白以后，我现在更认可的是：

**PromptBuilder 不是只看原始用户输入，而是结合 evaluation 结果和 state 中间资产，给这次请求动态加一层 contribution。**

#### 我现在的理解

1. PromptBuilder 不只是看用户输入
   - 还会看：
     - evaluation 结果
     - experience 候选
     - FastIntent 命中
     - 已经注入过哪些 guidance
     - 当前所处的阶段

2. `PromptContributor` 的职责不是决定走哪条路
   - 它的职责是：
     - **根据前面已经做好的判断，决定这次要给模型补什么上下文和指导**

3. 它最终不是去“改写一份固定 system prompt”
   - 而是生成一批本次请求专属的 contribution / messages
   - 然后把这些附加消息并入本次模型输入

4. 所以这套框架的 prompt 更像：
   - 固定 system prompt
   - + 用户问题
   - + 这次请求专属的动态 briefing

#### 一个月后只看这几句也能捡起来

- PromptBuilder 不只是拼字符串
- 它会结合 evaluation + state 动态加 contribution
- Evaluation 决定走法，PromptBuilder 决定喂法
- 最终模型看到的是“固定规则 + 本次动态 briefing”

#### 对项目的影响

这件事解释了为什么我的电商项目后面要把：

- Experience
- FastIntent
- React guidance
- report template

都接成框架内的 contribution，而不是自己硬写进一大段 system prompt。

因为真正可维护的方式不是：

- 一份越来越长的万能 prompt

而是：

- 固定基座
- 按当前请求动态补内容

#### 面试可讲点

> 我理解 PromptBuilder 不是模板字符串拼接，而是一个动态 contribution 机制。  
> 前面 Evaluation 决定这次请求怎么理解、倾向走哪条路径；后面的 PromptContributor 再结合 state 和经验资产，为这次请求额外补经验、guidance 和阶段提示。  
> 所以模型看到的不是静态 prompt，而是“固定规则 + 本次动态 briefing”。

### FastIntent：快路径如何跳过从零规划

**目标文件：**
- [FastIntentReactHook.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/experience/hook/FastIntentReactHook.java)
- [FastIntentService.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/experience/fastintent/FastIntentService.java)
- [FastIntentConfig.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-extensions/src/main/java/com/alibaba/assistant/agent/extension/experience/model/FastIntentConfig.java)
- [CodeactToolsStateInitHook.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-autoconfigure/src/main/java/com/alibaba/assistant/agent/autoconfigure/hook/CodeactToolsStateInitHook.java)

#### 先说业务问题

我这次要读懂的核心不是“FastIntent 怎么匹配字符串”，而是：

**对于“昨天 GMV 多少”这种高频标准问题，框架怎么避免每次都让模型从零理解和从零规划。**

这对电商分析 Agent 很关键，因为高频问题真正浪费时间的地方，不是 SQL 执行，而是：

- 每次都重新判断是不是 GMV 问题
- 每次都重新决定查哪条路径
- 每次都重新组织一套回答策略

#### 我的初始理解

一开始我容易把 FastIntent 理解成两种错误版本：

1. 只是给 prompt 多加一点提示
2. 直接绕开所有执行链，凭模板生成答案

后来梳理下来，真正的理解是：

**FastIntent 跳过的不是执行层，而是“从零理解问题、从零规划路径”这一层。**

#### 我现在的理解

1. FastIntent 会在主推理链之前先做一次快速匹配
   - 它更像一个“高频标准问题分流器”

2. 它匹配的对象不是 SQL，也不是某个 Python 函数名
   - 它匹配的是：
     - 某条 Experience
     - 某个 verified case
     - 某条标准 REACT 处理路径

3. 命中后，结果会写进 `state`
   - 这样后面的链路就知道：
     - 这题已经被认出来了
     - 该走哪条标准路径
     - 是快路径还是深路径

4. 命中 FastIntent 不等于“直接出答案”
   - 后面仍然可能：
     - 调 Tool
     - 跑 REACT plan
     - 生成最终回答

5. 真正被跳过的是：
   - 从零做意图理解
   - 从零做路径规划
   - 从零决定该走哪些经验/工具

#### 用我的项目理解最直观

比如：

- `昨天 GMV 多少？`
  - 命中 `exp-ecom-fast-gmv-yesterday`
  - 系统直接知道该走标准 GMV 快路径
  - 但仍然要真实调用 `GmvQueryTool`

- `华东 GMV 为什么跌了？`
  - 命中 `exp-ecom-fast-root-cause-east-drop`
  - 系统直接知道这是一条标准深路径
  - 但后面仍然要跑：
    - 区域
    - 品类
    - 漏斗
    - 退款
    这整条 REACT plan

#### 一个月后只看这几句也能捡起来

- FastIntent 不是“模板回答”
- 它是：**主推理前先分流**
- 命中的不是 SQL，而是 Experience / verified case / REACT path
- 它跳过的是“从零理解和规划”，不是 Tool 执行
- 命中结果会写进 `state`，供后续链读取

#### Experience 和 FastIntent 的区别

- Experience 更偏：
  - **模型先看到什么经验**
  - 是知识/经验注入链

- FastIntent 更偏：
  - **系统先走哪条标准路径**
  - 是执行前的路径分流链

这是我后面面试必须讲清楚的一个点。

#### 对项目的影响

这解释了为什么我现在在项目里要同时做：

- `verified_cases.yaml`
- `FastIntentConfig`
- REACT tool plan

因为我的目标不是只做一个“会回答”的 Agent，而是做一个：

**对高频标准问题会直接命中熟路，对复杂问题还能沿着固定分析套路往下走的业务分析 Agent。**

#### 面试可讲点

> FastIntent 的价值不是“跳过执行”，而是把高频标准问题提前分流到固定 Experience 或 REACT plan，减少重复意图理解和路径规划成本。  
> 命中后系统仍然会真实调 Tool、跑分析链，只是不再每次从零决定“这题怎么做”。

### Day 6：业务痛点如何落回框架能力

#### 今天的目标

不是继续抽象讨论“阿里 / 京东 / 拼多多有什么痛点”，而是把这些业务痛点真正落回当前项目资产里。

#### 今天做了什么

1. 新增了业务映射文档：
   - [business_capability_map.md](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/resources/experiences/business_capability_map.md)
   - 把三类大厂式痛点映射到：
     - 产品能力
     - 框架能力
     - 当前项目里的实际落地点

2. 把业务映射接回 Experience 主链
   - 在 `EcommerceExperienceBootstrapConfig` 里新增：
     - `exp-ecom-business-capability-map`
   - 让这份映射不只是 README 文字，而是框架可检索、可披露的经验资产

3. 把映射落回 verified cases / semantic model
   - `verified_cases.yaml` 增加：
     - `target_role`
     - `benchmark_reference`
     - `framework_capabilities`
   - `semantic_model.yaml` 增加：
     - `benchmark_pain`
     - `benchmark_value`

4. 补了 validator 覆盖
   - `ExperienceTestValidator` 现在会检查：
     - 业务映射 Experience 是否存在
     - verified cases 是否带 benchmark pain 映射
     - 框架能力标签是否覆盖到关键 case

#### 这件事为什么重要

这一步不是“写更多文档”，而是把项目从：

- 会跑的 Agent 原型

往前推成：

- **能明确回答“它解决哪类组织问题、为什么比普通 demo 更像内部产品”的 Agent 原型**

也就是说，今天做的是：

**把产品定位写回框架资产，而不是只留在面试讲稿里。**

#### 面试可讲点

> 我没有把竞品和业务痛点分析停留在文档层，而是继续把它们接进了 Experience 主链。  
> 我专门做了一份“业务痛点 -> 框架能力 -> 当前项目资产”的映射，并把这些标签写回 verified cases、semantic model 和 validator。  
> 这样项目不只是技术上能跑，也能在产品层面解释“为什么这套能力组合值得做”。

#### 继续往前推了一步：标签真的接回主链

今天我发现一个很现实的问题：  
`verified_cases.yaml` 里虽然已经有 `target_role / benchmark_reference / framework_capabilities`，但如果这些信息只停在 YAML 里，它们对 Agent 本身没有价值。

所以我补了一个中间层：

- 新增 `VerifiedCaseCatalog`
  - 统一把 `verified_cases.yaml` 读成可查询目录
  - 不让 PromptContributor、validator、评测摘要各自去读原始 YAML

- Prompt 侧
  - 在产品定位 / 业务价值 / 竞品差异 / 框架能力问答里
  - `EcommerceExperienceDisclosurePromptContributor` 会调用目录，给模型追加一段
    `verified case signal summary`
  - 里面会明确写：
    - 这类问题主要对应哪些角色
    - 对标哪类大厂式痛点
    - 依赖哪些框架能力

- 评测侧
  - `exp-ecom-evaluation-loop` 引用的 `evaluation_summary.md`
  - 启动时会自动附加一段分组快照：
    - `By Target Role`
    - `By Benchmark Reference`
    - `By Framework Capability`

#### 这一步为什么重要

这一步让我更确定一个原则：

**业务标签如果不能参与 Prompt、评测或执行链，它们就只是注释，不是系统能力。**

我这次做的其实不是“再补一个文档”，而是把这些标签变成：

- 模型能看到的 briefing
- 评测能看到的分组维度

这样后面我讲项目时，才能理直气壮地说：

> 我不是只在 YAML 里写了 role / benchmark / capability，
> 而是把这些业务元数据重新接回了 Prompt 和 evaluation 主链。

#### 面试可讲点

> 我一开始只是给 verified cases 加了 `target_role / benchmark_reference / framework_capabilities` 标签，但很快发现如果这些标签不进入 Prompt 或评测，它们只是静态注释。  
> 所以我专门做了一个 `VerifiedCaseCatalog`，统一把这些标签变成运行时可用目录：PromptContributor 会基于它给模型补角色/竞品/框架能力相关 briefing，evaluation summary 也会按这些维度输出分组快照。  
> 这让我项目里的业务元数据真正参与了 Agent 的主链，而不是停留在配置文件里。

#### 再往前推了一步：从“分组结构”变成“真实分组指标”

只把 `evaluation_summary` 扩成：

- By Target Role
- By Benchmark Reference
- By Framework Capability

还不够，因为那只是结构，不是结果。

所以我又补了一个启动期 runner：

- `VerifiedCaseEvaluationSummaryRunner`

它做的事很具体：

1. 逐条读取 `verified_cases`
2. 用真实问题去跑当前最小问答链
3. 同时跑 `FastIntentService`
4. 统计两类指标：
   - `fast path hit rate`
   - `deep path success rate`
5. 再按三类维度聚合：
   - `target_role`
   - `benchmark_reference`
   - `framework_capabilities`
6. 最后把这份 runtime summary 写回
   `exp-ecom-evaluation-loop` 的 `evaluation_summary` reference

#### 这一步为什么重要

这一步让我项目里的评测不再只是“有一份模板文档”，而是：

**启动时真跑一组业务问题，再把结果写回经验主链。**

这很像真实系统里的：

- 日常回归
- 启动健康检查
- 样本覆盖率追踪

而不是静态配置。

#### 面试可讲点

> 我后来意识到，按 role / benchmark / capability 做分组本身没有太大价值，关键是这些分组下面要挂真实指标。  
> 所以我没有只输出分组结构，而是让系统启动时逐条跑 verified cases：一边看 FastIntent 是否命中，一边看最小分析链是否成功完成，再按角色、竞品痛点和框架能力三个维度聚合成 fast-path hit rate 和 deep-path success rate。  
> 这样 evaluation summary 就不只是模板，而是一次真实回归的结果。

### Week 2 复盘：最小业务闭环是怎么搭起来的

#### 这周真正做的不是“几个 Tool”

如果只看代码，Week 2 很容易被误以为是在：

- 接数据源
- 写几个 Tool
- 补几个 validator

但从业务视角看，这周真正完成的是：

**把这个电商分析 Agent 的最小业务闭环先搭起来。**

#### 先有数据，不再是空壳

Week 2 的第一步不是让模型更聪明，而是先让系统里真的有一套最小电商数据能查：

- 有 schema
- 有 seed data
- 有 demo warehouse
- 启动时能自动初始化

这解决的是：

> Agent 不能只会说“我来分析”，它得真的有经营数据能看。

#### Tool 不是按表切，而是按业务动作切

我后来重新想清楚，Week 2 最大的产品判断之一是：

**Tool 不是按数据库表切的，而是按运营和分析师的高频动作切的。**

不是：

- `OrdersTool`
- `PaymentsTool`
- `ProductsTool`

而是：

- `GmvQueryTool`
- `RegionPerformanceQueryTool`
- `CategoryRankTool`
- `RefundAnalysisTool`
- `FunnelAnalysisTool`

这说明系统后面调的不是“表”，而是“分析动作”。

#### 先解决高频标准问题

Week 2 先做的是最常见、最容易验证、最值得产品化的那一层：

- 昨天 GMV 多少
- 哪个区域更差
- 品类排行怎么样

这不是在追求“什么都能答”，而是在先把高频标准问题做稳。

#### 同时挑了一个标准深路径

更关键的是，Week 2 不只做了标准报数，还把：

- `华东 GMV 为什么跌了？`

这类问题先做成了一个**标准深路径**。

它的价值不在于“系统已经万能”，而在于：

**系统已经开始会做真正的多步业务分析，而不只是查一个数。**

这条深路径背后的业务顺序其实很清楚：

1. 先看区域现状
2. 再看对比基线
3. 再拆品类结构
4. 再看漏斗
5. 最后看退款

所以 Week 2 已经开始把“为什么跌了”这种高频复杂问题做成标准分析套路。

#### 为什么说它是“最小业务闭环”

因为到 Week 2 结束时，系统已经同时具备了：

1. 有数据
2. 有标准业务动作
3. 有标准高频问题
4. 有最小复杂归因
5. 有启动期验证

这就不再是零散功能，而是一条从：

- 用户提问题
- 系统调分析动作
- 给出业务结论

的最小闭环。

#### 一个月后只看这几句也能捡起来

- Week 2 先把数据接进系统，让 Agent 真有数可看
- Tool 不是按表切，而是按运营/分析师的高频动作切
- Week 2 不只做了标准报数，还挑了“为什么跌了”这种高频复杂问题做成标准深路径
- 所以 Week 2 搭起来的是最小业务闭环，不是零散功能

#### 面试可讲点

> Week 2 我没有急着做复杂 Agent 规划，而是先把最小业务闭环做实：先把演示数据接进系统，再把 GMV、区域、品类、退款、漏斗这些高频分析动作产品化成 Tool。  
> 在此基础上，我先解决高频标准问题，再挑“华东 GMV 为什么跌了”这种高频复杂问题做成标准深路径，证明系统已经开始具备多步业务分析能力。  
> 所以后面 FastIntent、Experience、评测闭环这些能力接进来时，底下已经有一条真实可跑的业务主链承接。

### 从 GMV 结果走到交易结构：为什么要补 OrderQueryTool

#### 我补的不是“又一个查询工具”，而是 GMV 的第一层解释能力

在只有 `GmvQueryTool` 的时候，系统能回答：

- 昨天 GMV 多少
- 比前一天涨了还是跌了

但这还停留在“结果层”。真实业务里，运营下一句一定会追问：

- 是单量变少了，还是客单价掉了？
- 支付订单数还在不在？
- 退款订单是不是变多了？

所以 `OrderQueryTool` 的意义不是多一个指标，而是把：

**GMV = 订单量 × 客单价**

这层分析逻辑正式放进主链。

#### 它解决的业务问题

有了 `OrderQueryTool` 之后，系统开始能回答：

- 今天 GMV 变化，到底是订单量驱动还是客单价驱动
- 支付订单数是不是同步变化
- 退款订单有没有放大经营压力

这让交易分析从“看大盘结果”往前走了一步，变成“能拆交易结构”。

#### 为什么先补它，而不是先补 UserMetricTool

我当时判断，当前主链还是以交易分析为中心：

- GMV
- 区域
- 品类
- 漏斗
- 退款

在这条主线下，最自然缺的一层就是：

- 订单量
- 客单价
- 支付订单数

也就是 `OrderQueryTool`。它和现有 GMV / root cause 路线耦合更紧，能立刻提升“为什么变了”的解释力。

#### 一个月后只看这几句也能捡起来

- `GmvQueryTool` 先解决“卖了多少”
- `OrderQueryTool` 再解决“是靠更多单，还是更贵的单”
- 这一步把交易分析从结果层推进到结构层
- 也是后面把 root cause 做得更像分析师的重要一环

#### 面试可讲点

> 我后来发现只看 GMV 还不够，因为业务一定会继续问“是订单量变了，还是客单价变了”。  
> 所以我补了 `OrderQueryTool`，把订单量、支付订单数、退款订单数和客单价正式接进主链，让系统从“会报结果”走到“会拆交易结构”。  
> 这一步虽然不是最炫的 Agent 能力，但它让后面的归因分析更像真实业务系统，而不是只会报一个总数。

### 从交易分析扩到用户增长：为什么继续补 UserMetricTool

#### 用户增长不是另一条无关支线，而是交易分析的上游解释层

当系统已经能回答：

- GMV 多少
- 订单量多少
- 客单价怎么样

下一层更自然的问题就是：

- 今天到底有多少用户进来
- 活跃买家有多少
- 是用户规模掉了，还是渠道质量变差了

所以 `UserMetricTool` 的业务定位不是“顺手补个用户指标”，而是把主线从：

- 交易结果

扩成：

- 交易结果 + 用户规模 + 渠道质量

#### 这次先做的最小版本是什么

我没有一上来就做完整的留存/新老客体系，而是先用现有数据把最小用户增长主线接起来：

- DAU
- 活跃买家数
- 买家激活率
- 渠道转化率

这样系统至少已经能回答：

- 昨天 DAU 和活跃买家多少
- 哪个渠道转化更高

这说明主线已经开始从“交易分析 Agent”变成“交易 + 用户增长分析 Agent”。

#### 为什么这样切是合理的

这符合我前面一贯的产品思路：

- 不是按表切
- 不是一口气做完整增长系统
- 而是先按高频业务动作切，先把最小可用能力接进主链

#### 一个月后只看这几句也能捡起来

- `OrderQueryTool` 解释的是交易结构
- `UserMetricTool` 解释的是用户规模和渠道质量
- 两者一起让系统不只会看“卖了多少”，还能看“谁在买、从哪来”

#### 面试可讲点

> 在补完订单结构后，我继续补了 `UserMetricTool`，因为真实业务里交易结果的上游一定是用户规模和渠道质量。  
> 我先做了最小版本，只接 DAU、活跃买家和渠道转化率，让 Agent 至少能回答“昨天有多少活跃用户、哪个渠道转化更高”。  
> 这样项目主线就从单纯的交易分析，扩成了“交易 + 用户增长”的分析 Agent。

### 把用户增长快路径沉淀进主链：不只多两个问题，而是主线开始升级

#### 这次补的不是“两个新问法”，而是把用户增长正式放进标准资产

当 `UserMetricTool` 第一次接进最小问答链时，它还只是：

- 能回答昨天 DAU 和活跃买家多少
- 能回答哪个渠道转化更高

但如果只停在这里，它更像临时功能，不像项目主线的一部分。

所以我又补了两层：

- `verified_cases`
- `FastIntent / REACT Experience`

把这两类用户增长问题也沉淀成标准资产。

#### 这一步的业务意义

这表示系统开始承认一件事：

**交易分析的上游解释，不只是订单结构，还有用户规模和渠道质量。**

也就是说，现在的标准快路径不再只有：

- 昨天 GMV 多少
- 哪个区域更差
- 品类排行怎么样

还包括：

- 昨天 DAU 和活跃买家多少
- 哪个渠道转化更高

这让项目的业务覆盖从“交易看板”更像地走向“交易 + 增长看板”。

#### 为什么这对后面的 root cause 很关键

如果没有这一步，系统在解释 GMV 波动时只能说：

- 品类掉了
- 漏斗差了
- 退款高了

但还很难回答：

- 是不是用户规模先掉了
- 是不是渠道质量先变差了

所以用户增长快路径先沉淀下来，后面 root cause 才能自然升级。

#### 一个月后只看这几句也能捡起来

- `UserMetricTool` 先把用户规模和渠道质量接进主链
- 再用 `verified_cases + FastIntent` 把它们变成标准快路径
- 这一步让项目从“交易分析 Agent”开始往“交易 + 增长分析 Agent”升级

#### 面试可讲点

> 我没有把 `UserMetricTool` 当成一个孤立新功能，而是继续把它沉淀进 verified cases 和 FastIntent。  
> 这样“昨天 DAU 多少”“哪个渠道转化更高”也进入了标准快路径资产，说明系统开始把用户规模和渠道质量当成正式分析对象。  
> 这一步很重要，因为后面的 GMV root cause 才能自然回答“是用户规模掉了，还是订单结构掉了”。

### 把 gmv_root_cause 从结果归因升级成“用户规模 + 订单结构 + 漏斗 + 退款”的完整链

#### 原来的 root cause 已经能解释问题，但还缺两层

原来的 `gmv_root_cause` 已经会看：

- 区域
- 品类
- 漏斗
- 退款

这已经不错，但它更像：

- 结果层
- 结构层
- 过程层
- 售后层

还缺了中间特别关键的两层：

- 用户规模有没有掉
- 订单结构有没有掉

#### 为什么要补这两层

因为真实业务里，GMV 下滑最常见的追问就是：

- 是不是用户盘子先小了
- 还是人还在，但订单量少了
- 还是订单量还行，但客单价掉了

如果没有：

- `OrderQueryTool`
- `UserMetricTool`

系统就只能围绕品类、漏斗、退款转，很难把“谁先掉”这件事讲清楚。

#### 升级后，这条深路径在业务上怎么理解

现在 `gmv_root_cause` 这条链已经变成：

1. 先确认区域 GMV 真的掉了
2. 再看订单结构，是订单量掉了还是客单价掉了
3. 再看用户规模，是 DAU / 活跃买家先掉了，还是交易端自己变差了
4. 再拆品类，看谁拖了后腿
5. 再看漏斗，是流量问题还是转化问题
6. 最后看退款，确认有没有售后放大影响

这比原来更像一个真实分析师在做归因。

#### 这一步最值钱的变化

系统现在终于能开始回答这种更像业务的话：

- 用户规模先掉了，后面交易自然承压
- 用户规模还在，但订单量掉了，说明问题更像交易承接
- 单量还可以，但客单价掉了，说明货盘或价格带可能出问题

这就是从“会做 root cause”走向“做得更像人”的关键一步。

#### 一个月后只看这几句也能捡起来

- 原 root cause 只看区域/品类/漏斗/退款
- 新 root cause 先补用户规模和订单结构
- 目标是能回答“是用户规模掉了，还是订单结构掉了”

#### 面试可讲点

> 我后来觉得原来的 root cause 还不够完整，因为它只能告诉我品类、漏斗、退款发生了什么，却很难回答“问题最早出现在用户规模，还是交易结构”。  
> 所以我把 `OrderQueryTool` 和 `UserMetricTool` 都接进了标准深路径，让系统在区域、品类、漏斗、退款之外，还会先判断订单量/客单价和 DAU/活跃买家。  
> 这一步让 `gmv_root_cause` 从一个能跑的多步链，升级成了更像真实分析师的归因工作流。

### 补交付件：把 Tool 能力和 root cause 演示链显式写出来

#### 今天补的不是新功能，而是两份关键交付件

- `TOOL_CATALOG.md`
- `DEMO_ROOT_CAUSE_CHAIN.md`

#### 为什么这一步值得单独做

到这一步为止，系统主链已经不弱了，但有一个典型风险：

- 代码里明明已经有高频看数、订单结构、用户规模、漏斗、退款、root cause
- 但如果不把它们显式整理出来，外部看上去还是像“零散功能集合”

所以这一步做的事，不是再加新能力，而是把现有能力整理成：

1. 一份“当前到底有哪些业务动作 Tool”
2. 一份“升级后的 gmv_root_cause 演示时该怎么讲”

#### 我的理解

`TOOL_CATALOG.md` 解决的是：

- 这套 Agent 现在到底会做什么
- Tool 为什么不是按表切，而是按业务动作切
- 哪些问题走快路径，哪些问题走标准深路径

`DEMO_ROOT_CAUSE_CHAIN.md` 解决的是：

- 现在这个最值钱的 root cause 演示，到底证明了什么
- 每一步 Tool 在业务上是在排查什么
- 演示时应该怎样从“报数”过渡到“归因”

#### 对项目的影响

这一步补完后，项目对外表达更完整了：

- 不是只有代码和日志
- 而是有一份明确的 Tool 能力目录
- 也有一份可以直接拿来录屏和面试讲解的标准深路径演示脚本

#### 一个月后只看这几句也能捡起来

- `TOOL_CATALOG.md`：把当前 Agent 的业务动作能力显式列出来
- `DEMO_ROOT_CAUSE_CHAIN.md`：把最值钱的 root cause 链路显式讲出来
- 这一步不是新增功能，而是把“已有能力”变成“可展示、可讲解、可复用的交付件”

## Day 15-16 - Week C 第一版：让系统开始记住你刚才在问什么

### 这次做的不是新 Tool，而是在问答入口前面补了一层“轻量会话理解”

到这一步之前，系统已经会：

- 昨天 GMV 多少
- 昨天订单量和客单价怎么样
- 昨天 DAU 和活跃买家多少
- 哪个渠道转化更高
- 华东 GMV 为什么跌了

但这些大多还是单轮问题。  
如果用户下一句说：

- 那华东呢？
- 那华南呢？
- 活跃用户怎么样？

系统之前并不真正理解“你是在接着刚才那个问题问”。

### 这轮补的核心能力

1. **会话状态**
   - 记住上一轮在问什么类型的问题
   - 记住上一轮的日期、区域、品类上下文

2. **时间语义**
   - 先支持最小可用的：
     - 今天
     - 昨天
     - 前天
     - 去年同期（先有规则入口）

3. **歧义澄清**
   - 当用户只说“活跃用户怎么样”时，不直接自作主张返回 DAU
   - 先问清楚是 DAU 还是活跃买家

### 这轮在业务上真正解决了什么

它解决的不是“多轮对话很高级”，而是一个很现实的问题：

> 运营和分析师真实沟通时，不会每一句都重新把问题说完整。

他们会自然地接着问：

- 昨天 GMV 多少？
- 那华东呢？
- 那华南呢？
- 活跃用户怎么样？

如果系统每次都把问题当成全新请求，就会很不自然，也很不像真正能协作的分析助手。

### 现在最小可用的效果

启动验证里已经跑通了 5 类多轮场景：

- `昨天 GMV 多少？` -> `那华东呢？`
- `昨天订单量多少，客单价怎么样？` -> `那华东呢？`
- `昨天 DAU 和活跃买家多少？` -> `那华东呢？`
- `2026-05-17 华东 GMV 为什么跌了？` -> `那华南呢？`
- `活跃用户怎么样？` -> 先澄清

这意味着系统已经开始从“单轮报数”走向“能连续跟着业务问题走”。

### 这轮最重要的产品判断

我没有一上来就做很重的通用对话状态机，而是先在最小问答链入口加一层轻量会话解析：

- 先继承上一轮意图
- 再继承上一轮日期/区域/品类
- 再对明显歧义词做澄清

这样做的好处是：

- 不打断现有 Tool 主链
- 先让最值钱的连续追问场景跑起来
- 以后再逐步把这层能力接回更完整的框架主链

### 目前的框架/数据边界

这轮也有一个明确边界：

- “去年同期”这类时间语义现在已经有了解析入口
- 但 demo 数据还没有完整覆盖 2025 同期数据

所以当前它更像“先把语言理解层搭起来”，还不是“完整历史同比分析已经成熟”。

### 一个月后只看这几句也能捡起来

- Week C 第一版不是加新 Tool，而是让系统能记住上一轮在问什么
- 先做了会话状态、时间语义和歧义澄清
- 重点场景是：`那华东呢 / 那华南呢 / 活跃用户怎么样`
- 这是从“会答题”走向“像同事一样接着聊”的第一步

### 面试可讲点

> 我在 Week C 先没有追求一个很重的通用会话系统，而是优先把业务上最常见的连续追问场景做起来。  
> 具体做法是在问答入口前加一层轻量会话理解：继承上一轮的意图、日期和区域上下文，并对“活跃用户”这种高频歧义词先澄清。  
> 这样系统就开始能自然接住“那华东呢”“那华南呢”这类真实业务表达，而不是每次都要求用户重新把完整问题说一遍。

## Day 17-18 - Week C 第二版：让指标词典真正开始驱动理解，而不是只躺在 YAML 里

### 这次做的不是“再加几个同义词”，而是把 `metric_dictionary.yaml` 变成运行时能力

到这一步之前，`metric_dictionary.yaml` 更多还是一个静态资产：

- 我们知道应该有 GMV、退款率、转化率、DAU 这些词
- 也知道它们可能有别名和歧义
- 但系统本身还没有真正按这份词典去理解用户问题

这次补完后，它开始变成运行时能力：

- 能按别名识别用户到底在问哪个指标
- 能识别“活跃用户”这种高频歧义词
- 能在多轮追问里继承上一轮正在讨论的指标和维度

### 这轮补的核心能力

1. **指标别名真正生效**
   - 不再只是把词写在 YAML 里
   - 系统会真的用它来判断：
     - 用户问的是 GMV
     - 还是退款率
     - 还是转化率

2. **歧义检测真正生效**
   - 现在“活跃用户怎么样？”不会直接猜成某个固定指标
   - 会先判断这是歧义表达
   - 再追问是 DAU 还是活跃买家

3. **维度追问进入最小会话链**
   - 不只是接 `那华东呢 / 那华南呢`
   - 现在也能开始接：
     - `那女装呢？`
     - `那退款率呢？`

### 这轮在业务上真正解决了什么

它解决的是一个非常真实的问题：

> 业务不会每次都说完整的“昨天华东女装退款率是多少”，  
> 而是会顺着刚才的话继续问：“那女装呢？”“那退款率呢？”

如果系统只会接完整句子，它更像一个查数表单；  
如果它能在上一轮基础上补全维度和指标，它才更像在跟业务同事连续讨论同一个问题。

### 现在最小可用的效果

这轮之后，启动验证里除了原来的连续追问，还新增了更贴近真实业务的场景：

- `昨天 GMV 多少？` -> `那女装呢？`
- `昨天 GMV 多少？` -> `那退款率呢？`
- `活跃用户怎么样？` -> 先澄清 DAU 还是活跃买家

这意味着系统已经开始具备两种更像人的能力：

- 能沿着上一轮问题继续缩小维度
- 能在业务词有歧义时先问清楚

### 这轮最重要的产品判断

我没有一上来就做一个很重的通用语义解析器，而是先做最值钱的最小增强：

- 让 `metric_dictionary.yaml` 真驱动别名识别
- 让高频歧义词先澄清
- 让“区域 / 品类 / 指标”三类追问先接起来

这样做的好处是：

- 不会把系统复杂度一下拉太高
- 先把真实业务对话里最常见的追问接住
- 以后再逐步扩成更完整的 semantic model 和多轮理解链

### 目前的框架/数据边界

这轮也有两个明确边界：

- 现在的别名和歧义判断，主要还是围绕高频指标和高频问法
- “去年同期”这类时间语义虽然已经有解析入口，但 demo 数据还没有完整的历史覆盖，所以更多是在语言理解层先搭桥

### 一个月后只看这几句也能捡起来

- `metric_dictionary.yaml` 现在不只是资产文件，而开始真正驱动别名识别和歧义检测
- 系统已经能接 `那女装呢 / 那退款率呢` 这类维度/指标追问
- “活跃用户”这类高频歧义词开始先澄清再回答
- Week C 第二版的重点是：让系统更像在连续讨论一件业务问题，而不是只接完整问句

### 面试可讲点

> 我在 Week C 第二版没有先做一个很重的语义系统，而是优先把最常见的业务表达接住：一是让指标词典真正参与运行时理解，而不是只躺在 YAML 里；二是让“那女装呢”“那退款率呢”这种维度追问也能沿着上一轮问题继续往下问。  
> 这样系统不只会答完整句子，还开始会顺着业务语境补全维度和指标，并在“活跃用户”这类歧义表达上先澄清，协作感会更强。

## Day 19-20 - Week C 第三版：让 `semantic_model.yaml` 开始参与运行时理解，而不是只做说明文档

### 这次做的不是“再补一份配置”，而是把语义模型往运行时前推了一步

到这一步之前，`semantic_model.yaml` 虽然已经写出了很多很好的业务定义：

- 华东 / 华南是什么
- 女装 / 家电 / 美妆是什么层级
- “那退款率呢”“那订单量呢”这种追问怎么理解
- GMV 下跌类问题的 root cause playbook 应该怎么拆

但如果它只是放在 resources 里，那它更像一份说明文档，  
还不算真正进入 Agent 的运行时脑子。

这次的目标就是先迈出第一步：

- 让区域词表不再写死在代码里
- 让品类词表不再写死在代码里
- 让一部分高频追问切换开始读 semantic model，而不是只靠手写规则

### 这轮补的核心能力

1. **区域和品类词表从 `semantic_model.yaml` 读取**
   - 之前像 `华东 / 华南`、`女装 / 家电 / 美妆` 这些值更多是硬编码
   - 现在它们开始由 semantic model 提供

2. **高频追问切换开始读 semantic model**
   - 像：
     - `那退款率呢`
     - `那订单量呢`
     - `那用户呢`
   - 不再只是 if/else 写死，而开始借 semantic model 的 follow-up 规则做运行时判断

3. **语义模型开始真正定义 Agent 的业务世界**
   - 指标词典负责“这个词在说什么指标”
   - semantic model 开始负责“这个词在什么业务上下文里怎么补全”

### 这轮在业务上真正解决了什么

它解决的是一个很现实的问题：

> 系统不能永远靠几条写死规则装作听得懂业务，  
> 否则每多一个区域、品类、追问方式，都要继续改代码。

把这一步往 semantic model 推之后，系统开始具备一种更健康的演化方式：

- 业务语义尽量写进模型
- 运行时从模型里读
- 代码更多只负责解释和执行

这会让后面接 Olist、扩类目、加区域、补新追问都更自然。

### 这轮最重要的产品判断

我没有一上来就试图把整份 semantic model 做成一个很重的推理引擎，  
而是先选最值钱的运行时切入点：

- 区域识别
- 品类识别
- 高频 follow-up metric 切换

这样做的好处是：

- 风险小
- 改动可控
- 但业务收益非常直观

也就是说，这一步的策略不是“语义系统一次到位”，而是“先让最值钱的语义开始真的参与运行时”。

### 目前的框架/实现边界

这轮也有明确边界：

- 现在 semantic model 参与的是最小会话理解，不是完整规划器
- root cause playbook 已经写进 semantic model，但还没有完全替代手写深路径逻辑
- 更复杂的 alias、层级下钻、role-aware 语义仍然要后续继续接

### 一个月后只看这几句也能捡起来

- `metric_dictionary.yaml` 解决“用户在说哪个指标”
- `semantic_model.yaml` 开始解决“这个指标和维度在业务上下文里怎么补全”
- 这一步先把区域、品类和高频追问切换接进运行时
- Week C 第三版的重点是：让语义模型不只解释业务，而开始驱动会话理解

### 面试可讲点

> 我在 Week C 第三版没有把 semantic model 停留在说明文档层，而是先把最值钱的部分接进运行时：区域和品类词表不再写死，高频追问像“那退款率呢”“那订单量呢”“那用户呢”也开始通过 semantic model 的 follow-up 规则做判断。  
> 这样项目的语义层就不只是给人看的，而开始真正参与 Agent 的会话理解，这对后面扩区域、扩品类、接真实公开数据都很关键。

## Day 21 - Week C 第四版：让“澄清后继续回答”形成最小闭环

### 这次做的不是再多问一句，而是把澄清真正接回业务回答

到这一步之前，系统已经会在一些高频歧义场景里先问清楚：

- `活跃用户怎么样？`
- 系统会追问：`您说的是 DAU 还是活跃买家？`

但如果后面用户只回一句：

- `DAU`

系统之前更像是把它当成一条很短的新问题，而不是把它理解成“对刚才澄清问题的回答”。

这次补完后，系统会：

1. 记住当前在澄清什么问题
2. 记住原问题的意图、日期、区域、品类和指标上下文
3. 当用户回 `DAU / 日活 / 活跃买家` 时，直接恢复原问题继续作答

### 这轮在业务上真正解决了什么

它解决的是一个真实协作体验问题：

> 如果系统只会先提问，但问完以后接不上，那它还是不像一个能协作的分析助手。

业务不会把“澄清”看成一个独立任务，而会把它理解成：

- 先确认口径
- 然后继续回答刚才那个问题

这次补完后，Agent 才真正开始具备这种协作感。

### 当前最小闭环

现在已经可以跑通：

- `活跃用户怎么样？`
- 系统先问：`您说的是 DAU 还是活跃买家？`
- 用户回答：`DAU`
- 系统继续回答当天 DAU / 活跃买家 / 支付订单数 / 买家激活率

这说明澄清链已经不是“只会先问”，而是开始能恢复上下文继续答。

### 这轮最重要的产品判断

我没有把这一步做成完整的 slot-filling 对话系统，而是先补最值钱的最小闭环：

- 先只覆盖高频歧义场景
- 先把待澄清上下文存进 session
- 先支持短回答回接原问题

这样做的好处是：

- 改动小
- 业务收益直接
- 能明显减少“系统先问了，但又接不上”的体验落差

### 当前边界

- 这轮只完整补通了“活跃用户 -> DAU / 活跃买家”这类高频澄清
- `转化率`、`订单量` 这类澄清链还可以继续用同样方式补
- 如果澄清后用户回复特别跳跃，当前最小会话链仍然不是完整的多轮对话图谱

### 一个月后只看这几句也能捡起来

- Week C 第四版的重点不是“会先问清楚”，而是“问清楚后还能接着答”
- 这次先把 `活跃用户 -> DAU / 活跃买家` 做成最小闭环
- 系统会保存待澄清上下文，再把短回答接回原问题
- 这一步补的是协作体验，不是单纯多一条规则

### 面试可讲点

> 我发现很多系统做澄清时只做到“先追问”，但没有真正把澄清结果接回原问题，所以用户体验会断。  
> 我在 Week C 第四版先补了一个最值钱的最小闭环：对于“活跃用户怎么样”这类高频歧义问题，系统不仅会先问清楚是 DAU 还是活跃买家，还会把待澄清上下文存进 session，等用户回一句 `DAU` 后继续回答，而不是把它当成一条新的孤立问题。

## Week 3 复盘：它不是继续加功能，而是让系统开始像分析同事

### 先说结论

如果 Week 2 做的是：

- 有数据
- 有 Tool
- 有高频问答
- 有最小归因

那么 Week 3 做的就不是“再加几个功能”，而是让系统从单轮问答器，开始更像一个能持续协作的分析同事。

### Week 3 真正在补什么

#### 1. 记住最值钱的业务参数

Week 3 没有去做“把所有聊天历史原样记住”，而是先记：

- 上一轮在问什么类型的问题
- 上一轮的指标
- 上一轮的日期
- 上一轮的区域
- 上一轮的品类

因为真实业务连续追问时，最重要的不是原话，而是这些业务参数。

#### 2. 听懂业务时间表达

业务不会每次都说：

- `2026-05-17`

更常说的是：

- 昨天
- 上周
- 去年同期

所以 Week 3 补的是“业务时间入口”，让系统开始能把这些说法翻译成数据查询时间。

#### 3. 处理业务口径歧义

系统一旦开始更像人一样接话，就马上会遇到一个问题：

- 活跃用户
- 转化率
- 订单量

这些词都可能有多个口径。

所以 Week 3 不只是继续答题，而是开始做：

- 指标词典
- 歧义检测
- 先澄清再回答

#### 4. 澄清后还要能继续答

这一步特别关键。

如果系统只会：

- 先问：你说的是哪种口径？

但问完以后接不上，那它依然不像一个能协作的分析助手。

所以 Week 3 后半段补的是：

- 先澄清
- 再把澄清结果接回原问题
- 继续按原业务上下文回答

这才是真正的协作闭环。

### Week 3 在业务上解决的到底是什么问题

它解决的是：

> 业务不会每次都说完整句子，也不会每次都用标准指标名，还经常会用有歧义的业务词。  
> 如果系统接不住这些真实表达，它就不像内部分析产品，只像一个查数表单。

所以 Week 3 的真正价值，不是“模型更聪明了”，而是：

**系统开始更像真实业务沟通。**

### 一个月后只看这几句也能捡起来

- Week 3 不是继续加 Tool，而是让系统开始像分析同事
- 它先记住最值钱的业务参数，而不是全文聊天记录
- 它开始听懂“昨天、上周、去年同期”这种业务时间
- 它开始发现口径歧义，先问清楚，再继续答

### 面试可讲点

> Week 2 我先把最小业务闭环做出来，让系统有数据、有 Tool、有高频问答和最小归因链。  
> 但那时它更像一个单轮问答器，所以 Week 3 我重点补了连续协作能力：让系统记住上一轮最值钱的业务参数，听懂“昨天、上周、去年同期”这种业务时间表达，并且在“活跃用户、转化率、订单量”这类高频歧义词上先澄清，再继续回答。  
> 这样它才开始更像一个能和运营、分析师一起持续讨论问题的内部分析助手。

---

## Day 22-23 - Week D 第一版：让评测不只给成功率，还自动产出坏例子池

### 今天在推进什么

Week D 这一步，不再只是补 verified cases 和 FastIntent，而是把“失败样本怎么沉淀下来”也接回主链。

之前系统已经能在启动时跑一遍 verified cases，算出：

- fast path hit rate
- deep path success rate
- role / benchmark / capability 分组结果

但这还不够，因为它只能告诉我：

- 系统总体好不好

却不能告诉我：

- 失败主要卡在哪里
- 下次该优先修 FastIntent、路径判断，还是深路径执行链

所以这次补的是：

**让启动评测自动生成一版 runtime bad case snapshot。**

### 这一步在业务上解决什么问题

如果没有坏例子分类，评测很容易停留在“像 dashboard 一样的结果展示”：

- 命中率是多少
- 成功率是多少

但产品团队真正需要的是：

- 失败是不是因为高频问题没命中快路径
- 复杂问题是不是被误降级成普通问答
- 深路径是不是走到一半断掉了

也就是说：

**命中率回答的是“系统现在表现怎样”，坏例子池回答的是“系统下一步该修哪里”。**

### 这次补了什么运行时闭环

现在 `VerifiedCaseEvaluationSummaryRunner` 不只会输出 runtime summary，还会基于每条 verified case 的真实执行结果，把失败样本归类成这几类：

- `FastIntent 漏命中`
- `快路径命中但执行未闭环`
- `深路径分析链中断`
- `深路径被误走成其它路径`

然后把这些结果回写进 `exp-ecom-evaluation-loop` 里的 `bad_cases.yaml` 引用内容。

所以系统现在有两层 bad case：

- **静态 bad case**：人工先写进去的典型失败样本
- **runtime bad case snapshot**：启动回归时自动跑出来的新失败分类

这就开始有“失败问题会自己回流成系统资产”的味道了。

### 为什么这比只看命中率更值钱

因为对这个项目来说，真正难的不是“把几个问题答出来”，而是：

- 哪类高频问题还没稳定产品化
- 哪类深路径问题虽然能答，但没有按预期路径走
- 哪些地方后面应该修 semantic model
- 哪些地方应该补 verified case / FastIntent 规则

所以这一步本质上是在把：

**评测 -> 失败分类 -> 下一步修复方向**

串成一条最小闭环。

### 一个月后只看这几句也能捡起来

- Week D 第一版不只看命中率，而是开始自动产出 runtime bad case snapshot
- runtime bad case 的价值，不是证明系统差，而是告诉我下一步该修哪类问题
- 对内部分析 Agent 来说，“失败样本能不能回流成系统资产”比单次回答更重要

### 面试可讲点

> 我在 Week D 没把评测停留在命中率统计，而是把 verified case 的真实执行结果继续沉淀成 runtime bad case snapshot。  
> 这样系统不只会告诉我“快路径命中率是多少、深路径成功率是多少”，还会告诉我失败主要是 FastIntent 漏命中、路径类型错了，还是深路径没走完。  
> 对业务 Agent 来说，这一步很重要，因为它把“评测结果”真正变成了“下一轮要修的产品资产池”。

---

## Day 24 - Week 4 启动：先把 Olist raw 层入口搭出来，不打断当前主链

### 今天在推进什么

Week 4 不是立刻把当前 demo 数据推翻重来，而是先把公开数据升级支线的入口搭出来。

这次先做的不是：

- 立刻切换所有 Tool 到 Olist
- 立刻重做 dwd / dim / ads

而是先把：

- `raw_olist_*` 第一版表结构
- 启动开关
- 目录和说明文档

放进项目里。

### 这一步在业务上解决什么问题

当前手工 seed data 很适合把 Agent 主链做通，
但如果后面要把项目讲成“更像真实数据产品”，只靠手工数据说服力不够。

所以需要一条升级路径：

`公开数据集 -> raw -> dwd -> dim -> ads -> Agent tools`

Week 4 先做 raw 层入口，就是把这条路径的第一段先接出来，
同时又不影响当前已经稳定的：

- 高频看数
- root cause 深路径
- verified case 回归

### 为什么不直接替换当前数据

因为现在项目最值钱的是：

- Agent 主链已经跑通
- Experience / FastIntent / evaluation loop 已经稳定

如果这个时候直接把所有数据底座推翻掉，主线很容易被数据清洗工作淹没。

所以更合理的节奏是：

- 保留当前 seed data 继续支撑主链
- 并行搭 Olist raw 层入口
- 后面再逐步迁 `GMV / 区域 / 品类` 三条快路径

### 一个月后只看这几句也能捡起来

- Week 4 不是“现在就全面切 Olist”，而是先把公开数据 raw 层入口搭出来
- 这样后面拿到 CSV 时，不需要再从零补 schema 和接入开关
- 这一步的目标是增强数据可信度，不是打断当前 Agent 主链

### 补了一层 very small importer / manifest 的意义

这次我没有急着写 CSV 导入脚本，而是先把：

- `import_manifest.yaml`
- `IMPORT_PLAN.md`

补上。

这一步最重要的不是“文件多了两个”，而是把未来 Olist 接入时最容易混乱的 4 件事先定清楚：

- 哪些 CSV 对应哪些 raw 表
- 先导哪张表
- raw 层哪些字段先原样保留
- 后面做 `dwd_orders / dwd_order_items` 时，关键字段从哪里来

这样后面就不是“边导数据边改设计”，而是沿着既定数据契约推进。

### 一个月后只看这几句也能捡起来

- Week 4 的 very small manifest，不是导入脚本，而是公开数据接入契约
- 它先固定导入顺序、字段映射和 raw 层边界
- 这样后面接 Olist 时，更像真实数据产品，而不是临时写脚本

### raw -> dwd 第一版为什么先做并行表

这次继续推进 Olist 支线时，我没有直接让公开数据去覆盖当前主链里的：

- `dwd_orders`
- `dwd_order_items`

而是先设计了一套并行表：

- `dwd_olist_orders`
- `dwd_olist_order_items`

原因很简单：

- 当前主链已经稳定支撑了高频看数、root cause、多轮追问和 verified case 回归
- Olist 支线现在还处在“公开数据刚开始接入”的阶段

如果这个时候直接把半成品 dwd 换进主链，风险太大。

所以更稳的做法是：

- 先把 raw -> dwd 的加工规则写清楚
- 先把支付金额、用户城市、州、品类原始字段这些关键口径接出来
- 等并行 dwd 稳定后，再逐步迁 `GMV / 区域 / 品类` 三条快路径

### 一个月后只看这几句也能捡起来

- Olist 支线现在先做 `dwd_olist_*`，不是马上替换主链 dwd
- 这样能先验证公开数据加工规则，不会把当前 Agent 主链打断
- 这是“并行迁移”思路，不是“推翻重来”

### ultra-light importer 骨架为什么现在就值得做

这次我没有等 Olist CSV 真进仓库以后再临时想办法导，而是先补了一层 very small importer：

- 应用内增加了 `olist-raw-import-dir`
- 只要后面把 Olist CSV 放到约定目录
- 启动时就可以直接灌进 `raw_olist_*`

这一步的价值不在于“现在已经导了公开数据”，而在于：

- 未来接公开数据时，不需要再临时写一次导入脚本
- raw 层接入方式和导入顺序已经稳定
- 你后面演示时可以很自然地说：这不是概念 schema，而是已经有最小导入骨架

### 一个月后只看这几句也能捡起来

- Week 4 的 importer 骨架不是另起一套数据工程，而是把 Olist CSV 导入做成应用内最小能力
- 后面只要把 CSV 放到指定目录，就能直接灌进 `raw_olist_*`
- 这样公开数据支线从一开始就是“能落地的”，不是只有文档
- 我还补了一套 7 张单行 sample CSV 做烟测，启动日志已经能看到每张 `raw_olist_*` 表各导入 1 行

### dim_olist_* / ads_olist_* 第一版设计在解决什么

继续往前推进 Olist 支线时，我开始补：

- `dim_olist_regions / dim_olist_categories / dim_olist_products`
- `ads_olist_daily_core_metrics / ads_olist_region_daily / ads_olist_category_daily`

这一步不是为了把 Olist 一口气做成完整中国电商语义，而是先把：

- 区域
- 品类
- 大盘日级汇总

这些当前 Agent 最需要的快路径支撑层补出来。

也就是说：

- `dwd_olist_*` 解决“发生了什么”
- `dim_olist_*` 解决“这些字段在业务里是什么标签”
- `ads_olist_*` 解决“哪些高频问题可以直接查快照”

### 一个月后只看这几句也能捡起来

- `dim_olist_*` 是公开数据的最小业务标签层
- `ads_olist_*` 是公开数据的最小快照层
- 它们的目标不是立刻替换主链，而是为 `GMV / 区域 / 品类` 三条快路径迁移做准备

### root cause 开始进入“混合数据迁移期”

这次继续推进 Olist 支线时，我没有要求整个 `gmv_root_cause` 一步切到公开数据，而是只先迁：

- 区域判断
- 品类判断

保留：

- 订单结构
- 用户规模
- 漏斗
- 退款

仍然沿当前 demo 主链。

这样做的业务意义是：

- 先让 root cause 最容易标准化、最容易和公开订单数据对齐的部分先迁过去
- 不为了“全都换成公开数据”牺牲当前分析链的稳定性
- 让系统进入一个可解释的混合阶段：哪些判断来自公开数据，哪些判断还来自主链数据，都能说清楚

所以这一步不是“切一半所以不完整”，而是：

**先把最值钱、最可信、最容易平滑迁移的分析切片迁过去。**

### 为什么要给 prefer-olist-analytics=true 补专门启动验证

混合数据迁移最容易出的问题不是直接报错，而是：

- 配置开了
- 系统还能答
- 但其实已经偷偷退回 demo 主链

这种退化如果只看“应用能不能启动”是发现不了的。

所以这次我专门补了一条验证：

- 只要 `prefer-olist-analytics=true`
- 启动时就必须验证 `gmv_root_cause`
- 其中 `region_metrics_source / category_metrics_source`
- 必须明确等于 `olist_public_dataset`

这样后面这条 Olist 支线如果退化，不会变成“还能跑，但没人知道已经退回旧链”，而是启动时直接暴露出来。

### 一个月后只看这几句也能捡起来

- root cause 现在进入了“混合数据迁移期”：区域/品类先迁，订单/用户/漏斗/退款暂留主链
- 这不是妥协，而是为了稳住分析链，同时让公开数据优先接管最适合它的部分
- `prefer-olist-analytics=true` 不再只是配置开关，而是有专门启动验证守住的质量承诺

### 导入“做完”和“做好”是两件事

继续补 Olist 数据线时，我意识到只把 CSV 灌进 `raw_olist_*` 还不够。

因为“做完导入”只说明：

- 文件进来了
- 表里有数据了

但还不能说明：

- 关键表是不是都进了
- 订单和客户能不能对上
- 订单商品和商品表能不能对上
- 支付表是不是在引用真实订单

所以这一步我补了两层校验：

1. `raw import summary`
   - 明确 7 张 `raw_olist_*` 表各进了多少行
2. `relationship integrity checks`
   - `orders_without_customer`
   - `items_without_order`
   - `items_without_product`
   - `payments_without_order`

这样导入这件事就从：

- “日志里好像导进去了”

升级成：

- “我知道每张表进了多少行，而且知道关键关联关系没有断”

### 为什么这一步有业务价值

因为后面所有：

- `dwd_olist_orders`
- `dwd_olist_order_items`
- `dim_olist_*`
- `ads_olist_*`
- 以及 `GMV / 区域 / 品类` 快路径

都建立在 raw 层关联关系可信这个前提上。

如果 raw 层就已经有：

- 订单对不上客户
- 商品明细对不上商品
- 支付记录找不到订单

那后面做出来的经营分析只会是“算得很快，但口径一开始就错了”。

### 一个月后只看这几句也能捡起来

- Olist 数据导入不只是“灌进表里”，还要做覆盖率和关联完整性检查
- summary 解决“进了多少”，integrity check 解决“连得对不对”
- 只有 raw 层可信，后面的 dwd/dim/ads 和 Agent 快路径迁移才有意义

---

## Day 29-30 - Week E 第一版：让 Agent 开始主动出手

### 这次先做成了什么

这轮我没有再继续扩分析能力，而是先把 Agent 从“等人来问”推进到“自己知道什么时候该行动”。

先落地了两条最小主动链：

1. **定时日报**
   - 到点自动汇总昨日 `GMV / 订单 / 用户`
   - 再通过 `send_notification` 往外发

2. **GMV 异常巡检**
   - 按 cron 定时比对昨日和前一日的大盘 GMV
   - 一旦跌幅超过阈值，就自动补一版初步解释：
     - 区域线索
     - 订单结构线索
     - 品类线索

### 为什么 Week E 先做这两件事

因为前面 Week A-D 已经证明：

- 系统会不会分析
- 会不会多轮
- 会不会记经验
- 会不会做快路径/深路径

但还没有解决：

- 它什么时候该主动工作
- 工作完怎么发出去
- 主动动作会不会悄悄失控

所以 Week E 的第一步不是继续堆 Prompt，而是先补：

- `trigger`
- `reply`
- 最小校验和保护

### 这次最关键的框架取舍

我没有自己再造一套定时任务系统，而是沿框架原生的 `trigger + reply` 主链去做。

真正需要补的是一层 **业务触发执行器**：

- 让 trigger 执行时能复用现有 `GmvQueryTool / OrderQueryTool / UserMetricTool / RegionPerformanceQueryTool / CategoryRankTool`
- 同时也能复用 `send_notification`

这一步说明问题不在“能不能调度”，而在：

**框架默认 trigger 执行链能不能真正吃到业务 Tool。**

### 这轮踩到的两个真实坑

1. **日报校验跑得太早**
   - 原来 validator 在 bootstrap 之前跑，会假阴性地说 trigger 没注册
   - 后来改成 bootstrap 先跑、validator 再跑

2. **Olist 支线校验误伤 Week E 主线**
   - `OlistRawImportValidator` 默认去查 `raw_olist_orders`
   - 但这次烟测并没有启 Olist raw schema
   - 所以后来把它改成：只有 raw 表真的存在时才校验

### 验证结果

- `assistant-agent-start` 在 JDK 17 下编译通过
- 无 Web 启动烟测通过
- 启动日志里已经能看到：
  - `DailyReportTriggerBootstrap#run`
  - `GmvDropWatchTriggerBootstrap#run`
  - `DailyReportTriggerValidator#run`
  - `GmvDropWatchTriggerValidator#run`

### 一个月后只看这几句也能捡起来

- Week E 第一版解决的是“系统什么时候该主动出手”
- 第一条主动链是定时日报，第二条是 GMV 异常巡检
- 不是单独造调度系统，而是沿框架 `trigger + reply` 主链补业务执行器
- 主动链路一旦启动，就必须先补顺序校验和最小防误伤保护

## Day 30 - Week E 第二版：把定时日报收成可展示版本

### 这次做了什么

这次没有继续扩 trigger 数量，而是把第一条主动链 `daily_report` 从“能发一条通知”升级成了“可演示、可讲解、可作为产品交付”的版本。

日报现在不只是发三行数字，而是固定成三段：

1. **核心看板**
   - GMV
   - 支付订单量
   - 客单价
   - DAU
   - 活跃买家

2. **结构观察**
   - Top 区域
   - Top 品类

3. **系统备注**
   - 当前日报只做昨日经营概览
   - 异常检测和 root cause 由另一条巡检 trigger 负责
   - 带数据源说明，方便识别 demo / Olist 支线

### 为什么这一步重要

Week E 第一版已经证明系统会主动出手，但如果日报内容太像调试输出，演示时仍然会很弱。

所以这一步真正解决的是：

- 主动链不仅要“会触发”
- 还要“交付得像一个内部产品”

### 这次的框架理解

日报 trigger 的核心不在于让模型自由发挥，而在于：

- 用 `trigger` 决定什么时候启动
- 用现有业务 Tool 取稳定数据
- 用 `reply` 把结果送出去

也就是说，它更像一条**稳定任务编排链**，而不是一条开放式问答链。

### 这次补的保护

为了避免以后日报悄悄退化回“弱模板”，我还给 validator 多加了一层约束：

- 必须继续走 `send_notification`
- 必须继续带区域和品类摘要
- 必须继续保留日报标题

### 现在可以怎么讲这条链

> 到了 Week E，我先没有一上来做复杂异常系统，而是先把最稳定、最高频、最容易验证的主动动作做出来，也就是“定时日报”。  
> 它到点自动汇总昨日核心经营指标和结构观察，再通过通知通道推送出去。  
> 这一步让系统第一次从“会分析”变成“会按时交付分析结果”。

## Day 30.5 - Week E 后半段：把安全和鲁棒性变成可对外讲的运行规则

### 这次做了什么

这次没有继续加新 Trigger，而是把已经落地的保护层整理成两份可见规则：

- [SECURITY.md](SECURITY.md)
- [ROBUSTNESS.md](ROBUSTNESS.md)

同时把它们挂进了：

- [README.md](README.md)
- [README_zh.md](README_zh.md)

### 为什么现在要做这一步

如果这些保护只存在于代码里，那么：

- 对内很难复盘“系统为什么比较稳”
- 对外很难讲清“主动链路为什么不会轻易失控”

所以这一步不是写文档装饰，而是在把：

- 查询只读
- 工具白名单
- 防重复推送
- Trigger 超时 / 重试
- 多基线异常判断
- 启动格式校验

这些已经做好的东西，变成一套可以复述的运行规则。

### 现在这套规则最短怎么讲

- Agent 会主动出手，但默认只读
- Agent 会主动推送，但默认去重
- Agent 会异常巡检，但不是单一阈值乱报警
- Agent 会按时工作，但有最小执行边界和格式校验

### 这一步的价值

Week E 前半段解决的是：

> 系统会不会主动工作

Week E 后半段开始解决的是：

> 系统主动工作时，别人能不能相信它不会轻易闯祸

## Day 30.6 - Week E 收口：把降级、缓存和持久化幂等补成真正的运行层

### 这次做了什么

这次不是继续加新 Trigger，而是把 Week E 剩下最容易“线上出事”的三层缺口补上：

- **持久化幂等**
  - 新增 `notification_delivery_log`
  - 通知去重不再只靠内存，应用重启后也能避免短时间重复推送

- **系统化降级**
  - 飞书 webhook 没配、超时或返回非 2xx 时，不再直接让主动链路报错退出
  - 会降级到本地 IDE/log 通道，同时保留去重指纹和失败原因

- **查询缓存**
  - 给高频只读查询补了一层轻量 TTL cache
  - 重点保护日报、异常巡检和 root cause 里反复读取的大盘/区域/品类/订单结构数据

同时补了一条启动校验，确保：

- 持久化去重表真的存在
- webhook 超时配置是有效的
- 查询缓存开了以后 TTL 不是非法值

### 为什么这一步现在必须做

到了 Week E，系统已经不只是“你问我答”，而是会：

- 定时发日报
- 巡检异常
- 自动推送

这时候如果没有这三层补丁，最容易出现的不是“功能没有”，而是：

- 重启后同一条日报又发一遍
- 飞书没配好时整条主动链路直接失败
- 同一轮巡检里重复查相同指标，放大查询成本

所以这一步是在把主动链路从“能跑”推进到“更像能稳定跑”。

### 现在这套最短怎么讲

- 去重不只在内存里，而是开始有持久化指纹
- 通知通道失败时，不会把主动任务整条打断，而会先降级留痕
- 高频读链路有轻量缓存，减少重复查数

### 这一轮的边界

这版仍然是 **Week E 的最小可靠实现**，不是完整分布式方案：

- 幂等表现在是 H2/本地数据库级别，不是跨实例共享锁
- 降级现在是 `IDE_TEXT/log`，不是多通道容灾编排
- 缓存现在是进程内 TTL cache，不是外部缓存服务

但这三层已经足够让当前 demo 从“功能成立”进到“运行面更稳”。

### Day 30.7 - Week E Day 34 收口：把“部分完成”的运行层补实

这一轮继续补 Day 34 里还没有讲清楚的四件事：

- **缓存策略更明确**
  - 只缓存只读分析查询
  - 默认 TTL `120s`
  - 默认最大 `512` 条
  - 默认不缓存空结果，避免数据导入或 ads 重建期间把“暂时无数据”缓存住

- **root cause 支持部分降级**
  - 单个 Tool 失败时，不再默认整条 root cause 失败
  - 已完成证据继续返回
  - 失败维度写入 `degradations`
  - 返回 `degraded=true`，让页面、日报和异常巡检都能识别“这是一份不完整但可用的分析”

- **幂等状态更可观察**
  - 通知重复抑制会标明来自 `in_memory` 还是 `persistent_store`
  - 返回去重窗口，便于解释为什么这条通知没有重复发
  - 当前仍是“单共享数据库”口径，不夸大成分布式强一致

- **Prompt Injection 有入口拦截和审计**
  - 对“忽略之前规则 / 打印系统提示词 / 删表 / 绕过规则”等高风险输入先拒绝
  - 拒绝事件写入 `prompt_security_audit`
  - 审计表不可用时至少写应用日志

这一步的业务意义是：系统已经开始从“能主动跑”进入“主动跑时能解释自己的运行边界”。

---

## Day 31 - Week F 第一版：先把分析轨迹和 Trace 标签做成稳定结果结构

这次继续往 Week F 推的时候，我没有先做 UI，而是先补了一层更底的能力：**让每次分析结果都带上统一的 `analysis_trace` 和 `trace_tags`**。这样不管后面是给日志看、给 UI 展示，还是给异常推送复用，系统都能解释“这次分析到底是怎么走出来的”。

这轮接上的不是完整 OpenTelemetry / Jaeger，而是更适合当前阶段的最小结果结构：

- `analysis_trace`
  - 按步骤列出这次分析用了哪些 Tool
  - 每一步对应什么业务阶段
  - 这一阶段在判断什么
- `trace_tags`
  - 标出这次走的是 `fast` 还是 `deep`
  - 当前 intent / metric / region / category / stat_date
  - Tool 数量和分析深度

### Day 31.5 - 展示层打磨：让第一次打开页面的人先看懂业务链路

这次没有继续堆新 Tool，而是把页面从“功能面板”改成更像“演示故事线”。

页面现在先解释四步：

- 发现异常
- 拆解原因
- 分发责任
- 形成通知

同时把两个入口讲清楚：

- `运行分析`：适合展示人工提问下的标准问数 / root cause 问答
- `运行 GMV 异常巡检`：适合展示系统主动巡检，自动接上 root cause、责任分发和通知草稿

这一步的业务意义是：项目要让别人一眼看懂，不应该先把观众丢进 Tool Chain、Path Type 和 JSON 里。真正对外讲的时候，第一屏应该先回答：

> 这个 Agent 到底帮运营少做了哪几步？

现在页面的答案更清楚了：它不是只回答一句“GMV 跌了”，而是把“发现异常 -> 拆原因 -> 找负责人 -> 生成交付物”这条链直接展示出来。

### 为什么这一步现在值得做

之前我们已经有：

- `path_type`
- `tool_chain`
- `facts`

但这还不够支撑后续“分析轨迹展示”。因为 `tool_chain` 只能回答“用了哪些 Tool”，回答不了“这些 Tool 分别在做什么判断”。一旦要做 UI 里的分析轨迹、README 里的路径说明，或者要把异常推送写得更可解释，这层信息就会变得很关键。

所以这次我先把“分析轨迹”做成结果层资产，而不是等到接 UI 时再临时拼。

### 这次的工程取舍

我没有一上来就直接接完整链路追踪基础设施，而是先把最值钱的那部分落下来：

- 结果层先统一轨迹结构
- 启动校验守住这层结构不退化

现在启动时会额外检查：

- `analysis_trace` 的步数必须和 `tool_chain` 一致
- `trace_tags.analysis_path` 必须和 `path_type` 一致

这意味着后面如果有人改了回答链，只保留结论、不再带轨迹，这层退化会在启动期直接暴露出来，而不是等演示时才发现“为什么这次没有过程说明了”。

### 这一轮带来的新理解

这次的一个认知也很清楚：**可观测性最好先从“结果结构可解释”开始，而不是一上来就只盯着基础设施名词。** 对当前这个项目来说，先把“分析轨迹”做成系统资产，比先喊 Jaeger 更有价值。等这层结构稳定了，后面再把它接到真正的 trace/span 展示上，改动就会小很多。

### Day 39.5 - 端到端 demo smoke：把可展示链路变成可回归验证链路

这次没有继续扩业务支线，而是补了一个轻量端到端 smoke 脚本：

- `scripts/smoke_ecommerce_demo.py`
- 只依赖 Python 标准库
- 校验 `/api/ecommerce/runtime`
- 校验 `/api/ecommerce/answer`
- 校验 `/api/ecommerce/triggers/gmv-drop-watch/run-once`

它检查的不是“接口有没有 200”，而是当前项目最关键的业务交付物有没有完整返回：

- root cause 结构化结果
- 责任分发 `action_routing`
- 通知草稿 `notification_draft`
- 证据可信度 `evidence_confidence`
- 数据来源 `data_lineage`
- 多步 `tool_chain`

这一步的业务意义是：项目现在已经进入“可展示链路”阶段，不能只靠手点页面确认有没有跑通。后面不管是改前端、改 trigger、改 root cause builder，还是继续调整 Olist / demo 混合数据口径，都需要一条快速回归链路。

这次实际跑通结果：

```text
[OK] runtime rules: cache/degradation/idempotency/security are visible
[OK] answer root cause: elapsed=345ms, routes=5
[OK] trigger root cause: demo_report_date=2018-08-29, elapsed=557ms, routes=5
[PASS] ecommerce demo smoke completed
```

### 这一轮带来的新理解

之前页面已经能展示“发现异常 -> 归因 -> 责任分发 -> 通知草稿”，但页面展示更偏演示，不适合作为工程回归标准。smoke 脚本补上的这一层，是把演示能力变成可验证能力。

这也说明当前阶段的优先级应该从“继续堆功能”转向“守住主链”：

- 主动异常巡检必须能触发
- root cause 必须返回结构化结果
- 责任分发和通知草稿不能退化
- 数据来源和可信度必须继续可见

这条链稳住以后，再去做 Docker 部署实测、录 Demo、补简历讲解，底气会更足。

---

## 待探索

- [ ] GraalVM 沙箱的资源限制边界在哪里（CPU/内存/网络）
- [ ] `FastIntentService` 的匹配算法是什么
- [ ] 经验体系三种类型（COMMON/REACT/TOOL）的具体区别和使用场景
- [ ] 多渠道回复（reply 模块）的 SPI 扩展点怎么接
- [ ] 触发器（trigger 模块）和定时任务的实现细节
- [ ] 评估图的节点是怎么定义和串联的
- [ ] Management 模块的 SKILL 包格式是什么
- [ ] 如何自定义知识库数据源（search SPI）

---

*最后更新：2026-05-27*
