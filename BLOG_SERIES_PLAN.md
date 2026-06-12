# AssistantAgent 电商分析 Agent 系列博客计划

## 说明

这份文件用来收拢当前项目最适合拆成博客的主题。目标不是机械按 Day/Week 写开发日记，而是把真正有独立价值的问题、能力和取舍拆成一组可以持续发布的技术博客。

建议写作顺序：

1. 先写最能立住项目定位的总述和路线选择
2. 再写最有技术辨识度的标准深路径、会话理解、Experience/FastIntent
3. 最后写数据迁移、主动触发和项目总复盘

---

## 系列 01

### 标题
AssistantAgent 电商分析 Agent 系列 01：从问答 Demo 到最小业务闭环

### 这篇解决什么问题
- 为什么要做这个项目
- 为什么不满足于聊天问答或智能问数
- 为什么是电商运营分析场景
- 最小业务闭环是怎么搭起来的

### 核心内容
- 业务角色和痛点
- 竞品路线观察
- 为什么选 Text-to-Code
- Tool 化 + 标准快路径 + 标准深路径
- 从 Demo 到产品雏形的主线

---

## 系列 02

### 标题
AssistantAgent 电商分析 Agent 系列 02：多轮追问为什么不能只靠聊天记录

### 这篇解决什么问题
- 为什么多轮对话不能只保存聊天记录
- 为什么业务追问本质上是在继承指标、时间、区域、品类和意图
- 为什么歧义澄清后必须继续回答，而不是只会反问

### 核心内容
- 会话状态
- 时间语义
- `metric_dictionary.yaml`
- `semantic_model.yaml`
- 澄清后继续回答

---

## 系列 03

### 标题
AssistantAgent 电商分析 Agent 系列 03：怎么把“为什么跌了”做成一条标准归因链

### 这篇解决什么问题
- 为什么复杂问题不能一开始完全开放规划
- 为什么“为什么跌了”应该先收成标准深路径
- root cause 为什么是业务型 Agent 从问数走向分析闭环的关键

### 核心内容
- `GMV = 支付订单量 × 客单价`
- `gmv_root_cause` 链路
- 区域 -> 订单结构 -> 用户规模 -> 品类 -> 漏斗 -> 退款
- 为什么标准深路径比开放规划更稳
- 深路径如何进入评测、异常巡检和责任分发

---

## 系列 04

### 标题
AssistantAgent 电商分析 Agent 系列 04：为什么业务型 Agent 的 Tool 不该按表切，而该按分析动作切

### 这篇解决什么问题
- 为什么 Tool 设计决定后面系统稳不稳
- 为什么不是 OrdersTool / PaymentsTool
- 为什么是 GmvQueryTool / CategoryRankTool / RefundAnalysisTool

### 核心内容
- 按表切和按业务动作切的区别
- 对稳定性、可复用性、评测的影响
- 标准快路径如何建立在 Tool 设计之上

---

## 系列 05

### 标题
AssistantAgent 电商分析 Agent 系列 05：为什么电商运营分析更适合 Text-to-Code，而不是纯 NL2SQL

### 这篇解决什么问题
- 为什么纯聊天不够
- 为什么纯 NL2SQL 不够
- 为什么这类问题天然需要执行链

### 核心内容
- 查数、对比、下钻、归因、汇总、通知
- SQL 正确率和语义口径问题
- 多步任务为什么不适合单轮问数
- Text-to-Code 如何更自然承接分析链

---

## 系列 06

### 标题
AssistantAgent 电商分析 Agent 系列 06：Experience、FastIntent 和标准问题快路径怎么做

### 这篇解决什么问题
- 为什么高频问题不该每次从零推理
- Experience 和 FastIntent 分别在解决什么
- 为什么评测不能只看成功率

### 核心内容
- Experience 怎么进入 prompt
- FastIntent 怎么分流
- `verified cases`
- `bad cases`
- `runtime bad case snapshot`

---

## 系列 07

### 标题
AssistantAgent 电商分析 Agent 系列 07：为什么公开电商数据不能一口气全迁：从 Olist 到混合数据 root cause

### 这篇解决什么问题
- 为什么要接公开数据
- 为什么不能一次性全切
- 为什么数据迁移必须围绕业务口径而不是只看导入成功

### 核心内容
- `raw -> dwd -> dim -> ads`
- Olist 的价值和边界
- 为什么先迁 GMV/区域/品类/订单结构
- 为什么用户/漏斗/退款先不迁
- 什么叫混合数据 root cause

---

## 系列 08

### 标题
AssistantAgent 电商分析 Agent 系列 08：从被动问答到主动日报和异常巡检，业务型 Agent 还差什么

### 这篇解决什么问题
- 为什么光会分析还不够
- 为什么主动 Agent 必须补运行层保护
- 为什么多基线异常判断比固定阈值更稳

### 核心内容
- trigger
- 定时日报
- 异常巡检
- 多基线判断
- 去重、幂等、只读、缓存、降级

---

## 可选进阶篇

### 09. 框架理解篇
标题：我怎么读懂 AssistantAgent：从代码执行链到工具注入链

### 10. 评测篇
标题：为什么业务型 Agent 不能只看命中率：从 bad case 到修复资产池

### 11. 部署篇
标题：为什么我没有选 SaaS Agent，而是做 Spring Boot 私有化部署

### 12. 总复盘篇
标题：做完这个项目后，我对业务型 Agent 最大的 5 个认知变化

---

## 建议发布顺序

### 第一阶段：先立住主线
1. 系列 01：从问答 Demo 到最小业务闭环
2. 系列 02：多轮追问为什么不能只靠聊天记录
3. 系列 03：怎么把“为什么跌了”做成一条标准归因链

### 第二阶段：补技术辨识度
4. 系列 05：为什么电商运营分析更适合 Text-to-Code，而不是纯 NL2SQL
5. 系列 06：Experience、FastIntent 和标准问题快路径怎么做
6. 系列 07：为什么公开电商数据不能一口气全迁

### 第三阶段：补产品和工程收口
7. 系列 08：从被动问答到主动日报和异常巡检，业务型 Agent 还差什么
8. 系列 12：做完这个项目后，我对业务型 Agent 最大的 5 个认知变化

---

## 当前状态

- 已有第一篇草稿：
  - [BLOG_assistantagent_ecommerce_agent_v1.md](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/BLOG_assistantagent_ecommerce_agent_v1.md)
- 当前最适合继续写的下一篇：
  - [BLOG_assistantagent_tool_design_v1.md](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/BLOG_assistantagent_tool_design_v1.md)
