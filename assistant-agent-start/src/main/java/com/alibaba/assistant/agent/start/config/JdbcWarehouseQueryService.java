package com.alibaba.assistant.agent.start.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcWarehouseQueryService {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final Set<String> WRITE_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "DROP", "ALTER", "TRUNCATE", "CREATE", "REPLACE", "CALL"
    );

    private final JdbcTemplate jdbcTemplate;
    private final AppDataSourceProperties properties;
    private final ConcurrentMap<String, CacheEntry> readCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong cacheSkips = new AtomicLong();

    public JdbcWarehouseQueryService(JdbcTemplate jdbcTemplate, AppDataSourceProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public List<Map<String, Object>> queryForList(String sql) {
        assertReadOnlySql(sql);
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        assertReadOnlySql(sql);
        return jdbcTemplate.queryForList(sql, args);
    }

    public Map<String, Object> cacheStats() {
        return Map.of(
                "enabled", properties.isReadCacheEnabled(),
                "ttl_seconds", properties.getReadCacheTtlSeconds(),
                "max_entries", properties.getReadCacheMaxEntries(),
                "cache_empty_results", properties.isReadCacheCacheEmptyResults(),
                "entry_count", readCache.size(),
                "hits", cacheHits.get(),
                "misses", cacheMisses.get(),
                "skips", cacheSkips.get()
        );
    }

    public Integer countRows(String tableName) {
        if (tableName == null || !SAFE_IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Unsafe table name for countRows: " + tableName);
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    public List<Map<String, Object>> getDailyCoreMetrics(LocalDate statDate) {
        return cachedList("dailyCoreMetrics|" + statDate, () -> {
            if (shouldUseOlistAnalytics()) {
                List<Map<String, Object>> olistRows = jdbcTemplate.queryForList(
                        """
                                SELECT stat_date, gmv, paid_order_count, active_buyer_count, 0 AS dau, 0 AS refund_rate, source_tag
                                FROM ads_olist_daily_core_metrics
                                WHERE stat_date = ?
                                ORDER BY stat_date
                                """,
                        Date.valueOf(statDate)
                );
                if (!olistRows.isEmpty()) {
                    return olistRows;
                }
            }

            return jdbcTemplate.queryForList(
                    """
                            SELECT stat_date, gmv, paid_order_count, active_buyer_count, dau, refund_rate, 'demo_seed' AS source_tag
                            FROM ads_daily_core_metrics
                            WHERE stat_date = ?
                            ORDER BY stat_date
                            """,
                    Date.valueOf(statDate)
            );
        });
    }

    public List<Map<String, Object>> getUserDailyMetrics(LocalDate statDate, String regionName) {
        return cachedList("userDailyMetrics|" + statDate + "|" + normalize(regionName), () -> {
            if (shouldUseOlistAnalytics()) {
                List<Map<String, Object>> olistRows = queryOlistUserDailyMetrics(statDate, regionName);
                if (!olistRows.isEmpty()) {
                    return olistRows;
                }
                // Olist has order/customer snapshots, but not a stable behavior-stream
                // user metric layer. Fall back to demo-completed user metrics so
                // high-frequency user questions do not return empty cards.
                return queryDemoUserDailyMetrics(statDate, regionName);
            }
            return queryDemoUserDailyMetrics(statDate, regionName);
        });
    }

    private List<Map<String, Object>> queryDemoUserDailyMetrics(LocalDate statDate, String regionName) {
        if (regionName == null || regionName.isBlank()) {
            return jdbcTemplate.queryForList(
                    """
                            SELECT stat_date, dau, active_buyer_count, paid_order_count,
                                   CASE WHEN dau = 0 THEN 0
                                        ELSE CAST(active_buyer_count AS DOUBLE) / dau
                                   END AS buyer_activation_rate,
                                   'demo_seed' AS source_tag
                            FROM ads_daily_core_metrics
                            WHERE stat_date = ?
                            ORDER BY stat_date
                            """,
                    Date.valueOf(statDate)
            );
        }
        return jdbcTemplate.queryForList(
                """
                        SELECT CAST(events.event_time AS DATE) AS stat_date,
                               COUNT(DISTINCT events.user_id) AS dau,
                               COUNT(DISTINCT CASE WHEN orders.order_status IN ('PAID', 'COMPLETED') THEN orders.buyer_id END) AS active_buyer_count,
                               COUNT(DISTINCT CASE WHEN orders.order_status IN ('PAID', 'COMPLETED') THEN orders.order_id END) AS paid_order_count,
                               CASE WHEN COUNT(DISTINCT events.user_id) = 0 THEN 0
                                    ELSE CAST(COUNT(DISTINCT CASE WHEN orders.order_status IN ('PAID', 'COMPLETED') THEN orders.buyer_id END) AS DOUBLE)
                                         / COUNT(DISTINCT events.user_id)
                               END AS buyer_activation_rate,
                               'demo_seed' AS source_tag
                        FROM dwd_user_events events
                        LEFT JOIN dwd_orders orders
                               ON CAST(orders.paid_at AS DATE) = CAST(events.event_time AS DATE)
                              AND orders.region_name = events.region_name
                              AND orders.buyer_id = events.user_id
                        WHERE CAST(events.event_time AS DATE) = ? AND events.region_name = ?
                        GROUP BY CAST(events.event_time AS DATE)
                        """,
                Date.valueOf(statDate),
                regionName
        );
    }

    private List<Map<String, Object>> queryOlistUserDailyMetrics(LocalDate statDate, String regionName) {
        if (regionName != null && !regionName.isBlank()) {
            return jdbcTemplate.queryForList(
                    """
                    SELECT CAST(order_purchase_timestamp AS DATE) AS stat_date,
                           COUNT(DISTINCT customer_unique_id) AS active_buyer_count,
                           COUNT(DISTINCT customer_unique_id) AS dau,
                           COUNT(DISTINCT CASE WHEN order_status NOT IN ('canceled','unavailable') THEN order_id END) AS paid_order_count,
                           1.0 AS buyer_activation_rate,
                           'olist_public_dataset' AS source_tag
                    FROM dwd_olist_orders
                    WHERE CAST(order_purchase_timestamp AS DATE) = ?
                      AND region_name_seed = ?
                    GROUP BY CAST(order_purchase_timestamp AS DATE)
                    """,
                    Date.valueOf(statDate), regionName);
        }
        return jdbcTemplate.queryForList(
                """
                SELECT o.stat_date,
                       o.active_buyer_count,
                       o.active_buyer_count AS dau,
                       o.paid_order_count,
                       1.0 AS buyer_activation_rate,
                       'olist_public_dataset' AS source_tag
                FROM ads_olist_daily_core_metrics o
                WHERE o.stat_date = ?
                """,
                Date.valueOf(statDate));
    }

    public String getUserAnalyticsSource() {
        return shouldUseOlistAnalytics() ? "olist_public_dataset" : "demo_seed";
    }

    // Olist 公开数据集不含渠道维度（无 utm_source / channel 字段），此方法固定走 demo schema。
    // 若接入有渠道埋点的真实数仓，在此处添加 shouldUseOlistAnalytics() 分支即可。
    public List<Map<String, Object>> getChannelUserMetrics(LocalDate statDate, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;
        return cachedList("channelUserMetrics|" + statDate + "|" + safeLimit, () -> jdbcTemplate.queryForList(
                """
                        SELECT stat_date, channel_code, dau, active_buyer_count, conversion_rate
                        FROM ads_channel_daily
                        WHERE stat_date = ?
                        ORDER BY conversion_rate DESC, active_buyer_count DESC, channel_code ASC
                        LIMIT ?
                        """,
                Date.valueOf(statDate),
                safeLimit
        ));
    }

    public List<Map<String, Object>> getOrderDailyMetrics(LocalDate statDate, String regionName, String categoryName) {
        return cachedList("orderDailyMetrics|" + statDate + "|" + normalize(regionName) + "|" + normalize(categoryName), () -> {
        if (shouldUseOlistAnalytics()) {
            if (categoryName == null || categoryName.isBlank()) {
                StringBuilder sql = new StringBuilder(
                        """
                                SELECT CAST(order_purchase_timestamp AS DATE) AS stat_date,
                                       COUNT(*) AS order_count,
                                       SUM(CASE WHEN COALESCE(paid_amount, 0) > 0 THEN 1 ELSE 0 END) AS paid_order_count,
                                       0 AS refunded_order_count,
                                       SUM(COALESCE(paid_amount, 0)) AS gross_pay_amount,
                                       0 AS refund_amount,
                                       CASE WHEN COUNT(*) = 0 THEN 0
                                            ELSE SUM(COALESCE(paid_amount, 0)) / COUNT(*)
                                       END AS avg_order_value,
                                       'olist_public_dataset' AS source_tag
                                FROM dwd_olist_orders
                                WHERE CAST(order_purchase_timestamp AS DATE) = ?
                                """
                );
                java.util.List<Object> args = new java.util.ArrayList<>();
                args.add(Date.valueOf(statDate));
                if (regionName != null && !regionName.isBlank()) {
                    sql.append("""
                             AND customer_state IN (
                                 SELECT state_code
                                 FROM dim_olist_regions
                                 WHERE region_name_seed = ?
                             )
                            """);
                    args.add(regionName);
                }
                sql.append(" GROUP BY CAST(order_purchase_timestamp AS DATE)");
                List<Map<String, Object>> olistRows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
                if (!olistRows.isEmpty()) {
                    return olistRows;
                }
            } else {
                StringBuilder sql = new StringBuilder(
                        """
                                SELECT CAST(o.order_purchase_timestamp AS DATE) AS stat_date,
                                       COUNT(DISTINCT i.order_id) AS order_count,
                                       COUNT(DISTINCT CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN i.order_id END) AS paid_order_count,
                                       0 AS refunded_order_count,
                                       SUM(COALESCE(i.item_gross_amount, 0)) AS gross_pay_amount,
                                       0 AS refund_amount,
                                       CASE WHEN COUNT(DISTINCT i.order_id) = 0 THEN 0
                                            ELSE SUM(COALESCE(i.item_gross_amount, 0)) / COUNT(DISTINCT i.order_id)
                                       END AS avg_order_value,
                                       'olist_public_dataset' AS source_tag
                                FROM dwd_olist_orders o
                                JOIN dwd_olist_order_items i
                                  ON o.order_id = i.order_id
                                LEFT JOIN dim_olist_products p
                                  ON i.product_id = p.product_id
                                WHERE CAST(o.order_purchase_timestamp AS DATE) = ?
                                  AND COALESCE(p.category_l1_seed, '其他品类') = ?
                                """
                );
                java.util.List<Object> args = new java.util.ArrayList<>();
                args.add(Date.valueOf(statDate));
                args.add(categoryName);
                if (regionName != null && !regionName.isBlank()) {
                    sql.append("""
                             AND o.customer_state IN (
                                 SELECT state_code
                                 FROM dim_olist_regions
                                 WHERE region_name_seed = ?
                             )
                            """);
                    args.add(regionName);
                }
                sql.append(" GROUP BY CAST(o.order_purchase_timestamp AS DATE)");
                List<Map<String, Object>> olistRows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
                if (!olistRows.isEmpty()) {
                    return olistRows;
                }
            }
        }

        StringBuilder sql = new StringBuilder(
                """
                        SELECT CAST(paid_at AS DATE) AS stat_date,
                               COUNT(*) AS order_count,
                               SUM(CASE WHEN order_status IN ('PAID', 'COMPLETED') THEN 1 ELSE 0 END) AS paid_order_count,
                               SUM(CASE WHEN refund_amount > 0 THEN 1 ELSE 0 END) AS refunded_order_count,
                               SUM(pay_amount) AS gross_pay_amount,
                               SUM(refund_amount) AS refund_amount,
                               CASE WHEN COUNT(*) = 0 THEN 0
                                    ELSE SUM(pay_amount) / COUNT(*)
                               END AS avg_order_value,
                               'demo_seed' AS source_tag
                        FROM dwd_orders
                        WHERE CAST(paid_at AS DATE) = ?
                        """
        );
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(Date.valueOf(statDate));
        if (regionName != null && !regionName.isBlank()) {
            sql.append(" AND region_name = ?");
            args.add(regionName);
        }
        if (categoryName != null && !categoryName.isBlank()) {
            sql.append(" AND category_l1 = ?");
            args.add(categoryName);
        }
        sql.append(" GROUP BY CAST(paid_at AS DATE)");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
        });
    }

    public List<Map<String, Object>> getRegionDailyMetrics(LocalDate statDate, String regionName) {
        return cachedList("regionDailyMetrics|" + statDate + "|" + normalize(regionName), () -> {
        if (shouldUseOlistAnalytics()) {
            if (regionName == null || regionName.isBlank()) {
                List<Map<String, Object>> olistRows = jdbcTemplate.queryForList(
                        """
                                SELECT stat_date, region_name_seed AS region_name, gmv, paid_order_count, 0 AS refund_rate, source_tag
                                FROM ads_olist_region_daily
                                WHERE stat_date = ?
                                ORDER BY gmv DESC, region_name_seed ASC
                                """,
                        Date.valueOf(statDate)
                );
                if (!olistRows.isEmpty()) {
                    return olistRows;
                }
            }
            else {
                List<Map<String, Object>> olistRows = jdbcTemplate.queryForList(
                        """
                                SELECT stat_date, region_name_seed AS region_name, gmv, paid_order_count, 0 AS refund_rate, source_tag
                                FROM ads_olist_region_daily
                                WHERE stat_date = ? AND region_name_seed = ?
                                ORDER BY gmv DESC, region_name_seed ASC
                                """,
                        Date.valueOf(statDate),
                        regionName
                );
                if (!olistRows.isEmpty()) {
                    return olistRows;
                }
            }
        }

        if (regionName == null || regionName.isBlank()) {
            return jdbcTemplate.queryForList(
                    """
                            SELECT stat_date, region_name, gmv, paid_order_count, refund_rate, 'demo_seed' AS source_tag
                            FROM ads_region_daily
                            WHERE stat_date = ?
                            ORDER BY gmv DESC, region_name ASC
                            """,
                    Date.valueOf(statDate)
            );
        }

        return jdbcTemplate.queryForList(
                """
                        SELECT stat_date, region_name, gmv, paid_order_count, refund_rate, 'demo_seed' AS source_tag
                        FROM ads_region_daily
                        WHERE stat_date = ? AND region_name = ?
                        ORDER BY gmv DESC, region_name ASC
                        """,
                Date.valueOf(statDate),
                regionName
        );
        });
    }

    public List<Map<String, Object>> getCategoryDailyMetrics(LocalDate statDate, Integer limit) {
        return getCategoryDailyMetrics(statDate, limit, null);
    }

    public List<Map<String, Object>> getCategoryDailyMetrics(LocalDate statDate, Integer limit, String regionName) {
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;
        return cachedList("categoryDailyMetrics|" + statDate + "|" + safeLimit + "|" + normalize(regionName), () -> {
        if (shouldUseOlistAnalytics()) {
            if (regionName == null || regionName.isBlank()) {
                List<Map<String, Object>> olistRows = jdbcTemplate.queryForList(
                        """
                                SELECT stat_date, category_l1_seed AS category_l1, gmv, paid_order_count, 0 AS refund_rate, source_tag
                                FROM ads_olist_category_daily
                                WHERE stat_date = ?
                                ORDER BY gmv DESC, category_l1_seed ASC
                                LIMIT ?
                                """,
                        Date.valueOf(statDate),
                        safeLimit
                );
                if (!olistRows.isEmpty()) {
                    return olistRows;
                }
            }
            else {
                List<Map<String, Object>> olistRows = jdbcTemplate.queryForList(
                        """
                                SELECT
                                    CAST(o.order_purchase_timestamp AS DATE) AS stat_date,
                                    COALESCE(p.category_l1_seed, i.product_category_name, '其他品类') AS category_l1,
                                    SUM(COALESCE(i.item_price, 0)) AS gmv,
                                    COUNT(DISTINCT CASE WHEN COALESCE(o.paid_amount, 0) > 0 THEN o.order_id END) AS paid_order_count,
                                    0 AS refund_rate,
                                    'olist_public_dataset' AS source_tag
                                FROM dwd_olist_orders o
                                JOIN dwd_olist_order_items i
                                  ON o.order_id = i.order_id
                                LEFT JOIN dim_olist_products p
                                  ON i.product_id = p.product_id
                                WHERE CAST(o.order_purchase_timestamp AS DATE) = ?
                                  AND o.customer_state IN (
                                      SELECT state_code
                                      FROM dim_olist_regions
                                      WHERE region_name_seed = ?
                                  )
                                GROUP BY
                                    CAST(o.order_purchase_timestamp AS DATE),
                                    COALESCE(p.category_l1_seed, i.product_category_name, '其他品类')
                                ORDER BY gmv DESC, category_l1 ASC
                                LIMIT ?
                                """,
                        Date.valueOf(statDate),
                        regionName,
                        safeLimit
                );
                if (!olistRows.isEmpty()) {
                    return olistRows;
                }
            }
        }

        if (regionName == null || regionName.isBlank()) {
            return jdbcTemplate.queryForList(
                    """
                            SELECT stat_date, category_l1, gmv, paid_order_count, refund_rate, 'demo_seed' AS source_tag
                            FROM ads_category_daily
                            WHERE stat_date = ?
                            ORDER BY gmv DESC, category_l1 ASC
                            LIMIT ?
                            """,
                    Date.valueOf(statDate),
                    safeLimit
            );
        }

        return jdbcTemplate.queryForList(
                """
                        SELECT CAST(paid_at AS DATE) AS stat_date,
                               category_l1,
                               SUM(pay_amount) AS gmv,
                               COUNT(*) AS paid_order_count,
                               CASE WHEN SUM(pay_amount) = 0 THEN 0
                                    ELSE SUM(refund_amount) / SUM(pay_amount)
                               END AS refund_rate,
                               'demo_seed' AS source_tag
                        FROM dwd_orders
                        WHERE CAST(paid_at AS DATE) = ? AND region_name = ?
                        GROUP BY CAST(paid_at AS DATE), category_l1
                        ORDER BY gmv DESC, category_l1 ASC
                        LIMIT ?
                        """,
                Date.valueOf(statDate),
                regionName,
                safeLimit
        );
        });
    }

    public String getOverviewAnalyticsSource() {
        return shouldUseOlistAnalytics() ? "olist_public_dataset" : "demo_seed";
    }

    public String getOrderAnalyticsSource() {
        return shouldUseOlistAnalytics() ? "olist_public_dataset" : "demo_seed";
    }

    private boolean shouldUseOlistAnalytics() {
        return properties.isPreferOlistAnalytics() && tableHasRows("ads_olist_daily_core_metrics");
    }

    private boolean tableHasRows(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public List<Map<String, Object>> getRefundCategoryBreakdown(LocalDate statDate, String regionName, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;
        return cachedList("refundCategoryBreakdown|" + statDate + "|" + normalize(regionName) + "|" + safeLimit, () -> {
            if (shouldUseOlistAnalytics()) {
                return queryOlistRefundCategoryBreakdown(statDate, regionName, safeLimit);
            }
            if (regionName == null || regionName.isBlank()) {
                return jdbcTemplate.queryForList(
                        """
                            SELECT CAST(paid_at AS DATE) AS stat_date,
                                   category_l1,
                                   COUNT(*) AS refunded_order_count,
                                   SUM(refund_amount) AS refund_amount,
                                   SUM(pay_amount) AS related_gmv,
                                   CASE WHEN SUM(pay_amount) = 0 THEN 0
                                        ELSE SUM(refund_amount) / SUM(pay_amount)
                                   END AS refund_amount_rate,
                                   'demo_seed' AS source_tag
                            FROM dwd_orders
                            WHERE CAST(paid_at AS DATE) = ? AND refund_amount > 0
                            GROUP BY CAST(paid_at AS DATE), category_l1
                            ORDER BY refund_amount DESC, category_l1 ASC
                            LIMIT ?
                            """,
                        Date.valueOf(statDate), safeLimit);
            }
            return jdbcTemplate.queryForList(
                    """
                            SELECT CAST(paid_at AS DATE) AS stat_date,
                                   category_l1,
                                   COUNT(*) AS refunded_order_count,
                                   SUM(refund_amount) AS refund_amount,
                                   SUM(pay_amount) AS related_gmv,
                                   CASE WHEN SUM(pay_amount) = 0 THEN 0
                                        ELSE SUM(refund_amount) / SUM(pay_amount)
                                   END AS refund_amount_rate,
                                   'demo_seed' AS source_tag
                            FROM dwd_orders
                            WHERE CAST(paid_at AS DATE) = ? AND region_name = ? AND refund_amount > 0
                            GROUP BY CAST(paid_at AS DATE), category_l1
                            ORDER BY refund_amount DESC, category_l1 ASC
                            LIMIT ?
                            """,
                    Date.valueOf(statDate), regionName, safeLimit);
        });
    }

    private List<Map<String, Object>> queryOlistRefundCategoryBreakdown(LocalDate statDate, String regionName, int safeLimit) {
        // Olist 用 canceled 订单近似退款，金额取 item_gross_amount
        if (regionName != null && !regionName.isBlank()) {
            return jdbcTemplate.queryForList(
                    """
                    SELECT CAST(o.order_purchase_timestamp AS DATE) AS stat_date,
                           i.product_category_name AS category_l1,
                           COUNT(DISTINCT o.order_id) AS refunded_order_count,
                           SUM(i.item_gross_amount) AS refund_amount,
                           SUM(i.item_gross_amount) AS related_gmv,
                           1.0 AS refund_amount_rate,
                           'olist_public_dataset' AS source_tag
                    FROM dwd_olist_orders o
                    JOIN dwd_olist_order_items i ON o.order_id = i.order_id
                    WHERE CAST(o.order_purchase_timestamp AS DATE) = ?
                      AND o.order_status IN ('canceled', 'unavailable')
                      AND o.region_name_seed = ?
                    GROUP BY CAST(o.order_purchase_timestamp AS DATE), i.product_category_name
                    ORDER BY refund_amount DESC
                    LIMIT ?
                    """,
                    Date.valueOf(statDate), regionName, safeLimit);
        }
        return jdbcTemplate.queryForList(
                """
                SELECT CAST(o.order_purchase_timestamp AS DATE) AS stat_date,
                       i.product_category_name AS category_l1,
                       COUNT(DISTINCT o.order_id) AS refunded_order_count,
                       SUM(i.item_gross_amount) AS refund_amount,
                       SUM(i.item_gross_amount) AS related_gmv,
                       1.0 AS refund_amount_rate,
                       'olist_public_dataset' AS source_tag
                FROM dwd_olist_orders o
                JOIN dwd_olist_order_items i ON o.order_id = i.order_id
                WHERE CAST(o.order_purchase_timestamp AS DATE) = ?
                  AND o.order_status IN ('canceled', 'unavailable')
                GROUP BY CAST(o.order_purchase_timestamp AS DATE), i.product_category_name
                ORDER BY refund_amount DESC
                LIMIT ?
                """,
                Date.valueOf(statDate), safeLimit);
    }

    public String getRefundAnalyticsSource() {
        return shouldUseOlistAnalytics() ? "olist_public_dataset" : "demo_seed";
    }

    public List<Map<String, Object>> getProductSellerDrilldown(LocalDate statDate,
                                                              LocalDate previousDate,
                                                              String regionName,
                                                              String categoryName,
                                                              Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;
        return cachedList("productSellerDrilldown|" + statDate + "|" + previousDate + "|"
                + normalize(regionName) + "|" + normalize(categoryName) + "|" + safeLimit, () -> {
            if (shouldUseOlistAnalytics()) {
                List<Map<String, Object>> currentRows = queryOlistProductSellerGmv(statDate, regionName, categoryName);
                List<Map<String, Object>> previousRows = queryOlistProductSellerGmv(previousDate, regionName, categoryName);
                List<Map<String, Object>> merged = mergeProductSellerDelta(currentRows, previousRows);
                if (!merged.isEmpty()) {
                    return merged.stream().limit(safeLimit).toList();
                }
            }

            List<Map<String, Object>> currentRows = queryDemoMerchantGmv(statDate, regionName, categoryName);
            List<Map<String, Object>> previousRows = queryDemoMerchantGmv(previousDate, regionName, categoryName);
            return mergeProductSellerDelta(currentRows, previousRows).stream().limit(safeLimit).toList();
        });
    }

    public List<Map<String, Object>> getBusinessEvidence(LocalDate statDate,
                                                         String regionName,
                                                         String categoryName,
                                                         Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;
        return cachedList("businessEvidence|" + statDate + "|" + normalize(regionName) + "|"
                + normalize(categoryName) + "|" + safeLimit, () -> {
            StringBuilder sql = new StringBuilder(
                    """
                            SELECT stat_date, region_name, category_l1, evidence_domain,
                                   product_id, product_name, seller_id, seller_name,
                                   evidence_signal, previous_metric, current_metric, impact_amount,
                                   suggested_owner, source_tag
                            FROM ads_business_evidence_daily
                            WHERE stat_date = ?
                            """
            );
            java.util.List<Object> args = new java.util.ArrayList<>();
            args.add(Date.valueOf(statDate));
            if (regionName != null && !regionName.isBlank()) {
                sql.append(" AND region_name = ?");
                args.add(regionName);
            }
            if (categoryName != null && !categoryName.isBlank()) {
                sql.append(" AND category_l1 = ?");
                args.add(categoryName);
            }
            sql.append(" ORDER BY ABS(impact_amount) DESC, evidence_domain ASC LIMIT ?");
            args.add(safeLimit);
            return jdbcTemplate.queryForList(sql.toString(), args.toArray());
        });
    }

    private List<Map<String, Object>> queryOlistProductSellerGmv(LocalDate statDate, String regionName, String categoryName) {
        StringBuilder sql = new StringBuilder(
                """
                        SELECT COALESCE(p.category_l1_seed, i.product_category_name, '其他品类') AS category_l1,
                               i.product_id AS product_id,
                               i.seller_id AS seller_id,
                               SUM(COALESCE(i.item_price, 0)) AS gmv,
                               COUNT(DISTINCT i.order_id) AS paid_order_count,
                               'olist_public_dataset' AS source_tag
                        FROM dwd_olist_orders o
                        JOIN dwd_olist_order_items i
                          ON o.order_id = i.order_id
                        LEFT JOIN dim_olist_products p
                          ON i.product_id = p.product_id
                        WHERE CAST(o.order_purchase_timestamp AS DATE) = ?
                        """
        );
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(Date.valueOf(statDate));
        if (regionName != null && !regionName.isBlank()) {
            sql.append("""
                     AND o.customer_state IN (
                         SELECT state_code
                         FROM dim_olist_regions
                         WHERE region_name_seed = ?
                     )
                    """);
            args.add(regionName);
        }
        if (categoryName != null && !categoryName.isBlank()) {
            sql.append(" AND COALESCE(p.category_l1_seed, i.product_category_name, '其他品类') = ?");
            args.add(categoryName);
        }
        sql.append("""
                 GROUP BY COALESCE(p.category_l1_seed, i.product_category_name, '其他品类'), i.product_id, i.seller_id
                """);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> queryDemoMerchantGmv(LocalDate statDate, String regionName, String categoryName) {
        StringBuilder sql = new StringBuilder(
                """
                        SELECT category_l1,
                               merchant_name AS seller_id,
                               merchant_name AS product_id,
                               SUM(pay_amount) AS gmv,
                               COUNT(*) AS paid_order_count,
                               'demo_seed' AS source_tag
                        FROM dwd_orders
                        WHERE CAST(paid_at AS DATE) = ?
                        """
        );
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(Date.valueOf(statDate));
        if (regionName != null && !regionName.isBlank()) {
            sql.append(" AND region_name = ?");
            args.add(regionName);
        }
        if (categoryName != null && !categoryName.isBlank()) {
            sql.append(" AND category_l1 = ?");
            args.add(categoryName);
        }
        sql.append(" GROUP BY category_l1, merchant_name");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> mergeProductSellerDelta(List<Map<String, Object>> currentRows,
                                                             List<Map<String, Object>> previousRows) {
        Map<String, Map<String, Object>> merged = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : previousRows) {
            String key = productSellerKey(row);
            Map<String, Object> item = merged.computeIfAbsent(key, ignored -> baseProductSellerRow(row));
            item.put("previous_gmv", numberValue(row.get("gmv")));
            item.put("previous_paid_order_count", numberValue(row.get("paid_order_count")));
        }
        for (Map<String, Object> row : currentRows) {
            String key = productSellerKey(row);
            Map<String, Object> item = merged.computeIfAbsent(key, ignored -> baseProductSellerRow(row));
            item.put("current_gmv", numberValue(row.get("gmv")));
            item.put("current_paid_order_count", numberValue(row.get("paid_order_count")));
        }
        for (Map<String, Object> item : merged.values()) {
            double currentGmv = numberValue(item.get("current_gmv"));
            double previousGmv = numberValue(item.get("previous_gmv"));
            item.put("gmv_delta", currentGmv - previousGmv);
            item.putIfAbsent("current_gmv", 0D);
            item.putIfAbsent("previous_gmv", 0D);
            item.putIfAbsent("current_paid_order_count", 0D);
            item.putIfAbsent("previous_paid_order_count", 0D);
        }
        return merged.values().stream()
                .sorted(java.util.Comparator.comparingDouble(row -> numberValue(row.get("gmv_delta"))))
                .toList();
    }

    private Map<String, Object> baseProductSellerRow(Map<String, Object> row) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("category_l1", row.get("category_l1"));
        item.put("product_id", row.get("product_id"));
        item.put("seller_id", row.get("seller_id"));
        item.put("source_tag", row.getOrDefault("source_tag", "demo_seed"));
        item.put("current_gmv", 0D);
        item.put("previous_gmv", 0D);
        item.put("current_paid_order_count", 0D);
        item.put("previous_paid_order_count", 0D);
        return item;
    }

    private String productSellerKey(Map<String, Object> row) {
        return String.valueOf(row.get("category_l1")) + "|"
                + row.get("product_id") + "|"
                + row.get("seller_id");
    }

    private double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    public List<Map<String, Object>> getFunnelMetrics(LocalDate statDate, String regionName, String categoryName) {
        return cachedList("funnelMetrics|" + statDate + "|" + normalize(regionName) + "|" + normalize(categoryName), () -> {
            // category 过滤需要 JOIN，暂不支持 Olist 路径，回退 demo
            if (shouldUseOlistAnalytics() && (categoryName == null || categoryName.isBlank())) {
                return queryOlistFunnelMetrics(statDate, regionName);
            }
            StringBuilder sql = new StringBuilder(
                    """
                            SELECT CAST(event_time AS DATE) AS stat_date,
                                   SUM(CASE WHEN event_type = 'view' THEN 1 ELSE 0 END) AS view_count,
                                   SUM(CASE WHEN event_type = 'pay' THEN 1 ELSE 0 END) AS pay_count,
                                   'demo_seed' AS source_tag
                            FROM dwd_user_events
                            WHERE CAST(event_time AS DATE) = ?
                            """
            );
            java.util.List<Object> args = new java.util.ArrayList<>();
            args.add(Date.valueOf(statDate));
            if (regionName != null && !regionName.isBlank()) {
                sql.append(" AND region_name = ?");
                args.add(regionName);
            }
            if (categoryName != null && !categoryName.isBlank()) {
                sql.append(" AND category_l1 = ?");
                args.add(categoryName);
            }
            sql.append(" GROUP BY CAST(event_time AS DATE)");
            return jdbcTemplate.queryForList(sql.toString(), args.toArray());
        });
    }

    private List<Map<String, Object>> queryOlistFunnelMetrics(LocalDate statDate, String regionName) {
        if (regionName != null && !regionName.isBlank()) {
            return jdbcTemplate.queryForList(
                    """
                    SELECT CAST(order_purchase_timestamp AS DATE) AS stat_date,
                           COUNT(*) AS view_count,
                           SUM(CASE WHEN order_status NOT IN ('canceled', 'unavailable') THEN 1 ELSE 0 END) AS pay_count,
                           CAST(SUM(CASE WHEN order_status NOT IN ('canceled', 'unavailable') THEN 1 ELSE 0 END) AS DOUBLE)
                               / NULLIF(COUNT(*), 0) AS view_to_pay_rate,
                           'olist_public_dataset' AS source_tag
                    FROM dwd_olist_orders
                    WHERE CAST(order_purchase_timestamp AS DATE) = ?
                      AND region_name_seed = ?
                    GROUP BY CAST(order_purchase_timestamp AS DATE)
                    """,
                    Date.valueOf(statDate), regionName);
        }
        return jdbcTemplate.queryForList(
                """
                SELECT CAST(order_purchase_timestamp AS DATE) AS stat_date,
                       COUNT(*) AS view_count,
                       SUM(CASE WHEN order_status NOT IN ('canceled', 'unavailable') THEN 1 ELSE 0 END) AS pay_count,
                       CAST(SUM(CASE WHEN order_status NOT IN ('canceled', 'unavailable') THEN 1 ELSE 0 END) AS DOUBLE)
                           / NULLIF(COUNT(*), 0) AS view_to_pay_rate,
                       'olist_public_dataset' AS source_tag
                FROM dwd_olist_orders
                WHERE CAST(order_purchase_timestamp AS DATE) = ?
                GROUP BY CAST(order_purchase_timestamp AS DATE)
                """,
                Date.valueOf(statDate));
    }

    public String getFunnelAnalyticsSource() {
        return shouldUseOlistAnalytics() ? "olist_public_dataset" : "demo_seed";
    }

    private List<Map<String, Object>> cachedList(String cacheKey, Supplier<List<Map<String, Object>>> loader) {
        if (!properties.isReadCacheEnabled() || properties.getReadCacheTtlSeconds() <= 0) {
            cacheSkips.incrementAndGet();
            return loader.get();
        }
        CacheEntry cached = readCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            cacheHits.incrementAndGet();
            return new ArrayList<>(cached.rows());
        }
        cacheMisses.incrementAndGet();
        List<Map<String, Object>> rows = loader.get();
        if (rows.isEmpty() && !properties.isReadCacheCacheEmptyResults()) {
            cacheSkips.incrementAndGet();
            return rows;
        }
        if (readCache.size() >= properties.getReadCacheMaxEntries()) {
            pruneExpiredCacheEntries(now);
        }
        if (readCache.size() >= properties.getReadCacheMaxEntries()) {
            cacheSkips.incrementAndGet();
            return rows;
        }
        readCache.put(cacheKey, new CacheEntry(List.copyOf(rows), now.plusSeconds(properties.getReadCacheTtlSeconds())));
        return rows;
    }

    private void pruneExpiredCacheEntries(Instant now) {
        readCache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "*" : value;
    }

    private void assertReadOnlySql(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL must not be null");
        }
        String normalized = sql
                .replaceAll("/\\*.*?\\*/", " ")
                .replaceAll("--.*?(\\r?\\n|$)", " ")
                .trim()
                .toUpperCase();
        if (!(normalized.startsWith("SELECT") || normalized.startsWith("WITH"))) {
            throw new IllegalArgumentException("Only read-only SELECT/WITH SQL is allowed");
        }
        for (String keyword : WRITE_KEYWORDS) {
            if (normalized.matches(".*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException("Write keyword detected in read-only query: " + keyword);
            }
        }
    }

    private record CacheEntry(List<Map<String, Object>> rows, Instant expiresAt) {
    }
}
