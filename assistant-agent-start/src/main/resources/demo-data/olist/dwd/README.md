# Olist DWD Layer (Phase 1)

## 这一层现在是干嘛的

这一层不是要立刻替换当前主链里的 `dwd_orders / dwd_order_items`，
而是先把 **Olist 公开数据从 raw 加工到可分析明细层** 的第一版规则固定下来。

为了不打断当前手工 seed data 主链，这里先使用：

- `dwd_olist_orders`
- `dwd_olist_order_items`

等后面公开数据真正导入并验证稳定后，再决定如何迁到项目主链里的标准 `dwd_orders / dwd_order_items`。

## Phase 1 的目标

只解决 3 个最值钱的问题：

1. 订单主表怎么统一成可分析口径  
2. 订单商品明细怎么和商品维度挂起来  
3. 后面 `GMV / 区域 / 品类` 三条快路径从哪儿接公开数据

## 为什么不直接覆盖当前 dwd 表

因为当前项目已经有一条稳定的演示主链：

- 高频看数
- 多轮追问
- root cause 深路径
- verified case 回归

如果现在直接用 Olist 的半成品 dwd 替换掉它，很容易让主链不稳。

所以 Phase 1 的原则是：

**先并行加工，再逐步迁快路径。**

## 当前设计重点

### dwd_olist_orders

这一层负责把：

- `raw_olist_orders`
- `raw_olist_payments`
- `raw_olist_customers`

合成一张“订单分析主事实表”。

重点字段包括：

- `order_id`
- `customer_id`
- `customer_unique_id`
- `order_status`
- `order_purchase_timestamp`
- `paid_amount`
- `payment_count`
- `payment_type_primary`
- `customer_city`
- `customer_state`
- `region_name_seed`

其中 `region_name_seed` 暂时只保留州或城市来源，不在这一步硬映射成“华东/华南”。

### dwd_olist_order_items

这一层负责把：

- `raw_olist_order_items`
- `raw_olist_products`

合成“商品粒度的订单事实表”。

重点字段包括：

- `order_id`
- `order_item_id`
- `product_id`
- `seller_id`
- `product_category_name`
- `item_price`
- `freight_value`
- `item_gross_amount`

## 这一步完成后意味着什么

这意味着后面可以很自然地继续做：

1. `dwd_olist_orders -> ads_olist_daily_core_metrics`
2. `dwd_olist_orders + dim_regions -> ads_olist_region_daily`
3. `dwd_olist_order_items -> ads_olist_category_daily`

也就是为：

- `GmvQueryTool`
- `RegionPerformanceQueryTool`
- `CategoryRankTool`

三条快路径的公开数据迁移打底。
