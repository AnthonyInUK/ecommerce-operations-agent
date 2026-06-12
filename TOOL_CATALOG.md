# TOOL_CATALOG

这份目录不是按数据库表组织，而是按**业务分析动作**组织。  
目标是让后面做 demo、面试讲解、Prompt 设计和 Tool 扩展时，先站在“运营/分析师每天在做什么”这个角度看系统能力。

## Overview

| Tool | 解决的业务问题 | 典型输入 | 主要输出 | 当前路径 |
|---|---|---|---|---|
| `GmvQueryTool` | 昨天卖了多少，核心盘子怎么样 | `stat_date` | GMV、支付订单数、活跃买家、DAU、退款率 | 快路径 |
| `OrderQueryTool` | GMV 变化到底是订单量变了还是客单价变了 | `stat_date`、`region_name`、`category_l1` | 订单量、支付订单量、退款订单量、总支付金额、客单价 | 快路径 / 深路径 |
| `UserMetricTool` | 成交变化是不是先从用户规模或渠道质量开始 | `stat_date`、`region_name`、`view_type`、`limit` | DAU、活跃买家、买家激活率、渠道转化率 | 快路径 / 深路径 |
| `RegionPerformanceQueryTool` | 哪个区域更差，哪个大区需要先复盘 | `stat_date`、`region_name` | 区域 GMV、支付订单数、退款率 | 快路径 / 深路径 |
| `CategoryRankTool` | 哪些品类在拉动或拖累大盘 | `stat_date`、`region_name`、`limit` | 品类 GMV、支付订单数、退款率 | 快路径 / 深路径 |
| `FunnelAnalysisTool` | 问题是流量少了，还是转化掉了 | `stat_date`、`region_name`、`category_l1` | 浏览数、支付数、`view_to_pay_rate` | 深路径 |
| `RefundAnalysisTool` | 成交后是不是售后/退款在拖后腿 | `stat_date`、`region_name`、`limit` | 品类退款金额、退款金额占比、关联 GMV | 深路径 |

## Tool 设计原则

### 1. 不按表切，按业务动作切

系统没有做成 `OrdersTool`、`PaymentsTool`、`ProductsTool` 这种“开发者视角”工具，而是做成：

- `GmvQueryTool`
- `OrderQueryTool`
- `UserMetricTool`
- `RegionPerformanceQueryTool`
- `CategoryRankTool`
- `FunnelAnalysisTool`
- `RefundAnalysisTool`

这样模型调的不是“数据库表”，而是“分析动作”。

### 2. 先稳住高频问题，再扩复杂问题

当前已经稳定覆盖的高频问题：

- 昨天 GMV 多少
- 昨天订单量多少，客单价怎么样
- 昨天 DAU 和活跃买家多少
- 哪个渠道转化更高
- 华东和华南哪个区域表现更差
- 某天品类排行怎么看

在此基础上，`gmv_root_cause` 走一条标准深路径，把复杂问题模板化。

### 3. Tool 背后继续走稳定数据读取层

这些 Tool 不直接暴露原始表，而是统一走 `JdbcWarehouseQueryService`，让业务口径保持稳定。  
这也是为什么后面接 Olist 时，可以先迁数据层，再逐步迁 Tool，而不用推翻 Agent 主链。

## 当前主路径映射

### 快路径

- `昨天 GMV 多少？` -> `GmvQueryTool`
- `昨天订单量多少，客单价怎么样？` -> `OrderQueryTool`
- `昨天 DAU 和活跃买家多少？` -> `UserMetricTool`
- `哪个渠道转化更高？` -> `UserMetricTool`
- `华东和华南哪个区域表现更差？` -> `RegionPerformanceQueryTool`
- `品类排行怎么看？` -> `CategoryRankTool`

### 标准深路径

- `华东 GMV 为什么跌了？`

当前标准深路径会按下面顺序拉事实：

1. `RegionPerformanceQueryTool`
2. `OrderQueryTool`
3. `UserMetricTool`
4. `CategoryRankTool`
5. `FunnelAnalysisTool`
6. `RefundAnalysisTool`

这条链解决的不是“查一个数”，而是：

- 先确认异常
- 再判断用户规模有没有掉
- 再判断订单结构有没有掉
- 再看品类拖累
- 再区分流量/转化问题
- 最后校验售后影响

## 当前最值得继续扩的方向

1. 把这份目录逐步接到 README 和 demo 里，减少口头解释成本。
2. 让 `verified cases` 和 `evaluation summary` 继续按 Tool 覆盖度和路径类型统计。
3. 后续接 Olist 时，优先保证 `GmvQueryTool / OrderQueryTool / UserMetricTool / RegionPerformanceQueryTool / CategoryRankTool` 五条高频路径先迁稳。
