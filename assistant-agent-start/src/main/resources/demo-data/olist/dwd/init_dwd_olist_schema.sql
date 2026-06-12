CREATE TABLE IF NOT EXISTS dwd_olist_orders (
    order_id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64),
    customer_unique_id VARCHAR(64),
    order_status VARCHAR(32),
    order_purchase_timestamp TIMESTAMP,
    order_approved_at TIMESTAMP,
    order_delivered_customer_date TIMESTAMP,
    paid_amount DECIMAL(18,2),
    payment_count INT,
    payment_type_primary VARCHAR(64),
    customer_city VARCHAR(128),
    customer_state VARCHAR(32),
    region_name_seed VARCHAR(64),
    source_tag VARCHAR(32) DEFAULT 'olist_raw'
);

CREATE TABLE IF NOT EXISTS dwd_olist_order_items (
    order_id VARCHAR(64) NOT NULL,
    order_item_id INT NOT NULL,
    product_id VARCHAR(64),
    seller_id VARCHAR(64),
    product_category_name VARCHAR(128),
    item_price DECIMAL(18,2),
    freight_value DECIMAL(18,2),
    item_gross_amount DECIMAL(18,2),
    source_tag VARCHAR(32) DEFAULT 'olist_raw',
    PRIMARY KEY (order_id, order_item_id)
);
