TRUNCATE TABLE ads_olist_daily_core_metrics;
TRUNCATE TABLE ads_olist_region_daily;
TRUNCATE TABLE ads_olist_category_daily;

INSERT INTO ads_olist_daily_core_metrics (
    stat_date,
    gmv,
    paid_order_count,
    active_buyer_count,
    avg_order_value,
    source_tag
)
SELECT
    CAST(order_purchase_timestamp AS DATE) AS stat_date,
    SUM(COALESCE(paid_amount, 0)) AS gmv,
    COUNT(DISTINCT CASE WHEN COALESCE(paid_amount, 0) > 0 THEN order_id END) AS paid_order_count,
    COUNT(DISTINCT CASE WHEN COALESCE(paid_amount, 0) > 0 THEN customer_unique_id END) AS active_buyer_count,
    CASE
        WHEN COUNT(DISTINCT CASE WHEN COALESCE(paid_amount, 0) > 0 THEN order_id END) = 0 THEN 0
        ELSE SUM(COALESCE(paid_amount, 0)) / COUNT(DISTINCT CASE WHEN COALESCE(paid_amount, 0) > 0 THEN order_id END)
    END AS avg_order_value,
    'olist_dwd'
FROM dwd_olist_orders
WHERE order_purchase_timestamp IS NOT NULL
GROUP BY CAST(order_purchase_timestamp AS DATE);

INSERT INTO ads_olist_region_daily (
    stat_date,
    region_name_seed,
    gmv,
    paid_order_count,
    active_buyer_count,
    avg_order_value,
    source_tag
)
SELECT
    CAST(o.order_purchase_timestamp AS DATE) AS stat_date,
    COALESCE(r.region_name_seed, o.region_name_seed, 'UNKNOWN') AS region_name_seed,
    SUM(COALESCE(o.paid_amount, 0)) AS gmv,
    COUNT(DISTINCT CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.order_id END) AS paid_order_count,
    COUNT(DISTINCT CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.customer_unique_id END) AS active_buyer_count,
    CASE
        WHEN COUNT(DISTINCT CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.order_id END) = 0 THEN 0
        ELSE SUM(COALESCE(o.paid_amount, 0)) / COUNT(DISTINCT CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.order_id END)
    END AS avg_order_value,
    'olist_dwd'
FROM dwd_olist_orders o
LEFT JOIN dim_olist_regions r
       ON o.customer_state = r.state_code
WHERE o.order_purchase_timestamp IS NOT NULL
GROUP BY
    CAST(o.order_purchase_timestamp AS DATE),
    COALESCE(r.region_name_seed, o.region_name_seed, 'UNKNOWN');

INSERT INTO ads_olist_category_daily (
    stat_date,
    category_l1_seed,
    gmv,
    order_item_count,
    paid_order_count,
    source_tag
)
SELECT
    CAST(o.order_purchase_timestamp AS DATE) AS stat_date,
    COALESCE(p.category_l1_seed, i.product_category_name, 'UNKNOWN') AS category_l1_seed,
    SUM(COALESCE(i.item_price, 0)) AS gmv,
    COUNT(*) AS order_item_count,
    COUNT(DISTINCT CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN i.order_id END) AS paid_order_count,
    'olist_dwd'
FROM dwd_olist_order_items i
JOIN dwd_olist_orders o
  ON i.order_id = o.order_id
LEFT JOIN dim_olist_products p
  ON i.product_id = p.product_id
WHERE o.order_purchase_timestamp IS NOT NULL
GROUP BY
    CAST(o.order_purchase_timestamp AS DATE),
    COALESCE(p.category_l1_seed, i.product_category_name, 'UNKNOWN');
