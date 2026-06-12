CREATE TABLE IF NOT EXISTS raw_orders (
    order_id VARCHAR(64) PRIMARY KEY,
    buyer_id VARCHAR(64) NOT NULL,
    city_name VARCHAR(64) NOT NULL,
    province_name VARCHAR(64) NOT NULL,
    category_l1 VARCHAR(64) NOT NULL,
    merchant_name VARCHAR(64) NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    campaign_name VARCHAR(64),
    pay_amount DECIMAL(18,2) NOT NULL,
    refund_amount DECIMAL(18,2) DEFAULT 0,
    order_status VARCHAR(32) NOT NULL,
    paid_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS dwd_orders (
    order_id VARCHAR(64) PRIMARY KEY,
    buyer_id VARCHAR(64) NOT NULL,
    region_name VARCHAR(32) NOT NULL,
    city_name VARCHAR(64) NOT NULL,
    category_l1 VARCHAR(64) NOT NULL,
    merchant_name VARCHAR(64) NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    campaign_name VARCHAR(64),
    pay_amount DECIMAL(18,2) NOT NULL,
    refund_amount DECIMAL(18,2) DEFAULT 0,
    order_status VARCHAR(32) NOT NULL,
    paid_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS dwd_user_events (
    event_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    region_name VARCHAR(32) NOT NULL,
    city_name VARCHAR(64) NOT NULL,
    category_l1 VARCHAR(64),
    channel_code VARCHAR(64),
    campaign_name VARCHAR(64),
    event_time TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS dwd_refunds (
    refund_id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    category_l1 VARCHAR(64) NOT NULL,
    merchant_name VARCHAR(64) NOT NULL,
    region_name VARCHAR(32) NOT NULL,
    refund_reason VARCHAR(128) NOT NULL,
    refund_amount DECIMAL(18,2) NOT NULL,
    refund_created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS ads_daily_core_metrics (
    stat_date DATE PRIMARY KEY,
    gmv DECIMAL(18,2) NOT NULL,
    paid_order_count INT NOT NULL,
    active_buyer_count INT NOT NULL,
    dau INT NOT NULL,
    refund_rate DECIMAL(8,4) NOT NULL
);

CREATE TABLE IF NOT EXISTS ads_region_daily (
    stat_date DATE NOT NULL,
    region_name VARCHAR(32) NOT NULL,
    gmv DECIMAL(18,2) NOT NULL,
    paid_order_count INT NOT NULL,
    refund_rate DECIMAL(8,4) NOT NULL,
    PRIMARY KEY (stat_date, region_name)
);

CREATE TABLE IF NOT EXISTS ads_category_daily (
    stat_date DATE NOT NULL,
    category_l1 VARCHAR(64) NOT NULL,
    gmv DECIMAL(18,2) NOT NULL,
    paid_order_count INT NOT NULL,
    refund_rate DECIMAL(8,4) NOT NULL,
    PRIMARY KEY (stat_date, category_l1)
);

CREATE TABLE IF NOT EXISTS ads_channel_daily (
    stat_date DATE NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    dau INT NOT NULL,
    active_buyer_count INT NOT NULL,
    conversion_rate DECIMAL(8,4) NOT NULL,
    PRIMARY KEY (stat_date, channel_code)
);

CREATE TABLE IF NOT EXISTS ads_refund_daily (
    stat_date DATE NOT NULL,
    category_l1 VARCHAR(64) NOT NULL,
    merchant_name VARCHAR(64) NOT NULL,
    refund_rate DECIMAL(8,4) NOT NULL,
    PRIMARY KEY (stat_date, category_l1, merchant_name)
);

CREATE TABLE IF NOT EXISTS ads_campaign_daily (
    stat_date DATE NOT NULL,
    campaign_name VARCHAR(64) NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    gmv DECIMAL(18,2) NOT NULL,
    conversion_rate DECIMAL(8,4) NOT NULL,
    roi DECIMAL(8,4) NOT NULL,
    PRIMARY KEY (stat_date, campaign_name, channel_code)
);

CREATE TABLE IF NOT EXISTS ads_business_evidence_daily (
    stat_date DATE NOT NULL,
    region_name VARCHAR(32) NOT NULL,
    category_l1 VARCHAR(64) NOT NULL,
    evidence_domain VARCHAR(64) NOT NULL,
    product_id VARCHAR(128),
    product_name VARCHAR(128),
    seller_id VARCHAR(128),
    seller_name VARCHAR(128),
    evidence_signal VARCHAR(256) NOT NULL,
    previous_metric VARCHAR(128),
    current_metric VARCHAR(128),
    impact_amount DECIMAL(18,2) DEFAULT 0,
    suggested_owner VARCHAR(64) NOT NULL,
    source_tag VARCHAR(64) NOT NULL,
    PRIMARY KEY (stat_date, region_name, category_l1, evidence_domain, product_id, seller_id)
);
