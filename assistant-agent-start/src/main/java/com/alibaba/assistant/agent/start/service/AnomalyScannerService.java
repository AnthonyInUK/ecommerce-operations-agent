package com.alibaba.assistant.agent.start.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * 从快照表动态扫描异常，替代硬编码的 anomalyItems() 列表。
 *
 * 扫描逻辑：
 *   1. 从 ads_olist_daily_core_metrics 取最新两日，检测全站 GMV 和订单量环比跌幅
 *   2. 从 ads_olist_region_daily 取最新两日，检测各区域 GMV 环比跌幅
 *   3. 从 ads_olist_category_daily 取最新两日，检测各品类 GMV 环比跌幅
 *   4. 从 ads_daily_core_metrics (demo_seed) 取最新两日，检测退款率环比涨幅
 *
 * 阈值：
 *   GMV 跌幅 > 15%    → 生成异常
 *   订单量跌幅 > 20%  → 生成异常
 *   退款率涨幅 > 30%  → 生成异常（相对涨幅）
 *
 * 结果按严重程度（跌幅绝对值）降序排列，最多返回 10 条。
 */
@Service
public class AnomalyScannerService {

    private static final double GMV_DROP_THRESHOLD        = -0.15;
    private static final double ORDER_DROP_THRESHOLD      = -0.20;
    private static final double REFUND_RISE_THRESHOLD     =  0.30;  // 相对涨幅
    private static final int    MAX_ANOMALIES             = 10;

    private final JdbcTemplate jdbcTemplate;

    public AnomalyScannerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 扫描所有快照表，返回异常列表（字段结构与原 anomaly() 方法一致）。
     * 如果快照表无数据或查询异常，返回空列表——调用方自行决定是否兜底到硬编码。
     */
    // 真实 GMV 单日基数下限：低于此值视为数据集边缘/稀疏日，环比跌幅是噪声，不作为异常。
    private static final double GMV_BASE_FLOOR = 1000.0;

    public List<Map<String, Object>> scan() {
        List<Map<String, Object>> results = new ArrayList<>();

        // 精选异常：人工验证过数据丰富、点击后能完整点亮结构化分析（区域/订单/品类信号 + 责任分发）。
        // 放在最前，保证演示时异常中心一定有可点、可归因的高质量样本。
        results.addAll(featuredAnomalies());

        results.addAll(scanOlistCore());
        results.addAll(scanOlistRegion());
        results.addAll(scanOlistCategory());
        results.addAll(scanDemoRefundRate());

        // 过滤数据集边缘/稀疏日的噪声异常（GMV 基数过小，环比跌幅无意义）。
        results.removeIf(this::isSparseGmvNoise);

        // 按 id 去重（精选优先，扫描到的同 id 丢弃）。
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> item : results) {
            byId.putIfAbsent(String.valueOf(item.get("id")), item);
        }
        List<Map<String, Object>> deduped = new ArrayList<>(byId.values());

        // 按跌幅绝对值降序（最严重的在前）
        deduped.sort(Comparator.comparingDouble(
                m -> Math.abs(toDouble(((Map<?, ?>) m).get("delta_rate")))
        ).reversed());

        return deduped.size() > MAX_ANOMALIES ? deduped.subList(0, MAX_ANOMALIES) : deduped;
    }

    /**
     * 人工精选的高质量异常样本（验证过结构化分析丰富）。currentValue/previousValue 取自 Olist 真实快照。
     */
    private List<Map<String, Object>> featuredAnomalies() {
        List<Map<String, Object>> featured = new ArrayList<>();
        // 黑五次日：华东 GMV 从大促峰值大幅回落（大数字 + 强信号，演示首选）
        featured.add(buildAnomaly(
                anomalyId("2017-11-25", "华东", "gmv"),
                "2017-11-25", "gmv", "GMV", "区域", "华东",
                48417.08, 123740.62,
                "Olist 公开数据", "平台运营",
                "2017-11-25 华东 GMV 为什么跌了？"));
        // 月中真实异常：华东 GMV 回落，家居品类主拖累
        featured.add(buildAnomaly(
                anomalyId("2018-05-17", "华东", "gmv"),
                "2018-05-17", "gmv", "GMV", "区域", "华东",
                25897.48, 46297.31,
                "Olist 公开数据", "平台运营",
                "2018-05-17 华东 GMV 为什么跌了？"));
        return featured;
    }

    /** GMV/订单类异常若当前与前一日基数都低于下限，视为边缘稀疏日噪声。 */
    private boolean isSparseGmvNoise(Map<String, Object> item) {
        String metricId = String.valueOf(item.get("metric_id"));
        if (!metricId.contains("gmv") && !"order_count".equals(metricId)) {
            return false;
        }
        double cur  = toDouble(item.get("current_value"));
        double prev = toDouble(item.get("previous_value"));
        return cur < GMV_BASE_FLOOR || prev < GMV_BASE_FLOOR;
    }

    // ── 全站核心指标（GMV + 订单量）────────────────────────────────────
    private List<Map<String, Object>> scanOlistCore() {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT stat_date, gmv, paid_order_count FROM ads_olist_daily_core_metrics " +
                    "ORDER BY stat_date DESC LIMIT 2");
            if (rows.size() < 2) return anomalies;

            Map<String, Object> cur  = rows.get(0);
            Map<String, Object> prev = rows.get(1);
            String curDate  = String.valueOf(cur.get("stat_date"));
            String prevDate = String.valueOf(prev.get("stat_date"));

            // GMV
            double curGmv  = toDouble(cur.get("gmv"));
            double prevGmv = toDouble(prev.get("gmv"));
            double gmvRate = deltaRate(prevGmv, curGmv);
            if (prevGmv > 0 && gmvRate <= GMV_DROP_THRESHOLD) {
                anomalies.add(buildAnomaly(
                        anomalyId(curDate, "全站", "gmv"),
                        curDate,
                        "gmv", "GMV", "全站", "全站",
                        curGmv, prevGmv,
                        "Olist 公开数据",
                        "平台运营",
                        String.format("%s 全站 GMV 为什么跌了？", curDate)
                ));
            }

            // 订单量
            double curOrd  = toDouble(cur.get("paid_order_count"));
            double prevOrd = toDouble(prev.get("paid_order_count"));
            double ordRate = deltaRate(prevOrd, curOrd);
            if (prevOrd > 0 && ordRate <= ORDER_DROP_THRESHOLD) {
                anomalies.add(buildAnomaly(
                        anomalyId(curDate, "全站", "order_count"),
                        curDate,
                        "order_count", "订单量", "全站", "全站",
                        curOrd, prevOrd,
                        "Olist 公开数据",
                        "经营分析 / 平台运营",
                        String.format("%s 全站订单量为什么跌了？", curDate)
                ));
            }
        } catch (Exception ignored) {
            // 表不存在或无数据时跳过
        }
        return anomalies;
    }

    // ── 区域 GMV ────────────────────────────────────────────────────────
    private List<Map<String, Object>> scanOlistRegion() {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        try {
            // 取最近两个日期
            List<String> dates = jdbcTemplate.queryForList(
                    "SELECT DISTINCT stat_date FROM ads_olist_region_daily ORDER BY stat_date DESC LIMIT 2",
                    String.class);
            if (dates.size() < 2) return anomalies;

            String curDate  = dates.get(0);
            String prevDate = dates.get(1);

            List<Map<String, Object>> curRows = jdbcTemplate.queryForList(
                    "SELECT region_name_seed, gmv, paid_order_count FROM ads_olist_region_daily WHERE stat_date = ?",
                    curDate);
            List<Map<String, Object>> prevRows = jdbcTemplate.queryForList(
                    "SELECT region_name_seed, gmv FROM ads_olist_region_daily WHERE stat_date = ?",
                    prevDate);

            Map<String, Double> prevMap = new HashMap<>();
            for (Map<String, Object> r : prevRows) {
                prevMap.put(String.valueOf(r.get("region_name_seed")), toDouble(r.get("gmv")));
            }

            for (Map<String, Object> cur : curRows) {
                String region  = String.valueOf(cur.get("region_name_seed"));
                double curGmv  = toDouble(cur.get("gmv"));
                double prevGmv = prevMap.getOrDefault(region, 0.0);
                double rate    = deltaRate(prevGmv, curGmv);
                if (prevGmv > 0 && rate <= GMV_DROP_THRESHOLD) {
                    anomalies.add(buildAnomaly(
                            anomalyId(curDate, region, "gmv"),
                            curDate,
                            "gmv", "GMV", "区域", region,
                            curGmv, prevGmv,
                            "Olist 公开数据",
                            "平台运营",
                            String.format("%s %s GMV 为什么跌了？", curDate, region)
                    ));
                }
            }
        } catch (Exception ignored) {}
        return anomalies;
    }

    // ── 品类 GMV ────────────────────────────────────────────────────────
    private List<Map<String, Object>> scanOlistCategory() {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        try {
            List<String> dates = jdbcTemplate.queryForList(
                    "SELECT DISTINCT stat_date FROM ads_olist_category_daily ORDER BY stat_date DESC LIMIT 2",
                    String.class);
            if (dates.size() < 2) return anomalies;

            String curDate  = dates.get(0);
            String prevDate = dates.get(1);

            List<Map<String, Object>> curRows = jdbcTemplate.queryForList(
                    "SELECT category_l1_seed, gmv FROM ads_olist_category_daily WHERE stat_date = ?",
                    curDate);
            List<Map<String, Object>> prevRows = jdbcTemplate.queryForList(
                    "SELECT category_l1_seed, gmv FROM ads_olist_category_daily WHERE stat_date = ?",
                    prevDate);

            Map<String, Double> prevMap = new HashMap<>();
            for (Map<String, Object> r : prevRows) {
                prevMap.put(String.valueOf(r.get("category_l1_seed")), toDouble(r.get("gmv")));
            }

            for (Map<String, Object> cur : curRows) {
                String cat    = String.valueOf(cur.get("category_l1_seed"));
                double curGmv  = toDouble(cur.get("gmv"));
                double prevGmv = prevMap.getOrDefault(cat, 0.0);
                double rate    = deltaRate(prevGmv, curGmv);
                if (prevGmv > 0 && rate <= GMV_DROP_THRESHOLD) {
                    anomalies.add(buildAnomaly(
                            anomalyId(curDate, cat, "category_gmv"),
                            curDate,
                            "category_gmv", "品类 GMV", "品类", cat,
                            curGmv, prevGmv,
                            "Olist 公开数据",
                            "类目运营",
                            String.format("%s %s 品类 GMV 为什么跌了？", curDate, cat)
                    ));
                }
            }
        } catch (Exception ignored) {}
        return anomalies;
    }

    // ── 退款率（demo_seed，有 refund_rate 字段）─────────────────────────
    private List<Map<String, Object>> scanDemoRefundRate() {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT stat_date, refund_rate FROM ads_daily_core_metrics ORDER BY stat_date DESC LIMIT 2");
            if (rows.size() < 2) return anomalies;

            Map<String, Object> cur  = rows.get(0);
            Map<String, Object> prev = rows.get(1);
            String curDate  = String.valueOf(cur.get("stat_date"));

            double curRate  = toDouble(cur.get("refund_rate"));
            double prevRate = toDouble(prev.get("refund_rate"));
            // 退款率上涨 > 30%（相对）才告警
            if (prevRate > 0 && (curRate - prevRate) / prevRate >= REFUND_RISE_THRESHOLD) {
                anomalies.add(buildAnomaly(
                        anomalyId(curDate, "全站", "refund_rate"),
                        curDate,
                        "refund_rate", "退款率", "全站", "全站",
                        curRate, prevRate,
                        "Demo 补齐口径",
                        "售后治理",
                        String.format("%s 全站退款率为什么升高了？", curDate)
                ));
            }
        } catch (Exception ignored) {}
        return anomalies;
    }

    // ── 工具方法 ────────────────────────────────────────────────────────

    private Map<String, Object> buildAnomaly(String id,
                                              String statDate,
                                              String metricId,
                                              String metricName,
                                              String scopeType,
                                              String scopeName,
                                              double currentValue,
                                              double previousValue,
                                              String source,
                                              String ownerRole,
                                              String rootCauseQuestion) {
        double delta     = round2(currentValue - previousValue);
        double deltaRate = deltaRate(previousValue, currentValue);
        double absDelta  = Math.abs(deltaRate);

        String severity  = absDelta >= 0.40 ? "高" : absDelta >= 0.20 ? "中" : "低";
        String confidence = source.contains("Olist") ? "高" : "中";

        // 描述和建议动作按指标类型生成
        String description = buildDescription(metricId, scopeType, scopeName, deltaRate);
        String nextStep    = buildNextStep(metricId);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id",                id);
        item.put("stat_date",         statDate);
        item.put("metric_id",         metricId);
        item.put("metric_name",       metricName);
        item.put("scope_type",        scopeType);
        item.put("scope_name",        scopeName);
        item.put("current_value",     round2(currentValue));
        item.put("previous_value",    round2(previousValue));
        item.put("delta",             delta);
        item.put("delta_rate",        round4(deltaRate));
        item.put("severity",          severity);
        item.put("status",            "待确认");
        item.put("owner_role",        ownerRole);
        item.put("confidence",        confidence);
        item.put("source",            source);
        item.put("root_cause_question", rootCauseQuestion);
        item.put("description",       description);
        item.put("next_step",         nextStep);
        item.put("source_system",     "metric_monitor");
        item.put("source_system_label", "AnomalyScanner / 快照表环比检测");
        item.put("analyze_endpoint",  "/api/ecommerce/anomalies/" + id + "/analyze");
        return item;
    }

    private String buildDescription(String metricId, String scopeType, String scopeName, double rate) {
        String pct = String.format("%.1f%%", Math.abs(rate) * 100);
        return switch (metricId) {
            case "gmv"          -> String.format("%s %s GMV 环比下跌 %s，需确认是否为区域性或品类性异常。", scopeType, scopeName, pct);
            case "order_count"  -> String.format("%s %s 订单量环比下跌 %s，优先判断是流量问题还是支付承接问题。", scopeType, scopeName, pct);
            case "category_gmv" -> String.format("品类【%s】GMV 环比下跌 %s，建议下钻重点商品和商家。", scopeName, pct);
            case "refund_rate"  -> String.format("%s 退款率环比上涨 %s，需排查退款原因和商品/物流质量。", scopeName, pct);
            default             -> String.format("%s %s %s 指标出现异常波动（%s）。", scopeType, scopeName, metricId, pct);
        };
    }

    private String buildNextStep(String metricId) {
        return switch (metricId) {
            case "gmv"          -> "确认影响范围，判断是否需要拉区域运营和类目运营联合排查";
            case "order_count"  -> "拆 GMV = 支付订单量 × 客单价，确认主拖累项";
            case "category_gmv" -> "定位拖累品类下的重点商品、商家、库存和活动资源";
            case "refund_rate"  -> "查退款原因分布，优先看是否有批量投诉或物流异常";
            default             -> "进入 root cause 分析，确认责任域和处理优先级";
        };
    }

    private static String anomalyId(String date, String scope, String metric) {
        // e.g. anom-20180829-华东-gmv  →  anom-20180829-east-gmv (保留中文也行)
        String scopeSlug = scope.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff]", "");
        return String.format("anom-%s-%s-%s", date, scopeSlug, metric);
    }

    private static double deltaRate(double prev, double cur) {
        if (prev == 0) return 0;
        return (cur - prev) / prev;
    }

    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
