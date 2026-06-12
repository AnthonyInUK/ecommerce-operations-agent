# Olist Raw Import Plan

## 这份文件是干嘛的

它不是导入脚本，而是 **导入契约**。

作用是先把这 4 件事固化下来：

1. 未来 Olist CSV 应该按什么顺序进入系统
2. 每张 CSV 对应哪张 `raw_olist_*` 表
3. raw 层哪些字段先原样保留，哪些加工留到 dwd/dim/ads
4. 后面做 `dwd_orders / dwd_order_items` 时，应该从哪里取关键字段

## 当前阶段

当前项目仍然依赖手工 seed data 跑主链：

- 高频看数
- 多轮追问
- root cause 深路径
- verified case 回归

所以这条 Olist 支线当前的目标不是“替换主链”，而是：

**先把公开数据的 raw 层入口标准化。**

## 导入顺序为什么这样定

### 1. 先 customers / products

因为它们是后面 orders 和 order_items 的基础维度来源：

- customers 解决用户、城市、州
- products 解决 product_id 和品类原始字段

### 2. 再 orders

订单主表是后面 payment、review、order_items 的关联核心。

### 3. 再 order_items / payments / reviews

这三张表都依赖 `order_id`：

- order_items 提供商品粒度和价格结构
- payments 提供支付金额口径
- reviews 提供体验和售后补充信息

### 4. geolocation 独立保留

它更多是区域补充映射来源，不必强耦合到第一轮主链导入。

## 为什么 raw 层不提前做业务改写

因为 raw 层的职责是：

- 保留公开数据原始字段
- 保证可追溯
- 给后面的 dwd/dim/ads 留出稳定加工起点

所以这些事情不在 raw 层做：

- 中文品类映射
- 华东/华南归并
- 支付成功口径统一
- GMV / 退款 / ROI 等业务指标计算

这些都应该放到后面的 dwd / dim / ads。

## 后面第一轮最值得推进的加工链

等公开 CSV 真进来以后，最自然的下一步是：

1. `raw_olist_orders + raw_olist_payments -> dwd_orders`
2. `raw_olist_order_items + raw_olist_products -> dwd_order_items`
3. `raw_olist_customers + raw_olist_geolocation -> dim_regions`
4. `raw_olist_products -> dim_products / dim_categories`

然后优先迁这三条快路径：

- `GmvQueryTool`
- `RegionPerformanceQueryTool`
- `CategoryRankTool`

## 这一步的业务意义

这一步不是“已经接好了公开数据”，而是：

**把以后接公开数据时最容易混乱的部分先定下来。**

这样后面拿到 Olist CSV 时，我们不会一边导数据一边现场改设计，
而是沿既定契约推进：

`CSV -> raw_olist_* -> dwd/dim -> ads -> Agent tools`
