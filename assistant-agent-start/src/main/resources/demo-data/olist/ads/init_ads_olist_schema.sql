CREATE TABLE IF NOT EXISTS ads_olist_daily_core_metrics (
    stat_date DATE PRIMARY KEY,
    gmv DECIMAL(18,2),
    paid_order_count INT,
    active_buyer_count INT,
    avg_order_value DECIMAL(18,2),
    source_tag VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS ads_olist_region_daily (
    stat_date DATE NOT NULL,
    region_name_seed VARCHAR(64) NOT NULL,
    gmv DECIMAL(18,2),
    paid_order_count INT,
    active_buyer_count INT,
    avg_order_value DECIMAL(18,2),
    source_tag VARCHAR(32),
    PRIMARY KEY (stat_date, region_name_seed)
);

CREATE TABLE IF NOT EXISTS ads_olist_category_daily (
    stat_date DATE NOT NULL,
    category_l1_seed VARCHAR(64) NOT NULL,
    gmv DECIMAL(18,2),
    order_item_count INT,
    paid_order_count INT,
    source_tag VARCHAR(32),
    PRIMARY KEY (stat_date, category_l1_seed)
);
