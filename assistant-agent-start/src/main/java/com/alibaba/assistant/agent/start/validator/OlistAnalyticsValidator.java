package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.start.config.AppDataSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(990)
public class OlistAnalyticsValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OlistAnalyticsValidator.class);

    private final AppDataSourceProperties appDataSourceProperties;
    private final JdbcTemplate jdbcTemplate;

    public OlistAnalyticsValidator(AppDataSourceProperties appDataSourceProperties,
                                   JdbcTemplate jdbcTemplate) {
        this.appDataSourceProperties = appDataSourceProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        boolean analyticsRequested = appDataSourceProperties.isBootstrapOlistAnalytics()
                || appDataSourceProperties.isPreferOlistAnalytics();
        if (!analyticsRequested && !hasAdsTableRows()) {
            return;
        }

        Map<String, Integer> stageCounts = new LinkedHashMap<>();
        stageCounts.put("raw_olist_orders", count("SELECT COUNT(*) FROM raw_olist_orders"));
        stageCounts.put("raw_olist_order_items", count("SELECT COUNT(*) FROM raw_olist_order_items"));
        stageCounts.put("dwd_olist_orders", count("SELECT COUNT(*) FROM dwd_olist_orders"));
        stageCounts.put("dwd_olist_order_items", count("SELECT COUNT(*) FROM dwd_olist_order_items"));
        stageCounts.put("dim_olist_regions", count("SELECT COUNT(*) FROM dim_olist_regions"));
        stageCounts.put("dim_olist_categories", count("SELECT COUNT(*) FROM dim_olist_categories"));
        stageCounts.put("dim_olist_products", count("SELECT COUNT(*) FROM dim_olist_products"));
        stageCounts.put("ads_olist_daily_core_metrics", count("SELECT COUNT(*) FROM ads_olist_daily_core_metrics"));
        stageCounts.put("ads_olist_region_daily", count("SELECT COUNT(*) FROM ads_olist_region_daily"));
        stageCounts.put("ads_olist_category_daily", count("SELECT COUNT(*) FROM ads_olist_category_daily"));

        Map<String, Integer> relationshipGaps = new LinkedHashMap<>();
        relationshipGaps.put("dwd_items_without_order", count("""
                SELECT COUNT(*)
                FROM dwd_olist_order_items i
                LEFT JOIN dwd_olist_orders o ON o.order_id = i.order_id
                WHERE o.order_id IS NULL
                """));
        relationshipGaps.put("dwd_items_without_dim_product", count("""
                SELECT COUNT(*)
                FROM dwd_olist_order_items i
                LEFT JOIN dim_olist_products p ON p.product_id = i.product_id
                WHERE p.product_id IS NULL
                """));
        relationshipGaps.put("ads_region_without_dim_region", count("""
                SELECT COUNT(*)
                FROM ads_olist_region_daily a
                LEFT JOIN dim_olist_regions r ON r.region_name_seed = a.region_name_seed
                WHERE r.state_code IS NULL
                """));
        relationshipGaps.put("ads_category_without_dim_category", count("""
                SELECT COUNT(*)
                FROM ads_olist_category_daily a
                LEFT JOIN dim_olist_categories c ON c.category_l1_seed = a.category_l1_seed
                WHERE c.product_category_name_raw IS NULL
                """));

        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("ads_min_date", scalar("SELECT MIN(stat_date) FROM ads_olist_daily_core_metrics"));
        coverage.put("ads_max_date", scalar("SELECT MAX(stat_date) FROM ads_olist_daily_core_metrics"));
        coverage.put("region_coverage", count("SELECT COUNT(DISTINCT region_name_seed) FROM ads_olist_region_daily"));
        coverage.put("category_coverage", count("SELECT COUNT(DISTINCT category_l1_seed) FROM ads_olist_category_daily"));

        log.info("OlistAnalyticsValidator#run - reason=olist analytics summary generated, stageCounts={}, relationshipGaps={}, coverage={}",
                stageCounts, relationshipGaps, coverage);

        requirePositive(stageCounts, "dwd_olist_orders");
        requirePositive(stageCounts, "dwd_olist_order_items");
        requirePositive(stageCounts, "dim_olist_regions");
        requirePositive(stageCounts, "dim_olist_categories");
        requirePositive(stageCounts, "dim_olist_products");
        requirePositive(stageCounts, "ads_olist_daily_core_metrics");
        requirePositive(stageCounts, "ads_olist_region_daily");
        requirePositive(stageCounts, "ads_olist_category_daily");

        requireZero(relationshipGaps, "dwd_items_without_order");
        requireZero(relationshipGaps, "dwd_items_without_dim_product");
        requireZero(relationshipGaps, "ads_region_without_dim_region");
        requireZero(relationshipGaps, "ads_category_without_dim_category");
    }

    private boolean hasAdsTableRows() {
        Integer tables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE UPPER(TABLE_NAME) = 'ADS_OLIST_DAILY_CORE_METRICS'
                """, Integer.class);
        if (tables == null || tables == 0) {
            return false;
        }
        return count("SELECT COUNT(*) FROM ads_olist_daily_core_metrics") > 0;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private Object scalar(String sql) {
        return jdbcTemplate.queryForObject(sql, Object.class);
    }

    private void requirePositive(Map<String, Integer> counts, String key) {
        Integer value = counts.get(key);
        if (value == null || value <= 0) {
            throw new IllegalStateException("Olist analytics coverage check failed: " + key + " has no rows.");
        }
    }

    private void requireZero(Map<String, Integer> gaps, String key) {
        Integer value = gaps.get(key);
        if (value != null && value > 0) {
            throw new IllegalStateException("Olist analytics integrity check failed: " + key + "=" + value);
        }
    }
}
