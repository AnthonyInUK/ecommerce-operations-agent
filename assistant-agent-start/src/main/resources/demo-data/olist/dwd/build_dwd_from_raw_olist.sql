-- ============================================================================
-- Olist Raw -> DWD (Phase 1)
-- ============================================================================
-- 目的：
-- 1. 先把 Olist 原始 CSV 对应的 raw_olist_* 表加工成可分析明细层
-- 2. 当前阶段不替换主链 dwd_orders / dwd_order_items
-- 3. 先生成并行表：dwd_olist_orders / dwd_olist_order_items
-- ============================================================================

TRUNCATE TABLE dwd_olist_orders;
TRUNCATE TABLE dwd_olist_order_items;

INSERT INTO dwd_olist_orders (
    order_id,
    customer_id,
    customer_unique_id,
    order_status,
    order_purchase_timestamp,
    order_approved_at,
    order_delivered_customer_date,
    paid_amount,
    payment_count,
    payment_type_primary,
    customer_city,
    customer_state,
    region_name_seed,
    source_tag
)
SELECT
    o.order_id,
    o.customer_id,
    c.customer_unique_id,
    o.order_status,
    o.order_purchase_timestamp,
    o.order_approved_at,
    o.order_delivered_customer_date,
    COALESCE(p.total_payment_value, 0) AS paid_amount,
    COALESCE(p.payment_count, 0) AS payment_count,
    p.payment_type_primary,
    c.customer_city,
    c.customer_state,
    c.customer_state AS region_name_seed,
    'olist_raw'
FROM raw_olist_orders o
LEFT JOIN raw_olist_customers c
       ON o.customer_id = c.customer_id
LEFT JOIN (
    SELECT
        order_id,
        SUM(payment_value) AS total_payment_value,
        COUNT(*) AS payment_count,
        MIN(payment_type) AS payment_type_primary
    FROM raw_olist_payments
    GROUP BY order_id
) p
       ON o.order_id = p.order_id;

INSERT INTO dwd_olist_order_items (
    order_id,
    order_item_id,
    product_id,
    seller_id,
    product_category_name,
    item_price,
    freight_value,
    item_gross_amount,
    source_tag
)
SELECT
    oi.order_id,
    oi.order_item_id,
    oi.product_id,
    oi.seller_id,
    pr.product_category_name,
    oi.price AS item_price,
    oi.freight_value,
    COALESCE(oi.price, 0) + COALESCE(oi.freight_value, 0) AS item_gross_amount,
    'olist_raw'
FROM raw_olist_order_items oi
LEFT JOIN raw_olist_products pr
       ON oi.product_id = pr.product_id;

-- ============================================================================
-- Phase 1 边界说明
-- ============================================================================
-- 1. 这里不做中文品类映射
-- 2. 这里不做华东/华南归并，只保留 state 作为 region_name_seed
-- 3. 这里不做 GMV / refund_rate / conversion_rate 聚合
-- 4. 这里不做支付成功 / 退款口径的最终业务定义
--
-- 这些动作统一留到下一步：
-- raw_olist_* -> dwd_olist_* -> dim_olist_* / ads_olist_*
-- ============================================================================
