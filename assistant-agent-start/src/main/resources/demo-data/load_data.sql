INSERT INTO raw_orders (order_id, buyer_id, city_name, province_name, category_l1, merchant_name, channel_code, campaign_name, pay_amount, refund_amount, order_status, paid_at) VALUES
('o1001', 'u001', '上海', '上海', '女装', '衣尚旗舰店', 'organic', '520大促', 320.00, 0.00, 'PAID', TIMESTAMP '2026-05-16 10:05:00'),
('o1002', 'u002', '杭州', '浙江', '家电', '优家电器', 'ad_feed', '520大促', 980.00, 0.00, 'PAID', TIMESTAMP '2026-05-16 11:30:00'),
('o1003', 'u003', '广州', '广东', '美妆', '美颜工坊', 'organic', '日常投放', 210.00, 0.00, 'PAID', TIMESTAMP '2026-05-16 13:20:00'),
('o1004', 'u004', '上海', '上海', '女装', '衣尚旗舰店', 'ad_feed', '520大促', 450.00, 80.00, 'COMPLETED', TIMESTAMP '2026-05-17 09:15:00'),
('o1005', 'u005', '南京', '江苏', '家电', '优家电器', 'organic', '日常投放', 760.00, 120.00, 'COMPLETED', TIMESTAMP '2026-05-17 14:50:00'),
('o1006', 'u006', '深圳', '广东', '美妆', '美颜工坊', 'live_stream', '达人专场', 180.00, 0.00, 'PAID', TIMESTAMP '2026-05-17 19:25:00');

INSERT INTO dwd_orders (order_id, buyer_id, region_name, city_name, category_l1, merchant_name, channel_code, campaign_name, pay_amount, refund_amount, order_status, paid_at) VALUES
('o1001', 'u001', '华东', '上海', '女装', '衣尚旗舰店', 'organic', '520大促', 320.00, 0.00, 'PAID', TIMESTAMP '2026-05-16 10:05:00'),
('o1002', 'u002', '华东', '杭州', '家电', '优家电器', 'ad_feed', '520大促', 980.00, 0.00, 'PAID', TIMESTAMP '2026-05-16 11:30:00'),
('o1003', 'u003', '华南', '广州', '美妆', '美颜工坊', 'organic', '日常投放', 210.00, 0.00, 'PAID', TIMESTAMP '2026-05-16 13:20:00'),
('o1004', 'u004', '华东', '上海', '女装', '衣尚旗舰店', 'ad_feed', '520大促', 450.00, 80.00, 'COMPLETED', TIMESTAMP '2026-05-17 09:15:00'),
('o1005', 'u005', '华东', '南京', '家电', '优家电器', 'organic', '日常投放', 760.00, 120.00, 'COMPLETED', TIMESTAMP '2026-05-17 14:50:00'),
('o1006', 'u006', '华南', '深圳', '美妆', '美颜工坊', 'live_stream', '达人专场', 180.00, 0.00, 'PAID', TIMESTAMP '2026-05-17 19:25:00');

INSERT INTO dwd_user_events (event_id, user_id, event_type, region_name, city_name, category_l1, channel_code, campaign_name, event_time) VALUES
('e1001', 'u001', 'view', '华东', '上海', '女装', 'organic', '520大促', TIMESTAMP '2026-05-16 09:55:00'),
('e1002', 'u001', 'pay', '华东', '上海', '女装', 'organic', '520大促', TIMESTAMP '2026-05-16 10:05:00'),
('e1003', 'u002', 'view', '华东', '杭州', '家电', 'ad_feed', '520大促', TIMESTAMP '2026-05-16 11:05:00'),
('e1004', 'u002', 'pay', '华东', '杭州', '家电', 'ad_feed', '520大促', TIMESTAMP '2026-05-16 11:30:00'),
('e1005', 'u004', 'view', '华东', '上海', '女装', 'ad_feed', '520大促', TIMESTAMP '2026-05-17 08:55:00'),
('e1006', 'u004', 'pay', '华东', '上海', '女装', 'ad_feed', '520大促', TIMESTAMP '2026-05-17 09:15:00'),
('e1007', 'u005', 'view', '华东', '南京', '家电', 'organic', '日常投放', TIMESTAMP '2026-05-17 14:00:00'),
('e1008', 'u005', 'pay', '华东', '南京', '家电', 'organic', '日常投放', TIMESTAMP '2026-05-17 14:50:00'),
('e1009', 'u006', 'view', '华南', '深圳', '美妆', 'live_stream', '达人专场', TIMESTAMP '2026-05-17 18:50:00'),
('e1010', 'u006', 'pay', '华南', '深圳', '美妆', 'live_stream', '达人专场', TIMESTAMP '2026-05-17 19:25:00');

INSERT INTO dwd_refunds (refund_id, order_id, category_l1, merchant_name, region_name, refund_reason, refund_amount, refund_created_at) VALUES
('r1001', 'o1004', '女装', '衣尚旗舰店', '华东', '尺码不合适', 80.00, TIMESTAMP '2026-05-18 10:00:00'),
('r1002', 'o1005', '家电', '优家电器', '华东', '商品质量问题', 120.00, TIMESTAMP '2026-05-18 11:20:00');

INSERT INTO ads_daily_core_metrics (stat_date, gmv, paid_order_count, active_buyer_count, dau, refund_rate) VALUES
(DATE '2026-05-16', 1510.00, 3, 3, 4200, 0.0000),
(DATE '2026-05-17', 1390.00, 3, 3, 3980, 0.3333);

INSERT INTO ads_region_daily (stat_date, region_name, gmv, paid_order_count, refund_rate) VALUES
(DATE '2026-05-16', '华东', 1300.00, 2, 0.0000),
(DATE '2026-05-16', '华南', 210.00, 1, 0.0000),
(DATE '2026-05-17', '华东', 1210.00, 2, 0.5000),
(DATE '2026-05-17', '华南', 180.00, 1, 0.0000);

INSERT INTO ads_category_daily (stat_date, category_l1, gmv, paid_order_count, refund_rate) VALUES
(DATE '2026-05-16', '女装', 320.00, 1, 0.0000),
(DATE '2026-05-16', '家电', 980.00, 1, 0.0000),
(DATE '2026-05-16', '美妆', 210.00, 1, 0.0000),
(DATE '2026-05-17', '女装', 450.00, 1, 1.0000),
(DATE '2026-05-17', '家电', 760.00, 1, 1.0000),
(DATE '2026-05-17', '美妆', 180.00, 1, 0.0000);

INSERT INTO ads_channel_daily (stat_date, channel_code, dau, active_buyer_count, conversion_rate) VALUES
(DATE '2026-05-16', 'organic', 1800, 2, 0.0820),
(DATE '2026-05-16', 'ad_feed', 1600, 1, 0.0640),
(DATE '2026-05-17', 'organic', 1500, 1, 0.0610),
(DATE '2026-05-17', 'ad_feed', 1400, 1, 0.0530),
(DATE '2026-05-17', 'live_stream', 1080, 1, 0.0750);

INSERT INTO ads_refund_daily (stat_date, category_l1, merchant_name, refund_rate) VALUES
(DATE '2026-05-18', '女装', '衣尚旗舰店', 1.0000),
(DATE '2026-05-18', '家电', '优家电器', 1.0000);

INSERT INTO ads_campaign_daily (stat_date, campaign_name, channel_code, gmv, conversion_rate, roi) VALUES
(DATE '2026-05-16', '520大促', 'organic', 320.00, 0.0820, 3.2000),
(DATE '2026-05-16', '520大促', 'ad_feed', 980.00, 0.0640, 2.1000),
(DATE '2026-05-17', '520大促', 'ad_feed', 450.00, 0.0530, 1.3500),
(DATE '2026-05-17', '日常投放', 'organic', 760.00, 0.0610, 2.5000),
(DATE '2026-05-17', '达人专场', 'live_stream', 180.00, 0.0750, 1.8500);

-- API error rate demo data: two APIs, 8 days, with a spike on 2026-05-14 simulating a bad release
INSERT INTO demo_api_error_log (log_date, api_name, http_status, error_count, total_request_count, error_rate) VALUES
(DATE '2026-05-10', '/api/checkout/submit', 400, 12, 2400, 0.005000),
(DATE '2026-05-11', '/api/checkout/submit', 400, 11, 2350, 0.004681),
(DATE '2026-05-12', '/api/checkout/submit', 400, 13, 2420, 0.005372),
(DATE '2026-05-13', '/api/checkout/submit', 400, 10, 2380, 0.004202),
(DATE '2026-05-14', '/api/checkout/submit', 400, 192, 2400, 0.080000),
(DATE '2026-05-15', '/api/checkout/submit', 400, 86, 2350, 0.036596),
(DATE '2026-05-16', '/api/checkout/submit', 400, 28, 2460, 0.011382),
(DATE '2026-05-17', '/api/checkout/submit', 400, 14, 2410, 0.005809),
(DATE '2026-05-10', '/api/checkout/submit', 500, 3, 2400, 0.001250),
(DATE '2026-05-11', '/api/checkout/submit', 500, 2, 2350, 0.000851),
(DATE '2026-05-12', '/api/checkout/submit', 500, 4, 2420, 0.001653),
(DATE '2026-05-13', '/api/checkout/submit', 500, 3, 2380, 0.001261),
(DATE '2026-05-14', '/api/checkout/submit', 500, 98, 2400, 0.040833),
(DATE '2026-05-15', '/api/checkout/submit', 500, 43, 2350, 0.018298),
(DATE '2026-05-16', '/api/checkout/submit', 500, 9, 2460, 0.003659),
(DATE '2026-05-17', '/api/checkout/submit', 500, 4, 2410, 0.001660),
(DATE '2026-05-10', '/api/payment/confirm', 400, 7, 2200, 0.003182),
(DATE '2026-05-11', '/api/payment/confirm', 400, 8, 2180, 0.003670),
(DATE '2026-05-12', '/api/payment/confirm', 400, 6, 2250, 0.002667),
(DATE '2026-05-13', '/api/payment/confirm', 400, 9, 2210, 0.004072),
(DATE '2026-05-14', '/api/payment/confirm', 400, 110, 2200, 0.050000),
(DATE '2026-05-15', '/api/payment/confirm', 400, 52, 2180, 0.023853),
(DATE '2026-05-16', '/api/payment/confirm', 400, 16, 2240, 0.007143),
(DATE '2026-05-17', '/api/payment/confirm', 400, 8, 2200, 0.003636),
(DATE '2026-05-10', '/api/payment/confirm', 500, 2, 2200, 0.000909),
(DATE '2026-05-11', '/api/payment/confirm', 500, 1, 2180, 0.000459),
(DATE '2026-05-12', '/api/payment/confirm', 500, 2, 2250, 0.000889),
(DATE '2026-05-13', '/api/payment/confirm', 500, 3, 2210, 0.001357),
(DATE '2026-05-14', '/api/payment/confirm', 500, 55, 2200, 0.025000),
(DATE '2026-05-15', '/api/payment/confirm', 500, 26, 2180, 0.011927),
(DATE '2026-05-16', '/api/payment/confirm', 500, 5, 2240, 0.002232),
(DATE '2026-05-17', '/api/payment/confirm', 500, 3, 2200, 0.001364);

-- A/B 实验「新版结算页_v2」用户分组标签（基于 2018-08-28~29 的真实行为用户）。
-- 转化率不在这里预写，而是由工具用「付款人数 ÷ 访问人数」从行为数据现场计算：
--   A 组（对照）：u201/u202/u203 付款，u209~u214 仅访问 → 3/9 ≈ 33.3%
--   B 组（实验）：u204/u205/u206/u207/u208/u217 付款，u215/u216/u218 仅访问 → 6/9 ≈ 66.7%
INSERT INTO dwd_experiment_assignment (experiment_name, user_id, group_id, assigned_at) VALUES
('新版结算页_v2', 'u201', 'A', TIMESTAMP '2018-08-28 00:00:00'),
('新版结算页_v2', 'u202', 'A', TIMESTAMP '2018-08-28 00:00:00'),
('新版结算页_v2', 'u203', 'A', TIMESTAMP '2018-08-28 00:00:00'),
('新版结算页_v2', 'u209', 'A', TIMESTAMP '2018-08-28 00:00:00'),
('新版结算页_v2', 'u210', 'A', TIMESTAMP '2018-08-28 00:00:00'),
('新版结算页_v2', 'u211', 'A', TIMESTAMP '2018-08-28 00:00:00'),
('新版结算页_v2', 'u212', 'A', TIMESTAMP '2018-08-28 00:00:00'),
('新版结算页_v2', 'u213', 'A', TIMESTAMP '2018-08-29 00:00:00'),
('新版结算页_v2', 'u214', 'A', TIMESTAMP '2018-08-29 00:00:00'),
('新版结算页_v2', 'u204', 'B', TIMESTAMP '2018-08-28 00:00:00'),
('新版结算页_v2', 'u205', 'B', TIMESTAMP '2018-08-29 00:00:00'),
('新版结算页_v2', 'u206', 'B', TIMESTAMP '2018-08-29 00:00:00'),
('新版结算页_v2', 'u207', 'B', TIMESTAMP '2018-08-29 00:00:00'),
('新版结算页_v2', 'u208', 'B', TIMESTAMP '2018-08-29 00:00:00'),
('新版结算页_v2', 'u217', 'B', TIMESTAMP '2018-08-28 00:00:00'),
('新版结算页_v2', 'u215', 'B', TIMESTAMP '2018-08-29 00:00:00'),
('新版结算页_v2', 'u216', 'B', TIMESTAMP '2018-08-29 00:00:00'),
('新版结算页_v2', 'u218', 'B', TIMESTAMP '2018-08-28 00:00:00');

INSERT INTO raw_orders (order_id, buyer_id, city_name, province_name, category_l1, merchant_name, channel_code, campaign_name, pay_amount, refund_amount, order_status, paid_at) VALUES
('o2001', 'u201', '上海', '上海', '家电', '优家电器', 'organic', '818大促', 1200.00, 0.00, 'PAID', TIMESTAMP '2018-08-28 09:10:00'),
('o2002', 'u202', '杭州', '浙江', '家电', '优家电器', 'ad_feed', '818大促', 1000.00, 0.00, 'PAID', TIMESTAMP '2018-08-28 10:40:00'),
('o2003', 'u203', '上海', '上海', '女装', '衣尚旗舰店', 'organic', '秋上新', 1000.00, 0.00, 'PAID', TIMESTAMP '2018-08-28 12:25:00'),
('o2004', 'u204', '南京', '江苏', '美妆', '美颜工坊', 'ad_feed', '秋上新', 641.30, 0.00, 'PAID', TIMESTAMP '2018-08-28 15:05:00'),
('o2009', 'u217', '苏州', '江苏', '家居', '宜居生活馆', 'organic', '家装换新', 520.00, 0.00, 'PAID', TIMESTAMP '2018-08-28 16:40:00'),
('o2005', 'u205', '上海', '上海', '家电', '优家电器', 'organic', '818大促', 400.00, 60.00, 'COMPLETED', TIMESTAMP '2018-08-29 09:20:00'),
('o2006', 'u206', '杭州', '浙江', '女装', '衣尚旗舰店', 'organic', '秋上新', 720.87, 0.00, 'PAID', TIMESTAMP '2018-08-29 11:10:00'),
('o2007', 'u207', '南京', '江苏', '美妆', '美颜工坊', 'ad_feed', '秋上新', 450.00, 0.00, 'PAID', TIMESTAMP '2018-08-29 14:00:00'),
('o2008', 'u208', '合肥', '安徽', '女装', '衣尚旗舰店', 'live_stream', '达人返场', 300.00, 0.00, 'PAID', TIMESTAMP '2018-08-29 18:35:00');

INSERT INTO dwd_orders (order_id, buyer_id, region_name, city_name, category_l1, merchant_name, channel_code, campaign_name, pay_amount, refund_amount, order_status, paid_at) VALUES
('o2001', 'u201', '华东', '上海', '家电', '优家电器', 'organic', '818大促', 1200.00, 0.00, 'PAID', TIMESTAMP '2018-08-28 09:10:00'),
('o2002', 'u202', '华东', '杭州', '家电', '优家电器', 'ad_feed', '818大促', 1000.00, 0.00, 'PAID', TIMESTAMP '2018-08-28 10:40:00'),
('o2003', 'u203', '华东', '上海', '女装', '衣尚旗舰店', 'organic', '秋上新', 1000.00, 0.00, 'PAID', TIMESTAMP '2018-08-28 12:25:00'),
('o2004', 'u204', '华东', '南京', '美妆', '美颜工坊', 'ad_feed', '秋上新', 641.30, 0.00, 'PAID', TIMESTAMP '2018-08-28 15:05:00'),
('o2009', 'u217', '华东', '苏州', '家居', '宜居生活馆', 'organic', '家装换新', 520.00, 0.00, 'PAID', TIMESTAMP '2018-08-28 16:40:00'),
('o2005', 'u205', '华东', '上海', '家电', '优家电器', 'organic', '818大促', 400.00, 60.00, 'COMPLETED', TIMESTAMP '2018-08-29 09:20:00'),
('o2006', 'u206', '华东', '杭州', '女装', '衣尚旗舰店', 'organic', '秋上新', 720.87, 0.00, 'PAID', TIMESTAMP '2018-08-29 11:10:00'),
('o2007', 'u207', '华东', '南京', '美妆', '美颜工坊', 'ad_feed', '秋上新', 450.00, 0.00, 'PAID', TIMESTAMP '2018-08-29 14:00:00'),
('o2008', 'u208', '华东', '合肥', '女装', '衣尚旗舰店', 'live_stream', '达人返场', 300.00, 0.00, 'PAID', TIMESTAMP '2018-08-29 18:35:00');

INSERT INTO dwd_user_events (event_id, user_id, event_type, region_name, city_name, category_l1, channel_code, campaign_name, event_time) VALUES
('e2001', 'u201', 'view', '华东', '上海', '家电', 'organic', '818大促', TIMESTAMP '2018-08-28 08:50:00'),
('e2002', 'u201', 'pay', '华东', '上海', '家电', 'organic', '818大促', TIMESTAMP '2018-08-28 09:10:00'),
('e2003', 'u202', 'view', '华东', '杭州', '家电', 'ad_feed', '818大促', TIMESTAMP '2018-08-28 10:05:00'),
('e2004', 'u202', 'pay', '华东', '杭州', '家电', 'ad_feed', '818大促', TIMESTAMP '2018-08-28 10:40:00'),
('e2005', 'u203', 'view', '华东', '上海', '女装', 'organic', '秋上新', TIMESTAMP '2018-08-28 11:45:00'),
('e2006', 'u203', 'pay', '华东', '上海', '女装', 'organic', '秋上新', TIMESTAMP '2018-08-28 12:25:00'),
('e2007', 'u204', 'view', '华东', '南京', '美妆', 'ad_feed', '秋上新', TIMESTAMP '2018-08-28 14:15:00'),
('e2008', 'u204', 'pay', '华东', '南京', '美妆', 'ad_feed', '秋上新', TIMESTAMP '2018-08-28 15:05:00'),
('e2009', 'u209', 'view', '华东', '上海', '家电', 'organic', '818大促', TIMESTAMP '2018-08-28 09:45:00'),
('e2010', 'u210', 'view', '华东', '杭州', '女装', 'organic', '秋上新', TIMESTAMP '2018-08-28 13:10:00'),
('e2011', 'u211', 'view', '华东', '南京', '美妆', 'ad_feed', '秋上新', TIMESTAMP '2018-08-28 16:20:00'),
('e2012', 'u212', 'view', '华东', '合肥', '女装', 'live_stream', '达人返场', TIMESTAMP '2018-08-28 19:30:00'),
('e2012a', 'u217', 'view', '华东', '苏州', '家居', 'organic', '家装换新', TIMESTAMP '2018-08-28 16:05:00'),
('e2012b', 'u217', 'pay', '华东', '苏州', '家居', 'organic', '家装换新', TIMESTAMP '2018-08-28 16:40:00'),
('e2012c', 'u218', 'view', '华东', '宁波', '家居', 'organic', '家装换新', TIMESTAMP '2018-08-28 17:20:00'),
('e2013', 'u205', 'view', '华东', '上海', '家电', 'organic', '818大促', TIMESTAMP '2018-08-29 08:55:00'),
('e2014', 'u205', 'pay', '华东', '上海', '家电', 'organic', '818大促', TIMESTAMP '2018-08-29 09:20:00'),
('e2015', 'u206', 'view', '华东', '杭州', '女装', 'organic', '秋上新', TIMESTAMP '2018-08-29 10:30:00'),
('e2016', 'u206', 'pay', '华东', '杭州', '女装', 'organic', '秋上新', TIMESTAMP '2018-08-29 11:10:00'),
('e2017', 'u207', 'view', '华东', '南京', '美妆', 'ad_feed', '秋上新', TIMESTAMP '2018-08-29 13:20:00'),
('e2018', 'u207', 'pay', '华东', '南京', '美妆', 'ad_feed', '秋上新', TIMESTAMP '2018-08-29 14:00:00'),
('e2019', 'u208', 'view', '华东', '合肥', '女装', 'live_stream', '达人返场', TIMESTAMP '2018-08-29 18:00:00'),
('e2020', 'u208', 'pay', '华东', '合肥', '女装', 'live_stream', '达人返场', TIMESTAMP '2018-08-29 18:35:00'),
('e2021', 'u213', 'view', '华东', '上海', '家电', 'organic', '818大促', TIMESTAMP '2018-08-29 09:40:00'),
('e2022', 'u214', 'view', '华东', '杭州', '女装', 'organic', '秋上新', TIMESTAMP '2018-08-29 12:05:00'),
('e2023', 'u215', 'view', '华东', '南京', '美妆', 'ad_feed', '秋上新', TIMESTAMP '2018-08-29 15:10:00'),
('e2024', 'u216', 'view', '华东', '合肥', '女装', 'live_stream', '达人返场', TIMESTAMP '2018-08-29 20:05:00');
