# Olist Import Runbook

## 这份 Runbook 是干嘛的

它解决的是：

- 真实 Olist CSV 到手后，应该怎么导入
- 先跑哪条命令
- 导入成功以后，应该看到哪些最小结果

也就是说，这不是数据设计文档，而是**实际操作入口**。

---

## 标准入口

在仓库根目录执行：

```bash
scripts/run_olist_pipeline.sh
```

默认会读取：

```text
assistant-agent-start/src/main/resources/demo-data/olist/raw/sample-csv
```

也就是当前仓库自带的极小样本。

如果后面你拿到真实 Olist CSV，可以把目录作为参数传进去：

```bash
scripts/run_olist_pipeline.sh /absolute/path/to/olist-csv-dir
```

或者：

```bash
scripts/run_olist_pipeline.sh data/olist-csv
```

---

## 这条命令实际做了什么

它会一次性完成下面几件事：

1. 切到仓库自带 JDK 17
2. 启动 `assistant-agent-start`
3. 创建 `raw_olist_*` 表
4. 把 CSV 导入 `raw_olist_*`
5. 构建：
   - `dwd_olist_*`
   - `dim_olist_*`
   - `ads_olist_*`
6. 跑一轮启动验证

所以这不是单纯“导 CSV”，而是：

**导 raw + 构 analytics + 做最小烟测**

---

## 成功时你应该看到什么

日志里至少应该出现这些信号：

### 1. raw 层建表

```text
olist raw schema initialized, rawTableCount=7
```

### 2. raw 导入完成

```text
raw olist csv import finished, rawOrderCount=...
```

### 3. raw 导入 summary

```text
OlistRawImportValidator#run - reason=raw olist import summary generated
```

这一步会同时带上：

- `rowCounts`
- `relationshipGaps`

你要重点看：

- `raw_olist_orders`
- `raw_olist_order_items`
- `raw_olist_payments`

是否有行数

以及：

- `orders_without_customer`
- `items_without_order`
- `items_without_product`
- `payments_without_order`

是否都是 `0`

### 4. analytics 构建完成

```text
olist analytics tables built, adsDailyRows=...
```

---

## 什么时候算“导得对”

不是只要表里有数据就算好。

至少满足这 3 条：

1. `raw_olist_orders / raw_olist_order_items / raw_olist_payments` 都有数据
2. `orders_without_customer = 0`
3. `items_without_order / items_without_product / payments_without_order = 0`

这代表：

- 订单能对上客户
- 商品明细能对上订单和商品
- 支付记录能对上订单

只有 raw 层关联关系没断，后面的 `dwd / dim / ads` 才有分析价值。

---

## 当前边界

这条入口现在解决的是：

- 公开数据能不能稳定进入系统
- `raw -> dwd -> dim -> ads` 能不能跑起来

它还没有解决的是：

- 全量真实 Olist CSV 的性能问题
- 更复杂的数据清洗
- 中国业务口径映射的完整覆盖

所以当前定位仍然是：

**最小可用公开数据接入链**

不是最终大规模生产方案。
