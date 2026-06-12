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

ALTER TABLE ads_business_evidence_daily ADD COLUMN IF NOT EXISTS previous_metric VARCHAR(128);
ALTER TABLE ads_business_evidence_daily ADD COLUMN IF NOT EXISTS current_metric VARCHAR(128);

MERGE INTO ads_business_evidence_daily (
    stat_date, region_name, category_l1, evidence_domain,
    product_id, product_name, seller_id, seller_name,
    evidence_signal, previous_metric, current_metric, impact_amount,
    suggested_owner, source_tag
) KEY (stat_date, region_name, category_l1, evidence_domain, product_id, seller_id)
VALUES
(DATE '2018-08-29', '华东', '家居', 'product_seller',
 'home-bed-001', '北欧实木床', 'seller-home-001', '宜居生活馆',
 '核心商品从重点坑位下架，导致家居品类当天无成交', '前一日 GMV 1711.96', '当日 GMV 0.00', -1711.96,
 '类目运营', 'demo_seed'),
(DATE '2018-08-29', '华东', '家居', 'inventory_fulfillment',
 'home-bed-001', '北欧实木床', 'seller-home-001', '宜居生活馆',
 '可售库存归零且承诺发货时效从 24h 拉长到 72h，影响继续承接订单', '库存 86 / 发货 24h', '库存 0 / 发货 72h', -920.00,
 '商家运营', 'demo_seed'),
(DATE '2018-08-29', '华东', '家居', 'marketing_campaign',
 'home-bed-001', '北欧实木床', 'seller-home-001', '宜居生活馆',
 '家装换新活动曝光下降，推荐坑位资源切走，流量无法补回商品下架影响', '曝光 12000 / 坑位 A', '曝光 4200 / 无坑位', -520.00,
 '增长运营', 'demo_seed'),
(DATE '2018-08-29', '华东', '家电', 'after_sales_refund',
 'appliance-001', '智能空气炸锅', 'seller-appliance-001', '优家电器',
 '退款集中在商品质量和物流破损，售后压力不构成第一主因但需要治理跟进', '退款 0', '退款 60.00', -60.00,
 '售后治理', 'demo_seed');
