# Olist DIM (Phase 1)

这一层的目标不是一次做完整业务语义，而是先把 Olist 公开数据补成 **当前 Agent 快路径能消费的最小维表**。

## Phase 1 范围

- `dim_olist_regions`
  - 用巴西州 `customer_state` 先生成稳定区域种子
  - 暂时不强行映射成“华东/华南”这类中国语义，只保留可继续归并的 seed

- `dim_olist_categories`
  - 把 Olist 原始英文品类名整理成可复用分类种子
  - 先不做正式中文业务映射，只保留 `category_name_cn_seed`

- `dim_olist_products`
  - 给后续品类排行和商品结构分析准备稳定产品维度

## 设计原则

1. 先让区域和品类有稳定“业务标签入口”
2. 维表优先服务现有快路径：`GMV / 区域 / 品类`
3. 中文映射和中国电商口径后续再补，不在第一版强做

## 与当前主链的关系

- 当前 Agent 主链仍继续使用手工 seed data
- `dim_olist_*` 是公开数据升级支线，为后续迁移三条快路径做准备
