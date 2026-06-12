# Olist ADS (Phase 1)

这一层的目标是把 `dwd_olist_*` 先汇总成 **当前 Agent 最需要的高频快照**，让未来 Olist 支线可以逐步承接：

- `昨天 GMV 多少`
- `哪个区域更差`
- `品类排行怎么看`

## Phase 1 范围

- `ads_olist_daily_core_metrics`
  - 日级大盘 GMV / 支付订单量 / 活跃买家 / 客单价

- `ads_olist_region_daily`
  - 日级区域表现

- `ads_olist_category_daily`
  - 日级品类表现

## 设计原则

1. 先服务快路径，不急着把 root cause 全量迁移
2. 先按 Olist 自身可稳定得到的口径汇总
3. 统一从 `dwd_olist_*` 进入 ads，不直接跳 raw

## 与当前 Tool 主链的关系

- 当前 `GmvQueryTool / RegionPerformanceQueryTool / CategoryRankTool` 仍使用 demo 主链
- `ads_olist_*` 是未来把三条快路径迁到公开数据的桥头堡
