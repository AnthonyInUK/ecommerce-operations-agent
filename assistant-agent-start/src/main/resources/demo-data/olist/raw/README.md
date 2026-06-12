# Olist Raw Layer

这层目录是 Week 4 数据升级支线的入口，目标不是立刻替换当前手工 seed data，
而是先把 **Kaggle Olist 巴西电商公开数据集** 接到项目的 `raw` 层。

## 当前状态

- 已提供 `init_raw_olist_schema.sql`，用于创建第一版 `raw_olist_*` 表结构
- 已提供 ultra-light importer 骨架；只要把 CSV 放到约定目录并配置导入路径，启动时就能自动灌进 `raw_olist_*`
- 默认 **不自动启用**，避免打断当前 demo warehouse 主链
- 启用方式：
  - 在 `application.yml` 或运行参数中设置  
    `app.datasource.bootstrap-olist-raw-schema=true`
  - 如果要自动导入 CSV，再额外设置  
    `app.datasource.olist-raw-import-dir=/path/to/olist/csv`

## 预期数据文件

后续拿到公开数据后，建议把原始 CSV 保留在这个目录或其子目录中：

- `olist_orders_dataset.csv`
- `olist_order_items_dataset.csv`
- `olist_customers_dataset.csv`
- `olist_products_dataset.csv`
- `olist_order_payments_dataset.csv`
- `olist_order_reviews_dataset.csv`
- `olist_geolocation_dataset.csv`

## 设计原则

1. **先接 raw，不直接改当前 Agent 主链**
2. **尽量保留原始字段，便于追溯**
3. **后面再从 raw 加工到 dwd / dim / ads**
4. **优先迁 `GMV / 区域 / 品类` 三条快路径**

## 为什么要先做 raw 层

因为当前手工 seed data 适合打通 Agent 主链，
但后续如果想让项目更像真实数据产品，就需要一条公开数据的升级路径：

`public dataset -> raw -> dwd -> dim -> ads -> Agent tools`

这一层就是这条升级路径的起点。
