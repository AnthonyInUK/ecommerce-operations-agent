# 电商业务痛点 -> 框架能力映射

## 为什么要有这份映射

这个项目不是“把框架功能都用一遍”，而是要回答一个更业务的问题：

- 阿里、京东、拼多多这类平台内部分析团队，到底卡在哪里
- 我们现在这个 Agent，分别用框架里的哪些能力去解决这些卡点

这份文档的目标，就是把：

- 业务痛点
- 产品能力
- 框架能力
- 当前项目资产

串成一条清晰链路。

---

## 1. 阿里式痛点：工具很多，但业务到分析结论的链路还是慢

### 业务表现

- 业务能在 BI 看板里看到现象
- 但解释“为什么跌了”仍然要找分析师
- 跨团队口径不一致，沟通成本高
- 经验和模板很多，但不一定在正确时机进入模型上下文

### 我们项目对应的产品能力

- 统一指标词典和语义模型
- 高频问题直接命中标准经验
- 复杂问题按固定归因套路继续往下走
- 日报/周报/异常提醒输出结构统一

### 对应的框架能力

- Experience
- PromptContributor
- semantic model
- report templates

### 当前项目里的落地点

- `metric_dictionary.yaml`
- `semantic_model.yaml`
- `report_templates.yaml`
- `exp-ecom-metric-dictionary`
- `exp-ecom-semantic-model`

---

## 2. 京东式痛点：标准问数已经很强，但复杂归因仍受 SQL 路线限制

### 业务表现

- “去年比前年涨多少”这类问题适合自助问数
- 但“为什么跌了”往往需要多步拆解
- 需要先看区域，再看品类，再看漏斗，再看退款
- 如果每次都从零规划，复杂分析不稳也不快

### 我们项目对应的产品能力

- Text2Code 路线支持多步分析
- root cause 问题沉淀成固定 REACT plan
- 高价值复杂问题不依赖 LLM 每次临场发挥

### 对应的框架能力

- CodeactTool
- REACT Experience
- FastIntent
- ToolRegistryBridge / callTrace

### 当前项目里的落地点

- `exp-ecom-fast-root-cause-east-drop`
- `GmvQueryTool`
- `RegionPerformanceQueryTool`
- `CategoryRankTool`
- `FunnelAnalysisTool`
- `RefundAnalysisTool`

---

## 3. 拼多多式痛点：重复分析太多，人效压力高

### 业务表现

- 高频报数和日报周报消耗大量人力
- 很多问题每天都在重复问
- 好分析套路长在人身上，不长在系统里
- 失败经验如果不回流，系统就永远停在“会答一次”

### 我们项目对应的产品能力

- verified cases 沉淀高频标准问题
- FastIntent 分流高频问题
- bad case 回流
- evaluation summary 形成自我改进闭环

### 对应的框架能力

- Experience
- FastIntent
- state
- evaluation / prompt contribution

### 当前项目里的落地点

- `verified_cases.yaml`
- `bad_cases.yaml`
- `evaluation_summary.md`
- `exp-ecom-verified-cases`
- `exp-ecom-evaluation-loop`

---

## 4. 这份映射对当前项目的意义

这意味着我们不是在做“一个会聊天的数据 Agent”，而是在做：

1. 对高频标准问题会直接命中熟路的系统
2. 对复杂归因问题会按固定套路往下拆的系统
3. 会把成功经验沉淀成标准路径、把失败问题沉淀成修正线索的系统

也就是：

**把“查数 -> 下钻 -> 归因 -> 报告 -> 预警”这条业务工作流，产品化成框架内的可复用能力。**
