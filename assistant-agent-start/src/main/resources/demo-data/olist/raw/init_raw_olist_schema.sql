CREATE TABLE IF NOT EXISTS raw_olist_orders (
    order_id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64),
    order_status VARCHAR(32),
    order_purchase_timestamp TIMESTAMP,
    order_approved_at TIMESTAMP,
    order_delivered_carrier_date TIMESTAMP,
    order_delivered_customer_date TIMESTAMP,
    order_estimated_delivery_date TIMESTAMP
);

CREATE TABLE IF NOT EXISTS raw_olist_order_items (
    order_id VARCHAR(64) NOT NULL,
    order_item_id INT NOT NULL,
    product_id VARCHAR(64),
    seller_id VARCHAR(64),
    shipping_limit_date TIMESTAMP,
    price DECIMAL(18,2),
    freight_value DECIMAL(18,2),
    PRIMARY KEY (order_id, order_item_id)
);

CREATE TABLE IF NOT EXISTS raw_olist_customers (
    customer_id VARCHAR(64) PRIMARY KEY,
    customer_unique_id VARCHAR(64),
    customer_zip_code_prefix VARCHAR(16),
    customer_city VARCHAR(128),
    customer_state VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS raw_olist_products (
    product_id VARCHAR(64) PRIMARY KEY,
    product_category_name VARCHAR(128),
    product_name_length INT,
    product_description_length INT,
    product_photos_qty INT,
    product_weight_g INT,
    product_length_cm INT,
    product_height_cm INT,
    product_width_cm INT
);

CREATE TABLE IF NOT EXISTS raw_olist_payments (
    order_id VARCHAR(64) NOT NULL,
    payment_sequential INT NOT NULL,
    payment_type VARCHAR(64),
    payment_installments INT,
    payment_value DECIMAL(18,2),
    PRIMARY KEY (order_id, payment_sequential)
);

CREATE TABLE IF NOT EXISTS raw_olist_reviews (
    review_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    review_score INT,
    review_comment_title VARCHAR(255),
    review_comment_message CLOB,
    review_creation_date TIMESTAMP,
    review_answer_timestamp TIMESTAMP,
    PRIMARY KEY (review_id, order_id)
);

CREATE TABLE IF NOT EXISTS raw_olist_geolocation (
    geolocation_zip_code_prefix VARCHAR(16),
    geolocation_lat DECIMAL(12,8),
    geolocation_lng DECIMAL(12,8),
    geolocation_city VARCHAR(128),
    geolocation_state VARCHAR(32)
);
