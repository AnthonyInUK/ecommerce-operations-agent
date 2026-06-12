CREATE TABLE IF NOT EXISTS dim_olist_regions (
    state_code VARCHAR(32) PRIMARY KEY,
    region_name_seed VARCHAR(64),
    macro_region_group VARCHAR(64),
    source_tag VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS dim_olist_categories (
    product_category_name_raw VARCHAR(128) PRIMARY KEY,
    category_l1_seed VARCHAR(64),
    category_name_cn_seed VARCHAR(128),
    source_tag VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS dim_olist_products (
    product_id VARCHAR(64) PRIMARY KEY,
    product_category_name_raw VARCHAR(128),
    category_l1_seed VARCHAR(64),
    weight_band_seed VARCHAR(64),
    source_tag VARCHAR(32)
);
