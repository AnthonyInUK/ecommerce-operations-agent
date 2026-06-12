package com.alibaba.assistant.agent.start.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OlistRawImportAuditService {

    private final JdbcTemplate jdbcTemplate;

    public OlistRawImportAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasRawOrders() {
        if (!tableExists("raw_olist_orders")) {
            return false;
        }
        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_olist_orders", Integer.class);
        return rowCount != null && rowCount > 0;
    }

    public Map<String, Integer> buildRowCountSummary() {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("raw_olist_customers", count("SELECT COUNT(*) FROM raw_olist_customers"));
        summary.put("raw_olist_products", count("SELECT COUNT(*) FROM raw_olist_products"));
        summary.put("raw_olist_orders", count("SELECT COUNT(*) FROM raw_olist_orders"));
        summary.put("raw_olist_order_items", count("SELECT COUNT(*) FROM raw_olist_order_items"));
        summary.put("raw_olist_payments", count("SELECT COUNT(*) FROM raw_olist_payments"));
        summary.put("raw_olist_reviews", count("SELECT COUNT(*) FROM raw_olist_reviews"));
        summary.put("raw_olist_geolocation", count("SELECT COUNT(*) FROM raw_olist_geolocation"));
        return summary;
    }

    public Map<String, Integer> buildRelationshipGapSummary() {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("orders_without_customer", count("""
                SELECT COUNT(*)
                FROM raw_olist_orders o
                LEFT JOIN raw_olist_customers c ON c.customer_id = o.customer_id
                WHERE c.customer_id IS NULL
                """));
        summary.put("items_without_order", count("""
                SELECT COUNT(*)
                FROM raw_olist_order_items i
                LEFT JOIN raw_olist_orders o ON o.order_id = i.order_id
                WHERE o.order_id IS NULL
                """));
        summary.put("items_without_product", count("""
                SELECT COUNT(*)
                FROM raw_olist_order_items i
                LEFT JOIN raw_olist_products p ON p.product_id = i.product_id
                WHERE p.product_id IS NULL
                """));
        summary.put("payments_without_order", count("""
                SELECT COUNT(*)
                FROM raw_olist_payments p
                LEFT JOIN raw_olist_orders o ON o.order_id = p.order_id
                WHERE o.order_id IS NULL
                """));
        summary.put("reviews_without_order", count("""
                SELECT COUNT(*)
                FROM raw_olist_reviews r
                LEFT JOIN raw_olist_orders o ON o.order_id = r.order_id
                WHERE o.order_id IS NULL
                """));
        return summary;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }
}
