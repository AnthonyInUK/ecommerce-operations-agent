package com.alibaba.assistant.agent.start.service;

import com.alibaba.assistant.agent.start.config.AppOperationsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RootCauseAnalysisBuilder {

    private final AppOperationsProperties operationsProperties;

    @Autowired
    public RootCauseAnalysisBuilder(AppOperationsProperties operationsProperties) {
        this.operationsProperties = operationsProperties;
    }

    public RootCauseAnalysisResult build(String regionName,
                                         LocalDate statDate,
                                         LocalDate previousDate,
                                         Map<String, Object> currentRegion,
                                         Map<String, Object> previousRegion,
                                         Map<String, Object> currentOrder,
                                         Map<String, Object> previousOrder,
                                         Map<String, Object> currentUser,
                                         Map<String, Object> previousUser,
                                         Map<String, Object> currentCategory,
                                         Map<String, Object> previousCategory,
                                         Map<String, Object> funnelCurrent,
                                         Map<String, Object> funnelPrevious,
                                         Map<String, Object> refund,
                                         List<Map<String, Object>> productSellerDrilldown,
                                         List<Map<String, Object>> businessEvidence) {
        Map<String, Object> currentOrderRow = firstRow(currentOrder);
        Map<String, Object> previousOrderRow = firstRow(previousOrder);
        Map<String, Object> currentUserRow = firstRow(currentUser);
        Map<String, Object> previousUserRow = firstRow(previousUser);
        List<Map<String, Object>> currentCategoryRows = rows(currentCategory);
        List<Map<String, Object>> previousCategoryRows = rows(previousCategory);
        Map<String, Object> currentRegionRow = regionRowOrFallback(regionName, firstRow(currentRegion), currentOrderRow, currentCategoryRows);
        Map<String, Object> previousRegionRow = regionRowOrFallback(regionName, firstRow(previousRegion), previousOrderRow, previousCategoryRows);
        Map<String, Object> funnelCurrentRow = firstRow(funnelCurrent);
        Map<String, Object> funnelPreviousRow = firstRow(funnelPrevious);
        List<Map<String, Object>> refundRows = rows(refund);
        List<Map<String, Object>> drilldownRows = productSellerDrilldown == null ? List.of() : productSellerDrilldown;
        List<Map<String, Object>> businessEvidenceRows = businessEvidence == null ? List.of() : businessEvidence;

        String regionDataSource = String.valueOf(currentRegion.getOrDefault("data_source", "demo_seed"));
        String orderDataSource = String.valueOf(currentOrder.getOrDefault("data_source", "demo_seed"));
        String userDataSource = String.valueOf(currentUser.getOrDefault("data_source", "demo_seed"));
        String categoryDataSource = String.valueOf(currentCategory.getOrDefault("data_source", "demo_seed"));
        String funnelDataSource = String.valueOf(funnelCurrentRow.getOrDefault("source_tag", "demo_seed"));
        String refundDataSource = refundRows.isEmpty()
                ? "demo_seed"
                : String.valueOf(refundRows.get(0).getOrDefault("source_tag", "demo_seed"));
        String businessEvidenceSource = businessEvidenceRows.isEmpty()
                ? "demo_seed"
                : String.valueOf(businessEvidenceRows.get(0).getOrDefault("source_tag", "demo_seed"));

        RootCauseAnalysisResult.Section regionSection = buildRegionSection(currentRegionRow, previousRegionRow, regionDataSource);
        RootCauseAnalysisResult.Section orderSection = buildOrderSection(currentOrderRow, previousOrderRow, orderDataSource);
        RootCauseAnalysisResult.Section userSection = buildUserSection(currentUserRow, previousUserRow, userDataSource);
        RootCauseAnalysisResult.Section categorySection = buildCategorySection(currentCategoryRows, previousCategoryRows, categoryDataSource);
        RootCauseAnalysisResult.Section businessEvidenceSection = buildBusinessEvidenceSection(businessEvidenceRows, businessEvidenceSource);
        RootCauseAnalysisResult.Section funnelSection = buildFunnelSection(funnelCurrentRow, funnelPreviousRow, funnelDataSource);
        RootCauseAnalysisResult.Section refundSection = buildRefundSection(refundRows, refundDataSource);

        Map<String, Object> dataLineage = Map.of(
                "region_metrics_source", regionDataSource,
                "category_metrics_source", categoryDataSource,
                "order_metrics_source", orderDataSource,
                "user_metrics_source", userDataSource,
                "funnel_metrics_source", funnelDataSource,
                "refund_metrics_source", refundDataSource,
                "business_evidence_source", businessEvidenceSource
        );

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("current_region", currentRegionRow.isEmpty() ? List.of() : List.of(currentRegionRow));
        facts.put("previous_region", previousRegionRow.isEmpty() ? List.of() : List.of(previousRegionRow));
        facts.put("current_order_structure", rows(currentOrder));
        facts.put("previous_order_structure", rows(previousOrder));
        facts.put("current_user_metrics", rows(currentUser));
        facts.put("previous_user_metrics", rows(previousUser));
        facts.put("current_category_breakdown", currentCategoryRows);
        facts.put("previous_category_breakdown", previousCategoryRows);
        facts.put("refund_breakdown", refundRows);
        facts.put("product_seller_drilldown", drilldownRows);
        facts.put("business_evidence", businessEvidenceRows);
        facts.put("current_funnel", rows(funnelCurrent));
        facts.put("previous_funnel", rows(funnelPrevious));
        facts.put("data_lineage", dataLineage);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("region_name", regionName);
        overview.put("stat_date", statDate.toString());
        overview.put("previous_date", previousDate.toString());
        overview.put("previous_gmv", value(previousRegionRow, "GMV"));
        overview.put("current_gmv", value(currentRegionRow, "GMV"));

        String summary = String.format(
                "%s 在 %s 相比 %s 的 GMV 从 %s 下降到 %s。用户：%s；交易：%s；品类：%s；业务证据：%s；漏斗：%s；售后：%s。%s",
                regionName,
                statDate,
                previousDate,
                value(previousRegionRow, "GMV"),
                value(currentRegionRow, "GMV"),
                userSection.summary(),
                orderSection.summary(),
                categorySection.summary(),
                businessEvidenceSection.summary(),
                funnelSection.summary(),
                refundSection.summary(),
                lineageSentence(regionDataSource, orderDataSource, categoryDataSource)
        );
        List<RootCauseAnalysisResult.Section> sections = List.of(regionSection, orderSection, userSection, categorySection, businessEvidenceSection, funnelSection, refundSection);
        List<Map<String, Object>> decisionTrace = buildDecisionTrace(sections);
        List<Map<String, Object>> actionRouting = buildActionRouting(sections, drilldownRows, businessEvidenceRows);
        Map<String, Object> evidenceConfidence = buildEvidenceConfidence(sections, dataLineage);
        double previousGmv = numberValue(value(previousRegionRow, "GMV"));
        double currentGmv  = numberValue(value(currentRegionRow,  "GMV"));
        Map<String, Object> metricBridge = buildMetricBridge(
                currentRegionRow,
                previousRegionRow,
                currentOrderRow,
                previousOrderRow,
                currentUserRow,
                previousUserRow,
                currentCategoryRows,
                previousCategoryRows,
                funnelCurrentRow,
                funnelPreviousRow,
                refundRows
        );
        List<Map<String, Object>> impactDrivers = buildImpactDrivers(metricBridge, drilldownRows, businessEvidenceRows, sections);
        List<Map<String, Object>> verificationPlan = buildVerificationPlan(impactDrivers, actionRouting);
        Map<String, Object> notificationDraft = buildNotificationDraft(regionName, statDate, previousDate, summary, sections, actionRouting, drilldownRows, evidenceConfidence, dataLineage, previousGmv, currentGmv);

        return new RootCauseAnalysisResult(
                summary,
                overview,
                metricBridge,
                impactDrivers,
                verificationPlan,
                sections,
                decisionTrace,
                actionRouting,
                notificationDraft,
                drilldownRows,
                evidenceConfidence,
                dataLineage,
                facts
        );
    }

    private Map<String, Object> regionRowOrFallback(String regionName,
                                                    Map<String, Object> regionRow,
                                                    Map<String, Object> orderRow,
                                                    List<Map<String, Object>> categoryRows) {
        if (regionRow != null && !regionRow.isEmpty()) {
            return regionRow;
        }

        double orderGmv = numberValue(value(orderRow, "GROSS_PAY_AMOUNT"));
        double orderCount = numberValue(value(orderRow, "PAID_ORDER_COUNT"));
        if (orderGmv > 0 || orderCount > 0) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("region_name", regionName);
            fallback.put("gmv", orderGmv);
            fallback.put("paid_order_count", orderCount);
            fallback.put("refund_rate", 0D);
            fallback.put("source_tag", value(orderRow, "SOURCE_TAG") == null ? "demo_seed" : value(orderRow, "SOURCE_TAG"));
            fallback.put("region_fallback_source", "order_structure");
            return fallback;
        }

        double categoryGmv = categoryRows == null ? 0D : categoryRows.stream()
                .mapToDouble(row -> numberValue(value(row, "GMV")))
                .sum();
        double categoryPaidOrders = categoryRows == null ? 0D : categoryRows.stream()
                .mapToDouble(row -> numberValue(value(row, "PAID_ORDER_COUNT")))
                .sum();
        if (categoryGmv > 0 || categoryPaidOrders > 0) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("region_name", regionName);
            fallback.put("gmv", categoryGmv);
            fallback.put("paid_order_count", categoryPaidOrders);
            fallback.put("refund_rate", 0D);
            fallback.put("source_tag", "demo_seed");
            fallback.put("region_fallback_source", "category_sum");
            return fallback;
        }

        return Map.of();
    }

    private Map<String, Object> buildMetricBridge(Map<String, Object> currentRegionRow,
                                                  Map<String, Object> previousRegionRow,
                                                  Map<String, Object> currentOrderRow,
                                                  Map<String, Object> previousOrderRow,
                                                  Map<String, Object> currentUserRow,
                                                  Map<String, Object> previousUserRow,
                                                  List<Map<String, Object>> currentCategoryRows,
                                                  List<Map<String, Object>> previousCategoryRows,
                                                  Map<String, Object> currentFunnelRow,
                                                  Map<String, Object> previousFunnelRow,
                                                  List<Map<String, Object>> refundRows) {
        double previousGmv = numberValue(value(previousRegionRow, "GMV"));
        double currentGmv = numberValue(value(currentRegionRow, "GMV"));
        double totalDelta = currentGmv - previousGmv;
        double previousOrderCount = numberValue(value(previousOrderRow, "ORDER_COUNT"));
        double currentOrderCount = numberValue(value(currentOrderRow, "ORDER_COUNT"));
        double previousAov = numberValue(value(previousOrderRow, "AVG_ORDER_VALUE"));
        double currentAov = numberValue(value(currentOrderRow, "AVG_ORDER_VALUE"));

        double orderCountEffect = (currentOrderCount - previousOrderCount) * previousAov;
        double aovEffect = currentOrderCount * (currentAov - previousAov);
        double explainedByOrderModel = orderCountEffect + aovEffect;
        double residual = totalDelta - explainedByOrderModel;

        List<Map<String, Object>> bridgeComponents = new ArrayList<>();
        bridgeComponents.add(bridgeComponent(
                "paid_order_count",
                "支付订单量效应",
                previousOrderCount,
                currentOrderCount,
                currentOrderCount - previousOrderCount,
                orderCountEffect,
                totalDelta,
                "在前一日客单价不变的假设下，单量变化对 GMV 的影响。"
        ));
        bridgeComponents.add(bridgeComponent(
                "average_order_value",
                "客单价效应",
                previousAov,
                currentAov,
                currentAov - previousAov,
                aovEffect,
                totalDelta,
                "在当前支付订单量下，客单价变化对 GMV 的影响。"
        ));
        bridgeComponents.add(bridgeComponent(
                "model_residual",
                "区域口径残差",
                previousGmv,
                currentGmv,
                totalDelta,
                residual,
                totalDelta,
                "区域 GMV 与订单模型之间的差额，优先核对退款后口径、订单状态和数据同步。"
        ));

        List<Map<String, Object>> categoryContribution = buildCategoryContribution(currentCategoryRows, previousCategoryRows, totalDelta);
        Map<String, Object> funnelBridge = buildFunnelBridge(currentFunnelRow, previousFunnelRow);
        Map<String, Object> userBridge = buildUserBridge(currentUserRow, previousUserRow);
        Map<String, Object> refundBridge = buildRefundBridge(refundRows, Math.abs(totalDelta));

        Map<String, Object> bridge = new LinkedHashMap<>();
        bridge.put("equation", "GMV = paid_order_count * average_order_value");
        bridge.put("previous_gmv", previousGmv);
        bridge.put("current_gmv", currentGmv);
        bridge.put("total_delta", totalDelta);
        bridge.put("drop_rate", previousGmv == 0 ? 0D : totalDelta / previousGmv);
        bridge.put("components", bridgeComponents);
        bridge.put("order_model_explained_delta", explainedByOrderModel);
        bridge.put("order_model_residual", residual);
        bridge.put("category_contribution", categoryContribution);
        bridge.put("user_bridge", userBridge);
        bridge.put("funnel_bridge", funnelBridge);
        bridge.put("refund_bridge", refundBridge);
        bridge.put("diagnostic_summary", metricBridgeSummary(totalDelta, bridgeComponents, categoryContribution, refundBridge));
        return bridge;
    }

    private Map<String, Object> bridgeComponent(String key,
                                                String title,
                                                double previousValue,
                                                double currentValue,
                                                double metricDelta,
                                                double estimatedGmvImpact,
                                                double totalDelta,
                                                String interpretation) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("title", title);
        item.put("previous_value", previousValue);
        item.put("current_value", currentValue);
        item.put("metric_delta", metricDelta);
        item.put("estimated_gmv_impact", estimatedGmvImpact);
        item.put("contribution_rate", contributionRate(estimatedGmvImpact, totalDelta));
        item.put("direction", estimatedGmvImpact < 0 ? "drag" : estimatedGmvImpact > 0 ? "offset" : "neutral");
        item.put("interpretation", interpretation);
        return item;
    }

    private List<Map<String, Object>> buildCategoryContribution(List<Map<String, Object>> currentRows,
                                                               List<Map<String, Object>> previousRows,
                                                               double totalDelta) {
        Map<String, Double> current = new LinkedHashMap<>();
        for (Map<String, Object> row : currentRows) {
            current.put(String.valueOf(value(row, "CATEGORY_L1")), numberValue(value(row, "GMV")));
        }
        Map<String, Double> previous = new LinkedHashMap<>();
        for (Map<String, Object> row : previousRows) {
            previous.put(String.valueOf(value(row, "CATEGORY_L1")), numberValue(value(row, "GMV")));
        }

        Map<String, Double> deltaMap = new LinkedHashMap<>();
        previous.forEach((category, previousGmv) -> deltaMap.put(category, current.getOrDefault(category, 0D) - previousGmv));
        current.forEach((category, currentGmv) -> deltaMap.putIfAbsent(category, currentGmv));

        List<Map<String, Object>> contribution = new ArrayList<>();
        for (Map.Entry<String, Double> entry : deltaMap.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category_l1", entry.getKey());
            item.put("previous_gmv", previous.getOrDefault(entry.getKey(), 0D));
            item.put("current_gmv", current.getOrDefault(entry.getKey(), 0D));
            item.put("gmv_delta", entry.getValue());
            item.put("contribution_rate", contributionRate(entry.getValue(), totalDelta));
            item.put("direction", entry.getValue() < 0 ? "drag" : entry.getValue() > 0 ? "offset" : "neutral");
            contribution.add(item);
        }
        contribution.sort((left, right) -> Double.compare(numberValue(right.get("contribution_rate")), numberValue(left.get("contribution_rate"))));
        return contribution;
    }

    private Map<String, Object> buildUserBridge(Map<String, Object> currentUserRow,
                                                Map<String, Object> previousUserRow) {
        double previousDau = numberValue(value(previousUserRow, "DAU"));
        double currentDau = numberValue(value(currentUserRow, "DAU"));
        double previousBuyer = numberValue(value(previousUserRow, "ACTIVE_BUYER_COUNT"));
        double currentBuyer = numberValue(value(currentUserRow, "ACTIVE_BUYER_COUNT"));
        Map<String, Object> bridge = new LinkedHashMap<>();
        bridge.put("previous_dau", previousDau);
        bridge.put("current_dau", currentDau);
        bridge.put("dau_delta", currentDau - previousDau);
        bridge.put("previous_active_buyer", previousBuyer);
        bridge.put("current_active_buyer", currentBuyer);
        bridge.put("active_buyer_delta", currentBuyer - previousBuyer);
        bridge.put("previous_buyer_activation_rate", previousDau == 0 ? 0D : previousBuyer / previousDau);
        bridge.put("current_buyer_activation_rate", currentDau == 0 ? 0D : currentBuyer / currentDau);
        return bridge;
    }

    private Map<String, Object> buildFunnelBridge(Map<String, Object> currentFunnelRow,
                                                  Map<String, Object> previousFunnelRow) {
        double previousViewToPay = numberValue(value(previousFunnelRow, "VIEW_TO_PAY_RATE"));
        double currentViewToPay = numberValue(value(currentFunnelRow, "VIEW_TO_PAY_RATE"));
        Map<String, Object> bridge = new LinkedHashMap<>();
        bridge.put("previous_view_to_pay", previousViewToPay);
        bridge.put("current_view_to_pay", currentViewToPay);
        bridge.put("rate_delta", currentViewToPay - previousViewToPay);
        bridge.put("direction", currentViewToPay < previousViewToPay ? "drag" : currentViewToPay > previousViewToPay ? "offset" : "neutral");
        return bridge;
    }

    private Map<String, Object> buildRefundBridge(List<Map<String, Object>> refundRows, double totalDropMagnitude) {
        double totalRefundAmount = refundRows.stream()
                .mapToDouble(row -> numberValue(value(row, "REFUND_AMOUNT")))
                .sum();
        Map<String, Object> bridge = new LinkedHashMap<>();
        bridge.put("refund_category_count", refundRows.size());
        bridge.put("total_refund_amount", totalRefundAmount);
        bridge.put("refund_to_drop_ratio", totalDropMagnitude == 0 ? 0D : totalRefundAmount / totalDropMagnitude);
        bridge.put("top_refund_category", refundRows.isEmpty() ? null : value(refundRows.get(0), "CATEGORY_L1"));
        bridge.put("top_refund_amount", refundRows.isEmpty() ? 0D : value(refundRows.get(0), "REFUND_AMOUNT"));
        return bridge;
    }

    private String metricBridgeSummary(double totalDelta,
                                       List<Map<String, Object>> bridgeComponents,
                                       List<Map<String, Object>> categoryContribution,
                                       Map<String, Object> refundBridge) {
        String direction = totalDelta < 0 ? "下滑" : totalDelta > 0 ? "增长" : "持平";
        String topMetric = bridgeComponents.stream()
                .filter(item -> "drag".equals(item.get("direction")))
                .findFirst()
                .map(item -> String.valueOf(item.get("title")))
                .orElse("暂无单一指标拖累");
        String topCategory = categoryContribution.stream()
                .filter(item -> "drag".equals(item.get("direction")))
                .findFirst()
                .map(item -> String.valueOf(item.get("category_l1")))
                .orElse("暂无明显拖累品类");
        return String.format("GMV 本期%s %.2f；指标桥接优先关注%s，品类贡献优先关注%s，退款金额/跌幅比约 %.2f。",
                direction,
                Math.abs(totalDelta),
                topMetric,
                topCategory,
                numberValue(refundBridge.get("refund_to_drop_ratio")));
    }

    private List<Map<String, Object>> buildImpactDrivers(Map<String, Object> metricBridge,
                                                         List<Map<String, Object>> productSellerDrilldown,
                                                         List<Map<String, Object>> businessEvidence,
                                                         List<RootCauseAnalysisResult.Section> sections) {
        List<Map<String, Object>> drivers = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = metricBridge.get("components") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        for (Map<String, Object> component : components) {
            double impact = numberValue(component.get("estimated_gmv_impact"));
            if (impact < 0) {
                drivers.add(driver(
                        "metric_bridge",
                        String.valueOf(component.get("key")),
                        String.valueOf(component.get("title")),
                        impact,
                        numberValue(component.get("contribution_rate")),
                        String.valueOf(component.get("interpretation")),
                        "business_analysis"
                ));
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categoryContribution = metricBridge.get("category_contribution") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        for (Map<String, Object> category : categoryContribution.stream().filter(row -> numberValue(row.get("gmv_delta")) < 0).limit(3).toList()) {
            drivers.add(driver(
                    "category",
                    String.valueOf(category.get("category_l1")),
                    "品类拖累：" + category.get("category_l1"),
                    numberValue(category.get("gmv_delta")),
                    numberValue(category.get("contribution_rate")),
                    "该品类 GMV 环比下降，应继续下钻商品、商家、价格带和库存。",
                    "category_operation"
            ));
        }

        for (Map<String, Object> row : productSellerDrilldown.stream().filter(item -> numberValue(item.get("gmv_delta")) < 0).limit(5).toList()) {
            drivers.add(driver(
                    "product_seller",
                    row.getOrDefault("product_id", "-") + "/" + row.getOrDefault("seller_id", "-"),
                    row.getOrDefault("category_l1", "未知品类") + " 商品/商家下钻",
                    numberValue(row.get("gmv_delta")),
                    contributionRate(numberValue(row.get("gmv_delta")), numberValue(metricBridge.get("total_delta"))),
                    "具体商品或商家成为拖累对象，适合直接分发给类目/商家运营核查。",
                    "category_operation"
            ));
        }

        for (Map<String, Object> row : businessEvidence.stream().limit(5).toList()) {
            double impact = numberValue(value(row, "IMPACT_AMOUNT"));
            drivers.add(driver(
                    "business_evidence",
                    String.valueOf(value(row, "EVIDENCE_DOMAIN")),
                    businessDomainName(String.valueOf(value(row, "EVIDENCE_DOMAIN"))) + "：" + value(row, "EVIDENCE_SIGNAL"),
                    impact,
                    contributionRate(impact, numberValue(metricBridge.get("total_delta"))),
                    "业务证据已落到商品、商家、库存、活动或售后对象。",
                    ownerKeyForEvidence(row)
            ));
        }

        for (RootCauseAnalysisResult.Section section : sections) {
            if ("funnel".equals(section.key()) && "signal".equals(section.status())) {
                drivers.add(driver("funnel", "view_to_pay", "漏斗转化变化", 0D, 0D, section.summary(), "conversion_operation"));
            }
            if ("refund".equals(section.key()) && "signal".equals(section.status())) {
                drivers.add(driver("refund", String.valueOf(section.highlights().getOrDefault("category", "-")), "退款压力", -numberValue(section.highlights().get("refund_amount")), 0D, section.summary(), "after_sales_governance"));
            }
        }

        drivers.sort((left, right) -> Double.compare(numberValue(right.get("score")), numberValue(left.get("score"))));
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (int index = 0; index < drivers.size(); index++) {
            Map<String, Object> item = new LinkedHashMap<>(drivers.get(index));
            item.put("rank", index + 1);
            ranked.add(item);
        }
        return ranked;
    }

    private Map<String, Object> driver(String type,
                                       String key,
                                       String title,
                                       double impactAmount,
                                       double contributionRate,
                                       String evidence,
                                       String ownerKey) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("key", key);
        item.put("title", title);
        item.put("impact_amount", impactAmount);
        item.put("contribution_rate", contributionRate);
        item.put("direction", impactAmount < 0 ? "drag" : impactAmount > 0 ? "offset" : "signal");
        item.put("score", Math.abs(impactAmount) * (impactAmount < 0 ? 1.2 : 0.6) + Math.abs(contributionRate) * 1000);
        item.put("evidence", evidence);
        item.put("owner_key", ownerKey);
        item.put("owner_contact", ownerContactFor(ownerKey));
        return item;
    }

    private List<Map<String, Object>> buildVerificationPlan(List<Map<String, Object>> impactDrivers,
                                                            List<Map<String, Object>> actionRouting) {
        List<Map<String, Object>> plan = new ArrayList<>();
        for (Map<String, Object> driver : impactDrivers.stream().limit(5).toList()) {
            String ownerKey = String.valueOf(driver.getOrDefault("owner_key", "business_analysis"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("driver_rank", driver.get("rank"));
            item.put("driver_title", driver.get("title"));
            item.put("owner_key", ownerKey);
            item.put("owner_contact", ownerContactFor(ownerKey));
            item.put("question_to_verify", verificationQuestionFor(driver));
            item.put("data_to_pull", verificationDataFor(ownerKey));
            item.put("decision_rule", verificationDecisionRuleFor(driver));
            item.put("suggested_route", actionRouting.stream()
                    .filter(route -> ownerKey.equals(route.get("owner_key")))
                    .findFirst()
                    .orElse(Map.of("owner_key", ownerKey, "owner_name", ownerKey)));
            plan.add(item);
        }
        return plan;
    }

    private String verificationQuestionFor(Map<String, Object> driver) {
        return switch (String.valueOf(driver.get("type"))) {
            case "metric_bridge" -> "这个指标桥接项是否能解释 GMV 主跌幅，是否存在订单状态或统计口径变化？";
            case "category" -> "该品类下跌是否集中在少数商品/商家/价格带，还是全品类同步回落？";
            case "product_seller" -> "该商品/商家的库存、价格、活动资源、履约和售后是否在异常日发生变化？";
            case "business_evidence" -> "业务证据对应的活动、库存、履约或售后事件是否真实发生，并覆盖异常时间窗？";
            case "funnel" -> "浏览、下单、支付三段漏斗中具体是哪一段承接变弱？";
            case "refund" -> "退款是否来自商品质量、物流履约或客服处理集中爆发？";
            default -> "该线索是否能被明细数据复核？";
        };
    }

    private List<String> verificationDataFor(String ownerKey) {
        return switch (ownerKey) {
            case "business_analysis" -> List.of("订单明细", "支付状态", "退款后口径", "近 7 日和上周同日基线");
            case "category_operation" -> List.of("品类-商品-商家 GMV 明细", "库存", "价格", "活动资源位", "竞品价格");
            case "conversion_operation" -> List.of("曝光/浏览", "加购/下单", "支付成功率", "页面和券链路日志");
            case "after_sales_governance" -> List.of("退款原因", "物流履约", "客服工单", "商品质量反馈");
            case "growth_operation" -> List.of("渠道流量", "投放预算", "素材", "活动曝光", "人群包");
            default -> List.of("指标明细", "业务事件", "数据口径说明");
        };
    }

    private String verificationDecisionRuleFor(Map<String, Object> driver) {
        double contributionRate = numberValue(driver.get("contribution_rate"));
        if (contributionRate >= 0.5) {
            return "若复核后贡献率仍超过 50%，作为主因进入 P0 处理。";
        }
        if (contributionRate >= 0.2) {
            return "若复核后贡献率超过 20%，作为重要次因同步对应负责人。";
        }
        return "若明细无法复现，则降级为观察线索，不进入通知主文案。";
    }

    private String ownerKeyForEvidence(Map<String, Object> row) {
        String owner = String.valueOf(value(row, "SUGGESTED_OWNER"));
        if (owner.contains("售后") || owner.contains("治理")) {
            return "after_sales_governance";
        }
        if (owner.contains("增长") || owner.contains("营销")) {
            return "growth_operation";
        }
        if (owner.contains("转化")) {
            return "conversion_operation";
        }
        if (owner.contains("类目") || owner.contains("商家")) {
            return "category_operation";
        }
        return "business_analysis";
    }

    private double contributionRate(double impactAmount, double totalDelta) {
        if (totalDelta == 0) {
            return 0D;
        }
        if (impactAmount < 0 && totalDelta < 0) {
            return Math.abs(impactAmount) / Math.abs(totalDelta);
        }
        if (impactAmount > 0 && totalDelta < 0) {
            return -impactAmount / Math.abs(totalDelta);
        }
        return impactAmount / totalDelta;
    }

    private Map<String, Object> buildEvidenceConfidence(List<RootCauseAnalysisResult.Section> sections,
                                                        Map<String, Object> dataLineage) {
        Map<String, Object> confidence = new LinkedHashMap<>();
        List<Map<String, Object>> sectionConfidence = new ArrayList<>();
        for (RootCauseAnalysisResult.Section section : sections) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", section.key());
            item.put("title", section.title());
            item.put("source", section.source());
            item.put("confidence", computeConfidenceScore(section.source(), section.status(), section.highlights()));
            item.put("note", confidenceNote(section.source(), section.status()));
            sectionConfidence.add(item);
        }
        confidence.put("overall", overallConfidence(sectionConfidence));
        confidence.put("sections", sectionConfidence);
        confidence.put("lineage_note", "区域/订单/品类优先使用 Olist 公开数据；用户/漏斗/退款仍是 demo 补齐口径，适合展示分析链路，不应包装成真实生产全量数据。");
        confidence.put("data_lineage", dataLineage);
        return confidence;
    }

    Map<String, String> ownerContactFor(String ownerKey) {
        return switch (ownerKey) {
            case "platform_operation" -> Map.of(
                "contact_name", "张伟（平台运营负责人）",
                "group",        "电商平台运营群",
                "feishu_webhook", "mock://feishu/platform-ops",
                "mention",      "@张伟"
            );
            case "category_operation" -> Map.of(
                "contact_name", "李娜（类目运营负责人）",
                "group",        "类目运营群",
                "feishu_webhook", "mock://feishu/category-ops",
                "mention",      "@李娜"
            );
            case "growth_operation"  -> Map.of(
                "contact_name", "王强（增长运营负责人）",
                "group",        "增长运营群",
                "feishu_webhook", "mock://feishu/growth-ops",
                "mention",      "@王强"
            );
            case "business_analysis"  -> Map.of(
                "contact_name", "赵敏（业务分析负责人）",
                "group",        "业务分析群",
                "feishu_webhook", "mock://feishu/business-analysis",
                "mention",      "@赵敏"
            );
            case "conversion_operation" -> Map.of(
                "contact_name", "陈浩（转化运营负责人）",
                "group",        "转化运营群",
                "feishu_webhook", "mock://feishu/conversion-ops",
                "mention",      "@陈浩"
            );
            case "after_sales_governance" -> Map.of(
                "contact_name", "刘洋（售后治理负责人）",
                "group",        "售后治理群",
                "feishu_webhook", "mock://feishu/after-sales-governance",
                "mention",      "@刘洋"
            );
            default -> Map.of(
                "contact_name", "数据分析师",
                "group",        "数据分析群",
                "feishu_webhook", "mock://feishu/data-analysis",
                "mention",      "@数据分析师"
            );
        };
    }


    Map<String, Object> computeConfidenceScore(
        String source, String status, Map<String, Object> highlights) {
        if ("insufficient".equals(status)) {
            Map<String, Object> zero = new LinkedHashMap<>();
            zero.put("score", 0);
            zero.put("level", "low");
            zero.put("source_score", 0);
            zero.put("dimension_score", 0);
            zero.put("magnitude_score", 0);
            zero.put("baseline_score", 0);
            return zero;
        }
        int sourceScore = 0;
        // 维度1：数据来源质量（0-40）
        // olist=40, demo=20, 其他=0
        if ("olist_public_dataset".equalsIgnoreCase(source)) {
            sourceScore = 40;
        } else if ("demo_seed".equals(source)) {
            sourceScore = 20;
        }else{
            sourceScore = 0;
        }

        // 维度2：维度贡献度（0-25）
        // 判断标准：status=signal 且 highlights 非空 → 25
        //           status=stable 且 highlights 非空 → 15
        //           insufficient 或 highlights 为空   → 0
        int dimensionScore = 0;

        if("signal".equals(status) && highlights != null && !highlights.isEmpty()) {
            dimensionScore = 25;
        } else if("stable".equals(status) && highlights != null && !highlights.isEmpty()) {
            dimensionScore = 15;
        } else {
            dimensionScore = 0;
        }

        // 维度3：异常幅度（0-25）
        // 判断标准：signal → 20, stable → 8, insufficient → 0
        int magnitudeScore = 0;
        if("signal".equals(status)) {
            magnitudeScore = 20;
        } else if("stable".equals(status)) {
            magnitudeScore = 8;
        } else {
            magnitudeScore = 0;
        }

        // 维度4：多基线一致性（0-10）
        // 当前没有多基线数据，signal→7, stable→4, insufficient→0
        int baselineScore = 0;
        if("signal".equals(status)) {
            baselineScore = 7;
        } else if("stable".equals(status)) {
            baselineScore = 4;
        } else {
            baselineScore = 0;
        }

        int total = sourceScore + dimensionScore + magnitudeScore + baselineScore;
        String level = total >= 70 ? "high" : total >= 40 ? "medium" : "low";

        Map<String, Object> score = new LinkedHashMap<>();
        score.put("score", total);
        score.put("level", level);
        score.put("source_score", sourceScore);
        score.put("dimension_score", dimensionScore);
        score.put("magnitude_score", magnitudeScore);
        score.put("baseline_score", baselineScore);
        return score;
    }


    private String confidenceNote(String source, String status) {
        if ("insufficient".equals(status)) {
            return "当前证据不足，只能作为待补充线索。";
        }
        if ("olist_public_dataset".equals(source)) {
            return "来自公开 Olist 订单明细加工后的指标，适合承载区域/订单/品类判断。";
        }
        if ("demo_seed".equals(source)) {
            return "来自逻辑补齐 demo 口径，适合展示分析链路，但需要真实行为/售后数据进一步替换。";
        }
        return "来源未明确，需谨慎解释。";
    }

    private String overallConfidence(List<Map<String, Object>> sectionConfidence) {
        double avgScore = sectionConfidence.stream()
        .mapToInt(item -> {
            Object conf = item.get("confidence");
            if (conf instanceof Map<?, ?> map) {
                return ((Number) map.get("score")).intValue();
            }
            return 0;
        })
        .average()
        .orElse(0);
    return avgScore >= 70 ? "high" : avgScore >= 40 ? "medium" : "low";
    }

    private Map<String, Object> buildNotificationDraft(String regionName,
                                                       LocalDate statDate,
                                                       LocalDate previousDate,
                                                       String summary,
                                                       List<RootCauseAnalysisResult.Section> sections,
                                                       List<Map<String, Object>> actionRouting,
                                                       List<Map<String, Object>> productSellerDrilldown,
                                                       Map<String, Object> evidenceConfidence,
                                                       Map<String, Object> dataLineage,
                                                       double previousGmv,
                                                       double currentGmv) {
        List<RootCauseAnalysisResult.Section> signalSections = sections.stream()
                .filter(section -> "signal".equals(section.status()))
                .toList();
        Map<String, Object> notificationGate = buildNotificationGate(signalSections, evidenceConfidence, previousGmv, currentGmv);
        String title = "【经营异常待排查】" + statDate + " " + regionName + " GMV 回落";
        String severity = signalSections.size() >= 4 ? "high" : signalSections.size() >= 2 ? "medium" : "low";
        String receiverText = actionRouting.stream()
                .map(route -> String.valueOf(route.get("owner_name")))
                .distinct()
                .reduce((left, right) -> left + "、" + right)
                .orElse("数据分析师");
        List<String> evidenceLines = signalSections.stream()
                .map(section -> "【" + section.title() + "】" + section.summary())
                .toList();
        String body = buildNotificationBody(title, summary, receiverText, evidenceLines, actionRouting,
                productSellerDrilldown, evidenceConfidence, dataLineage);

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("channel", "feishu_webhook_draft");
        draft.put("title", title);
        draft.put("severity", severity);
        draft.put("target_roles", actionRouting.stream()
                .map(route -> route.get("owner_name"))
                .distinct()
                .toList());
        draft.put("evidence", evidenceLines);
        draft.put("next_actions", actionRouting.stream()
                .map(route -> route.get("next_action"))
                .distinct()
                .toList());
        draft.put("action_routing", actionRouting);
        draft.put("drilldown", productSellerDrilldown);
        draft.put("evidence_confidence", evidenceConfidence);
        draft.put("confidence", notificationGate.get("confidence"));
        draft.put("notify_recommendation", notificationGate.get("recommendation"));
        draft.put("notify_recommendation_text", notificationGate.get("recommendation_text"));
        draft.put("manual_confirmation_required", notificationGate.get("manual_confirmation_required"));
        draft.put("auto_send_allowed", notificationGate.get("auto_send_allowed"));
        draft.put("body", body);
        draft.put("send_policy", "默认只生成草稿；接入飞书前需要通过幂等、去重、只读和降级保护。");
        return draft;
    }

    private Map<String, Object> buildNotificationGate(List<RootCauseAnalysisResult.Section> signalSections,
                                                      Map<String, Object> evidenceConfidence,
                                                      double previousGmv,
                                                      double currentGmv) {
        AppOperationsProperties.GmvDropWatch cfg = operationsProperties.getGmvDropWatch();
        double absoluteDrop = previousGmv - currentGmv;
        double dropRate     = previousGmv > 0 ? absoluteDrop / previousGmv : 0;

        boolean businessSignificant = absoluteDrop >= cfg.getMinNotifyAbsoluteGmvDrop()
                && dropRate >= cfg.getMinNotifyDropRate();

        String overall = String.valueOf(evidenceConfidence.getOrDefault("overall", "medium"));
        long highSignalCount = signalSections.stream()
                .filter(section -> "high".equals(computeConfidenceScore(section.source(), section.status(), section.highlights()).get("level")))
                .count();
        long mediumSignalCount = signalSections.stream()
                .filter(section -> "medium".equals(computeConfidenceScore(section.source(), section.status(), section.highlights()).get("level")))
                .count();

        String confidence;
        String recommendation;
        String recommendationText;

        if (!businessSignificant) {
            confidence = "low";
            recommendation = "log_only";
            recommendationText = String.format(
                "业务显著性不足（绝对跌幅 %.0f，跌幅比例 %.1f%%），仅记录，不建议推送。",
                absoluteDrop, dropRate * 100);
        } else if (signalSections.size() >= 3 && highSignalCount >= 3 && mediumSignalCount == 0 && "high".equals(overall)) {
            confidence = "high";
            recommendation = "recommend_notify";
            recommendationText = "高可信：建议通知，但仍需要人工确认后发送。";
        } else if (signalSections.size() >= 2 && highSignalCount + mediumSignalCount >= 2) {
            confidence = "medium";
            recommendation = "review_before_send";
            recommendationText = "中可信：建议人工复核后通知。";
        } else {
            confidence = "low";
            recommendation = "log_only";
            recommendationText = "低可信：仅记录，不建议推送。";
        }

        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("confidence", confidence);
        gate.put("recommendation", recommendation);
        gate.put("recommendation_text", recommendationText);
        gate.put("absolute_drop", absoluteDrop);
        gate.put("drop_rate", dropRate);
        gate.put("business_significant", businessSignificant);
        gate.put("manual_confirmation_required", true);
        gate.put("auto_send_allowed", false);
        gate.put("reason", "异常通知默认不自动发送；Agent 负责发现和组织证据，最终发布需要人工确认。");
        return gate;
    }

    private String buildNotificationBody(String title,
                                         String summary,
                                         String receiverText,
                                         List<String> evidenceLines,
                                         List<Map<String, Object>> actionRouting,
                                         List<Map<String, Object>> productSellerDrilldown,
                                         Map<String, Object> evidenceConfidence,
                                         Map<String, Object> dataLineage) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n\n");
        builder.append("核心结论：").append(removeLineageSentence(summary)).append("\n\n");
        builder.append("建议同步：").append(receiverText).append("\n\n");
        builder.append("关键证据：\n");
        if (evidenceLines.isEmpty()) {
            builder.append("- 当前没有明显单点信号，需要扩大时间窗口继续观察。\n");
        }
        else {
            for (String line : evidenceLines) {
                builder.append("- ").append(line).append("\n");
            }
        }
        builder.append("\n责任分发：\n");
        if (actionRouting == null || actionRouting.isEmpty()) {
            builder.append("- 当前没有明确责任角色，需要数据分析师补充证据后再分发。\n");
        }
        else {
            for (Map<String, Object> route : actionRouting) {
                builder.append("\n【").append(route.getOrDefault("owner_name", "业务负责人")).append("】")
                        .append("优先级：").append(route.getOrDefault("priority", "P2")).append("\n");
                builder.append("问题：").append(route.getOrDefault("problem", route.getOrDefault("reason", "待确认问题"))).append("\n");
                builder.append("证据：").append(route.getOrDefault("evidence", "证据不足")).append("\n");
                builder.append("建议动作：").append(route.getOrDefault("next_action", "补充证据后继续排查")).append("\n");
                List<?> drilldownObjects = route.get("drilldown_objects") instanceof List<?> list ? list : List.of();
                if (!drilldownObjects.isEmpty()) {
                    builder.append("下钻对象：\n");
                    drilldownObjects.stream().limit(3).forEach(item -> builder.append("- ").append(item).append("\n"));
                }
                builder.append("期望产出：").append(route.getOrDefault("expected_output", route.getOrDefault("output_format", "输出排查结论"))).append("\n");
            }
        }
        if (productSellerDrilldown != null && !productSellerDrilldown.isEmpty()) {
            builder.append("\n优先下钻对象：\n");
            productSellerDrilldown.stream().limit(3).forEach(row -> builder.append("- ")
                    .append(row.get("category_l1"))
                    .append(" / product_id=").append(row.get("product_id"))
                    .append(" / seller_id=").append(row.get("seller_id"))
                    .append(" / GMV变化=").append(formatDecimal(numberValue(row.get("gmv_delta")), 2))
                    .append("\n"));
        }
        builder.append("\n数据口径：区域/订单/品类=")
                .append(describeDataSource(String.valueOf(dataLineage.get("region_metrics_source"))))
                .append("；用户/漏斗/退款=")
                .append(describeDataSource(String.valueOf(dataLineage.get("user_metrics_source"))))
                .append("。");
        builder.append("\n可信度：").append(evidenceConfidence.getOrDefault("overall", "medium"))
                .append("。").append(evidenceConfidence.getOrDefault("lineage_note", ""));
        return builder.toString();
    }

    private String removeLineageSentence(String summary) {
        return summary
                .replaceAll("当前区域/订单/品类判断分别使用[^。]*。", "")
                .replaceAll("区域、订单、品类判断[^。]*。", "")
                .replace("用户/漏斗/退款仍沿主链数据。", "")
                .trim();
    }

    private List<Map<String, Object>> buildDecisionTrace(List<RootCauseAnalysisResult.Section> sections) {
        List<Map<String, Object>> trace = new ArrayList<>();
        for (RootCauseAnalysisResult.Section section : sections) {
            trace.add(decisionStep(section.key(), section.title(), decisionQuestion(section.key()),
                    decisionConclusion(section), decisionMeaning(section), section.source()));
        }
        return trace;
    }

    private Map<String, Object> decisionStep(String key,
                                             String title,
                                             String question,
                                             String conclusion,
                                             String meaning,
                                             String source) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("title", title);
        item.put("question", question);
        item.put("conclusion", conclusion);
        item.put("meaning", meaning);
        item.put("source", source);
        return item;
    }

    private String decisionQuestion(String key) {
        return switch (key) {
            case "region" -> "异常是否集中在目标区域？";
            case "order_structure" -> "GMV 下滑是单量问题还是客单价问题？";
            case "user_scale" -> "用户盘子是否先走弱？";
            case "category_drag" -> "哪个品类对下滑贡献最大？";
            case "business_evidence" -> "能否落到商品、商家、库存、活动或售后对象？";
            case "funnel" -> "转化承接有没有明显恶化？";
            case "refund" -> "售后退款是否成为压力来源？";
            default -> "这一层是否提供了有效证据？";
        };
    }

    private String decisionConclusion(RootCauseAnalysisResult.Section section) {
        return switch (section.status()) {
            case "signal" -> "发现信号：" + section.summary();
            case "stable" -> "暂未发现明显恶化：" + section.summary();
            case "insufficient" -> "证据不足：" + section.summary();
            default -> section.summary();
        };
    }

    private String decisionMeaning(RootCauseAnalysisResult.Section section) {
        return switch (section.key()) {
            case "region" -> "如果这一层有信号，说明问题值得进入多维排查，而不是只看大盘均值。";
            case "order_structure" -> "这一层决定后续优先排查单量、支付口径还是客单价。";
            case "user_scale" -> "这一层帮助区分是用户规模变小，还是用户来了但没有转化。";
            case "category_drag" -> "这一层会把问题落到具体品类，方便类目或行业运营接手。";
            case "business_evidence" -> "这一层把归因从指标变化推进到商品、商家、库存、履约、活动和售后对象。";
            case "funnel" -> "这一层用于判断前链路承接是否掉了，避免把流量问题误判成商品问题。";
            case "refund" -> "这一层用于判断售后压力是否在吞掉成交质量。";
            default -> "这一层用于补充 root cause 的证据链。";
        };
    }

    private List<Map<String, Object>> buildActionRouting(List<RootCauseAnalysisResult.Section> sections,
                                                         List<Map<String, Object>> productSellerDrilldown,
                                                         List<Map<String, Object>> businessEvidence) {
        List<Map<String, Object>> routing = new ArrayList<>();
        for (RootCauseAnalysisResult.Section section : sections) {
            if (!"signal".equals(section.status())) {
                continue;
            }
            switch (section.key()) {
                case "region" -> routing.add(route(
                        "platform_operation",
                        "平台运营",
                        "目标区域大盘出现回落",
                        section.summary(),
                        drilldownObjectsFor("platform_operation", section, productSellerDrilldown)
                ));
                case "order_structure" -> routing.add(route(
                        "business_analysis",
                        "经营分析 / 平台运营",
                        "交易结构出现信号",
                        section.summary(),
                        drilldownObjectsFor("business_analysis", section, productSellerDrilldown)
                ));
                case "user_scale" -> routing.add(route(
                        "growth_operation",
                        "增长 / 营销运营",
                        "用户规模或活跃买家出现回落",
                        section.summary(),
                        drilldownObjectsFor("growth_operation", section, productSellerDrilldown)
                ));
                case "category_drag" -> routing.add(route(
                        "category_operation",
                        "类目 / 行业运营",
                        "品类拖累明显",
                        section.summary(),
                        drilldownObjectsFor("category_operation", section, productSellerDrilldown)
                ));
                case "business_evidence" -> routing.add(route(
                        "category_operation",
                        "类目 / 商家运营",
                        "商品、商家、库存或活动证据指向明确对象",
                        section.summary(),
                        businessEvidenceObjects(businessEvidence)
                ));
                case "funnel" -> routing.add(route(
                        "conversion_operation",
                        "增长 / 商品转化运营",
                        "漏斗承接出现变化",
                        section.summary(),
                        drilldownObjectsFor("conversion_operation", section, productSellerDrilldown)
                ));
                case "refund" -> routing.add(route(
                        "after_sales_governance",
                        "售后 / 治理运营",
                        "退款或售后压力出现信号",
                        section.summary(),
                        drilldownObjectsFor("after_sales_governance", section, productSellerDrilldown)
                ));
                default -> {
                    // Unknown sections are intentionally ignored until they have a business owner.
                }
            }
        }
        if (routing.isEmpty()) {
            routing.add(route(
                    "business_analysis",
                    "数据分析师",
                    "当前没有明显单点信号",
                    "需要继续观察或扩大时间窗口。",
                    List.of("补充近 7 日趋势、同环比基线、渠道/商品/售后明细")
            ));
        }
        return sortRoutingByPriority(routing);
    }

    private List<Map<String, Object>> sortRoutingByPriority(List<Map<String, Object>> routing) {
        List<Map<String, Object>> sorted = new ArrayList<>(routing);
        sorted.sort((left, right) -> Integer.compare(priorityRank(left.get("priority")), priorityRank(right.get("priority"))));
        return sorted;
    }

    private int priorityRank(Object priority) {
        String value = String.valueOf(priority);
        if ("P0".equals(value)) {
            return 0;
        }
        if ("P1".equals(value)) {
            return 1;
        }
        if ("P2".equals(value)) {
            return 2;
        }
        return 99;
    }

    private Map<String, Object> route(String ownerKey,
                                      String ownerName,
                                      String reason,
                                      String evidence,
                                      List<String> drilldownObjects) {
        Map<String, Object> item = new LinkedHashMap<>();
        List<String> actionPlan = actionPlanFor(ownerKey);
        item.put("owner_key", ownerKey);
        item.put("owner_name", ownerName);
        item.put("owner_contact", ownerContactFor(ownerKey));
        item.put("priority", priorityFor(ownerKey));
        item.put("reason", reason);
        item.put("problem", problemFor(ownerKey, evidence));
        item.put("evidence", evidence);
        item.put("investigation_checklist", investigationChecklistFor(ownerKey));
        item.put("next_action", String.join(" ", actionPlan));
        item.put("action_plan", actionPlan);
        item.put("output_format", outputFormatFor(ownerKey));
        item.put("expected_output", outputFormatFor(ownerKey));
        item.put("drilldown_objects", drilldownObjects == null ? List.of() : drilldownObjects);
        return item;
    }

    private List<String> actionPlanFor(String ownerKey) {
        return switch (ownerKey) {
            case "platform_operation" -> List.of(
                    "1. 先确认目标区域 GMV 下滑是否只发生在当前区域，还是同步影响大盘。",
                    "2. 对比近 7 日、上周同日、去年同期和活动/节假日基线，判断是否为真实异常。",
                    "3. 拉齐类目运营、增长运营、售后治理分别补商品、流量、退款证据。",
                    "4. 输出一版“是否升级处理 + 主责角色 + 下一步处理建议”。"
            );
            case "business_analysis" -> List.of(
                    "1. 先拆 GMV = 支付订单量 x 客单价，确认主要拖累来自单量、客单价还是支付口径。",
                    "2. 下钻订单状态、取消订单、支付订单和退款后口径，排除统计口径变化。",
                    "3. 同步平台运营、类目运营和增长运营，对齐订单变化是否由活动、商品或流量引起。",
                    "4. 输出 GMV 拆解表、主因判断和需要继续验证的指标口径。"
            );
            case "growth_operation" -> List.of(
                    "1. 先确认 DAU、活跃买家和买家激活率分别是否回落，区分流量少了还是转化弱了。",
                    "2. 下钻渠道流量、活动曝光、人群触达、投放预算和素材节奏。",
                    "3. 同步平台运营和类目运营，确认是否存在活动资源、推荐流量或品类供给变化。",
                    "4. 输出流量/人群/活动变化说明和投放排查结论。"
            );
            case "category_operation" -> List.of(
                    "1. 先锁定拖累最大的品类，并按商品、商家、价格带和库存继续下钻。",
                    "2. 检查活动资源、推荐坑位、库存缺货、价格变化和竞品价格是否发生变化。",
                    "3. 同步商家运营、平台运营和售后治理，确认是否需要补资源、调价或联系商家。",
                    "4. 输出拖累品类、重点商品/商家清单和资源调整建议。"
            );
            case "conversion_operation" -> List.of(
                    "1. 先拆浏览、下单、支付三个环节，定位是前链路流量问题还是支付承接问题。",
                    "2. 下钻详情页承接、优惠力度、搜索推荐流量质量和支付链路异常。",
                    "3. 同步商品、增长和技术侧，确认是否存在页面、券、支付或推荐链路问题。",
                    "4. 输出漏斗断点、影响页面/链路和修复优先级。"
            );
            case "after_sales_governance" -> List.of(
                    "1. 先定位退款压力集中的品类、商品、商家和订单批次。",
                    "2. 下钻退款原因、物流履约、客服处理、商品质量和退换货时间分布。",
                    "3. 同步类目运营、商家运营和客服/物流侧，确认是否进入专项治理。",
                    "4. 输出退款集中对象、售后原因和治理动作建议。"
            );
            default -> List.of(
                    "1. 先补充近 7 日趋势、同环比基线、渠道、商品和售后明细。",
                    "2. 判断是否存在稳定异常信号，再决定是否分发给业务负责人。",
                    "3. 同步数据分析师复核指标口径和数据来源。",
                    "4. 输出补充证据和待确认问题。"
            );
        };
    }

    private String problemFor(String ownerKey, String evidence) {
        return switch (ownerKey) {
            case "platform_operation" -> "目标区域经营大盘出现回落，需要确认影响范围和是否升级。";
            case "business_analysis" -> "交易结构出现异常，需要判断 GMV 下滑来自订单量、客单价还是支付口径。";
            case "growth_operation" -> "用户规模或活跃买家回落，需要确认流量、人群触达和活动曝光是否走弱。";
            case "category_operation" -> "重点品类成为 GMV 拖累，需要定位到商品、商家、库存和活动资源。";
            case "conversion_operation" -> "漏斗承接出现变化，需要确认浏览、下单、支付链路的断点。";
            case "after_sales_governance" -> "售后退款压力出现信号，需要确认退款是否来自商品质量、履约或客服处理。";
            default -> evidence == null || evidence.isBlank() ? "当前问题仍需补充证据。" : evidence;
        };
    }

    private List<String> drilldownObjectsFor(String ownerKey,
                                             RootCauseAnalysisResult.Section section,
                                             List<Map<String, Object>> productSellerDrilldown) {
        return switch (ownerKey) {
            case "platform_operation" -> List.of("区域：" + section.highlights().getOrDefault("previous_gmv", "-")
                    + " -> " + section.highlights().getOrDefault("current_gmv", "-") + " GMV");
            case "business_analysis" -> List.of(
                    "订单量：" + section.highlights().getOrDefault("previous_order_count", "-")
                            + " -> " + section.highlights().getOrDefault("current_order_count", "-"),
                    "客单价：" + formatDecimal(numberValue(section.highlights().get("previous_aov")), 2)
                            + " -> " + formatDecimal(numberValue(section.highlights().get("current_aov")), 2)
            );
            case "growth_operation" -> List.of(
                    "DAU：" + section.highlights().getOrDefault("previous_dau", "-")
                            + " -> " + section.highlights().getOrDefault("current_dau", "-"),
                    "活跃买家：" + section.highlights().getOrDefault("previous_active_buyer", "-")
                            + " -> " + section.highlights().getOrDefault("current_active_buyer", "-")
            );
            case "category_operation" -> businessObjects(productSellerDrilldown, "优先商品/商家");
            case "business_evidence" -> businessObjects(productSellerDrilldown, "优先商品/商家");
            case "conversion_operation" -> List.of("view->pay 转化：" + section.highlights().getOrDefault("previous_view_to_pay", "-")
                    + " -> " + section.highlights().getOrDefault("current_view_to_pay", "-"));
            case "after_sales_governance" -> List.of("退款品类：" + section.highlights().getOrDefault("category", "-"),
                    "退款金额：" + section.highlights().getOrDefault("refund_amount", "-"),
                    "退款金额占比：" + section.highlights().getOrDefault("refund_amount_rate", "-"));
            default -> List.of();
        };
    }

    private List<String> businessObjects(List<Map<String, Object>> productSellerDrilldown, String fallbackPrefix) {
        if (productSellerDrilldown == null || productSellerDrilldown.isEmpty()) {
            return List.of(fallbackPrefix + "：待补充商品/商家明细");
        }
        return productSellerDrilldown.stream()
                .limit(3)
                .map(row -> String.valueOf(row.getOrDefault("category_l1", "未知品类"))
                        + " / product_id=" + row.getOrDefault("product_id", "-")
                        + " / seller_id=" + row.getOrDefault("seller_id", "-")
                        + " / GMV变化=" + formatDecimal(numberValue(row.get("gmv_delta")), 2))
                .toList();
    }

    private List<String> businessEvidenceObjects(List<Map<String, Object>> businessEvidence) {
        if (businessEvidence == null || businessEvidence.isEmpty()) {
            return List.of("业务证据：待补充商品/商家/库存/活动/售后明细");
        }
        return businessEvidence.stream()
                .limit(4)
                .map(row -> businessDomainName(String.valueOf(value(row, "EVIDENCE_DOMAIN")))
                        + " / " + value(row, "PRODUCT_NAME")
                        + " / " + value(row, "SELLER_NAME")
                        + " / " + value(row, "EVIDENCE_SIGNAL"))
                .toList();
    }

    private String priorityFor(String ownerKey) {
        return switch (ownerKey) {
            case "platform_operation", "business_analysis", "category_operation" -> "P0";
            case "growth_operation", "conversion_operation", "after_sales_governance" -> "P1";
            default -> "P2";
        };
    }

    private List<String> investigationChecklistFor(String ownerKey) {
        return switch (ownerKey) {
            case "platform_operation" -> List.of(
                    "确认异常是否只集中在目标区域，还是同步影响大盘。",
                    "对比同日活动、节假日、流量入口和平台资源位变化。",
                    "拉齐类目、增长、售后负责人，先定是否需要升级处理。"
            );
            case "business_analysis" -> List.of(
                    "拆 GMV = 支付订单量 x 客单价，确认主要拖累项。",
                    "核对支付订单口径、取消订单和退款后口径是否发生变化。",
                    "补充同环比、近 7 日均值和去年同期基线。"
            );
            case "growth_operation" -> List.of(
                    "检查渠道流量、活动曝光和人群触达是否回落。",
                    "对比 DAU、活跃买家和买家激活率，区分流量问题和转化问题。",
                    "核对投放预算、素材、活动节奏和人群包变更。"
            );
            case "category_operation" -> List.of(
                    "定位拖累品类下的重点商品、商家和价格带。",
                    "检查库存、活动资源、推荐坑位和竞品价格变化。",
                    "给出是否需要补资源、调价或联系商家的处理建议。"
            );
            case "conversion_operation" -> List.of(
                    "检查详情页承接、优惠力度、搜索推荐流量质量和支付链路。",
                    "区分是浏览少了、下单少了，还是支付环节掉了。",
                    "同步商品、增长和技术侧确认是否有链路异常。"
            );
            case "after_sales_governance" -> List.of(
                    "排查对应品类或商家的退款原因、物流履约和客服处理。",
                    "确认是否存在商品质量、发货延迟或异常退换货集中爆发。",
                    "必要时进入商家治理或售后专项跟进。"
            );
            default -> List.of("补充更多证据后再确认负责人。");
        };
    }

    private String outputFormatFor(String ownerKey) {
        return switch (ownerKey) {
            case "platform_operation" -> "输出区域异常影响范围、是否升级、协同负责人名单。";
            case "business_analysis" -> "输出 GMV 拆解表、主因判断和下一步验证 SQL/指标口径。";
            case "growth_operation" -> "输出流量/人群/活动变化说明和投放排查结论。";
            case "category_operation" -> "输出拖累品类、重点商品/商家清单和资源调整建议。";
            case "conversion_operation" -> "输出漏斗断点、影响页面/链路和修复优先级。";
            case "after_sales_governance" -> "输出退款集中对象、售后原因和治理动作建议。";
            default -> "输出补充证据和待确认问题。";
        };
    }

    private RootCauseAnalysisResult.Section buildRegionSection(Map<String, Object> currentRegionRow,
                                                              Map<String, Object> previousRegionRow,
                                                              String source) {
        if (currentRegionRow == null || currentRegionRow.isEmpty() || previousRegionRow == null || previousRegionRow.isEmpty()) {
            return section("region", "区域表现", "insufficient", "当前区域证据不足，无法确认异常是否集中在目标区域", source, Map.of());
        }
        double currentGmv = numberValue(value(currentRegionRow, "GMV"));
        double previousGmv = numberValue(value(previousRegionRow, "GMV"));
        String summary = currentGmv < previousGmv
                ? String.format("目标区域 GMV 明显回落（%.2f -> %.2f）", previousGmv, currentGmv)
                : String.format("目标区域 GMV 未继续走弱（%.2f -> %.2f）", previousGmv, currentGmv);
        return section("region", "区域表现", currentGmv < previousGmv ? "signal" : "stable", summary, source,
                Map.of("previous_gmv", previousGmv, "current_gmv", currentGmv));
    }

    private RootCauseAnalysisResult.Section buildOrderSection(Map<String, Object> currentOrderRow,
                                                             Map<String, Object> previousOrderRow,
                                                             String source) {
        if (currentOrderRow == null || currentOrderRow.isEmpty() || previousOrderRow == null || previousOrderRow.isEmpty()) {
            return section("order_structure", "订单结构", "insufficient", "订单结构证据不足，当前还不能稳定判断是单量问题还是客单价问题", source, Map.of());
        }
        double currentOrderCount = numberValue(value(currentOrderRow, "ORDER_COUNT"));
        double previousOrderCount = numberValue(value(previousOrderRow, "ORDER_COUNT"));
        double currentAov = numberValue(value(currentOrderRow, "AVG_ORDER_VALUE"));
        double previousAov = numberValue(value(previousOrderRow, "AVG_ORDER_VALUE"));
        boolean orderCountDown = currentOrderCount < previousOrderCount;
        boolean aovDown = currentAov < previousAov;
        String summary;
        String status = "stable";
        if (orderCountDown && aovDown) {
            summary = String.format("订单量和客单价同时走弱（订单量 %.0f -> %.0f，客单价 %.2f -> %.2f）", previousOrderCount, currentOrderCount, previousAov, currentAov);
            status = "signal";
        }
        else if (orderCountDown) {
            summary = String.format("主要是单量走弱（订单量 %.0f -> %.0f），客单价相对稳定（%.2f -> %.2f）", previousOrderCount, currentOrderCount, previousAov, currentAov);
            status = "signal";
        }
        else if (aovDown) {
            summary = String.format("主要是客单价走弱（%.2f -> %.2f），订单量没有明显拖累（%.0f -> %.0f）", previousAov, currentAov, previousOrderCount, currentOrderCount);
            status = "signal";
        }
        else {
            summary = String.format("订单结构整体相对稳定（订单量 %.0f -> %.0f，客单价 %.2f -> %.2f）", previousOrderCount, currentOrderCount, previousAov, currentAov);
        }
        return section("order_structure", "订单结构", status, summary, source,
                Map.of("previous_order_count", previousOrderCount, "current_order_count", currentOrderCount,
                        "previous_aov", previousAov, "current_aov", currentAov));
    }

    private RootCauseAnalysisResult.Section buildUserSection(Map<String, Object> currentUserRow,
                                                            Map<String, Object> previousUserRow,
                                                            String source) {
        if (currentUserRow == null || currentUserRow.isEmpty() || previousUserRow == null || previousUserRow.isEmpty()) {
            return section("user_scale", "用户规模", "insufficient", "用户规模证据不足，当前还不能稳定判断用户盘子是否先走弱", source, Map.of());
        }
        double currentDau = numberValue(value(currentUserRow, "DAU"));
        double previousDau = numberValue(value(previousUserRow, "DAU"));
        double currentBuyer = numberValue(value(currentUserRow, "ACTIVE_BUYER_COUNT"));
        double previousBuyer = numberValue(value(previousUserRow, "ACTIVE_BUYER_COUNT"));
        boolean dauDown = currentDau < previousDau;
        boolean buyerDown = currentBuyer < previousBuyer;
        String summary;
        String status = "stable";
        if (dauDown && buyerDown) {
            summary = String.format("用户规模先走弱（DAU %.0f -> %.0f，活跃买家 %.0f -> %.0f）", previousDau, currentDau, previousBuyer, currentBuyer);
            status = "signal";
        }
        else if (dauDown) {
            summary = String.format("流量盘子有回落（DAU %.0f -> %.0f），但活跃买家相对稳定（%.0f -> %.0f）", previousDau, currentDau, previousBuyer, currentBuyer);
            status = "signal";
        }
        else if (buyerDown) {
            summary = String.format("活跃买家回落更明显（%.0f -> %.0f），DAU 没有同步恶化（%.0f -> %.0f）", previousBuyer, currentBuyer, previousDau, currentDau);
            status = "signal";
        }
        else {
            summary = String.format("用户规模整体稳定（DAU %.0f -> %.0f，活跃买家 %.0f -> %.0f）", previousDau, currentDau, previousBuyer, currentBuyer);
        }
        return section("user_scale", "用户规模", status, summary, source,
                Map.of("previous_dau", previousDau, "current_dau", currentDau,
                        "previous_active_buyer", previousBuyer, "current_active_buyer", currentBuyer));
    }

    RootCauseAnalysisResult.Section buildCategorySection(List<Map<String, Object>> currentRows,
                                                                List<Map<String, Object>> previousRows,
                                                                String source) {
        if ((currentRows == null || currentRows.isEmpty()) && (previousRows == null || previousRows.isEmpty())) {
            return section("category_drag", "品类拖累", "insufficient", "当前品类证据不足", source, Map.of());
        }
        Map<String, Double> current = new LinkedHashMap<>();
        for (Map<String, Object> row : currentRows) {
            current.put(String.valueOf(value(row, "CATEGORY_L1")), numberValue(value(row, "GMV")));
        }
        Map<String, Double> previous = new LinkedHashMap<>();
        for (Map<String, Object> row : previousRows) {
            previous.put(String.valueOf(value(row, "CATEGORY_L1")), numberValue(value(row, "GMV")));
        }
        Map<String, Double> deltaMap = new LinkedHashMap<>();
        previous.forEach((cat, prev) -> {
            double curr = current.getOrDefault(cat, 0D);
            deltaMap.put(cat, curr - prev);
        });
        current.forEach((cat, curr) -> {
            if (!deltaMap.containsKey(cat)) {
                deltaMap.put(cat, curr);
            }
        });

        double totalDrop = deltaMap.values().stream()
                .filter(d -> d < 0)
                .mapToDouble(Double::doubleValue)
                .sum();

        double offsetGmv = deltaMap.values().stream()
                .filter(d -> d > 0)
                .mapToDouble(Double::doubleValue)
                .sum();

        if (totalDrop == 0) {
            return section("category_drag", "品类拖累", "stable", "暂无明显品类拖累", source,
                    Map.of("current_categories", currentRows.size(), "previous_categories", previousRows.size()));
        }

        String topCategory = null;
        double topContribution = 0D;
        for (Map.Entry<String, Double> entry : deltaMap.entrySet()) {
            if (entry.getValue() < 0) {
                double contribution = entry.getValue() / totalDrop;
                if (contribution > topContribution) {
                    topContribution = contribution;
                    topCategory = entry.getKey();
                }
            }
        }

        String summary = String.format("%s 贡献了 %.0f%% 的下跌（下降 %.0f 万）",
                topCategory, topContribution * 100, Math.abs(deltaMap.get(topCategory)));
        if (offsetGmv > 0) {
            summary += String.format("，其他品类上涨合计对冲了 %.0f 万", offsetGmv);
        }

        return section("category_drag", "品类拖累", "signal", summary, source,
                Map.of("top_category", topCategory,
                        "top_contribution_rate", topContribution,
                        "total_drop", totalDrop,
                        "offset_gmv", offsetGmv,
                        "current_categories", currentRows.size(),
                        "previous_categories", previousRows.size()));
    }

    private RootCauseAnalysisResult.Section buildBusinessEvidenceSection(List<Map<String, Object>> evidenceRows,
                                                                         String source) {
        if (evidenceRows == null || evidenceRows.isEmpty()) {
            return section("business_evidence", "业务证据", "insufficient",
                    "商品/商家、库存/履约、营销/活动、售后/退款证据尚未补齐", source, Map.of());
        }

        Map<String, Object> topEvidence = evidenceRows.get(0);
        String domainSummary = evidenceRows.stream()
                .map(row -> businessDomainName(String.valueOf(value(row, "EVIDENCE_DOMAIN"))))
                .distinct()
                .reduce((left, right) -> left + "、" + right)
                .orElse("业务证据");
        String summary = String.format("%s 证据已补齐，优先信号是%s：%s（%s -> %s）",
                domainSummary,
                value(topEvidence, "SUGGESTED_OWNER"),
                value(topEvidence, "EVIDENCE_SIGNAL"),
                value(topEvidence, "PREVIOUS_METRIC"),
                value(topEvidence, "CURRENT_METRIC"));
        return section("business_evidence", "业务证据", "signal", summary, source,
                Map.of(
                        "evidence_count", evidenceRows.size(),
                        "top_domain", value(topEvidence, "EVIDENCE_DOMAIN"),
                        "top_owner", value(topEvidence, "SUGGESTED_OWNER"),
                        "top_signal", value(topEvidence, "EVIDENCE_SIGNAL"),
                        "top_product", value(topEvidence, "PRODUCT_NAME"),
                        "top_seller", value(topEvidence, "SELLER_NAME"),
                        "impact_amount", value(topEvidence, "IMPACT_AMOUNT")
                ));
    }

    private String businessDomainName(String domain) {
        return switch (domain) {
            case "product_seller" -> "商品/商家";
            case "inventory_fulfillment" -> "库存/履约";
            case "marketing_campaign" -> "营销/活动";
            case "after_sales_refund" -> "售后/退款";
            default -> "业务补齐";
        };
    }

    private RootCauseAnalysisResult.Section buildFunnelSection(Map<String, Object> currentFunnelRow,
                                                              Map<String, Object> previousFunnelRow,
                                                              String source) {
        if (currentFunnelRow == null || currentFunnelRow.isEmpty() || previousFunnelRow == null || previousFunnelRow.isEmpty()) {
            return section("funnel", "漏斗线索", "insufficient", "当前漏斗证据不足，无法确认问题是否来自前链路承接", source, Map.of());
        }
        double currentFunnelRate = numberValue(value(currentFunnelRow, "VIEW_TO_PAY_RATE"));
        double previousFunnelRate = numberValue(value(previousFunnelRow, "VIEW_TO_PAY_RATE"));
        boolean funnelStable = Math.abs(currentFunnelRate - previousFunnelRate) < 0.0001;
        String summary = funnelStable
                ? String.format("view->pay 转化基本稳定（前一日 %.2f，当前 %.2f）", previousFunnelRate, currentFunnelRate)
                : String.format("view->pay 转化发生变化（前一日 %.2f，当前 %.2f）", previousFunnelRate, currentFunnelRate);
        return section("funnel", "漏斗线索", funnelStable ? "stable" : "signal", summary, source,
                Map.of("previous_view_to_pay", previousFunnelRate, "current_view_to_pay", currentFunnelRate));
    }

    private RootCauseAnalysisResult.Section buildRefundSection(List<Map<String, Object>> refundRows,
                                                              String source) {
        if (refundRows == null || refundRows.isEmpty()) {
            return section("refund", "退款线索", "stable", "退款没有成为主导因素", source, Map.of());
        }
        Map<String, Object> topRefund = refundRows.get(0);
        String summary = String.format("退款压力主要集中在%s，退款金额%s，退款金额占比%s",
                value(topRefund, "CATEGORY_L1"),
                value(topRefund, "REFUND_AMOUNT"),
                formatDecimal(numberValue(value(topRefund, "REFUND_AMOUNT_RATE")), 4));
        return section("refund", "退款线索", "signal", summary, source,
                Map.of("category", value(topRefund, "CATEGORY_L1"),
                        "refund_amount", value(topRefund, "REFUND_AMOUNT"),
                        "refund_amount_rate", value(topRefund, "REFUND_AMOUNT_RATE")));
    }

    private RootCauseAnalysisResult.Section section(String key,
                                                    String title,
                                                    String status,
                                                    String summary,
                                                    String source,
                                                    Map<String, Object> highlights) {
        return new RootCauseAnalysisResult.Section(key, title, status, summary, source, highlights);
    }

    private List<Map<String, Object>> rows(Map<String, Object> toolResult) {
        Object raw = toolResult.get("rows");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
            return rows;
        }
        return List.of();
    }

    private Map<String, Object> firstRow(Map<String, Object> toolResult) {
        List<Map<String, Object>> rows = rows(toolResult);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String lower = key.toLowerCase();
        if (row.containsKey(lower)) {
            return row.get(lower);
        }
        String upper = key.toUpperCase();
        if (row.containsKey(upper)) {
            return row.get(upper);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            return Double.parseDouble(String.valueOf(value));
        }
        return 0D;
    }

    private String formatDecimal(double value, int scale) {
        return String.format("%." + scale + "f", value);
    }

    private String lineageSentence(String regionDataSource, String orderDataSource, String categoryDataSource) {
        String regionSource = describeDataSource(regionDataSource);
        String orderSource = describeDataSource(orderDataSource);
        String categorySource = describeDataSource(categoryDataSource);
        if (regionSource.equals(orderSource) && orderSource.equals(categorySource)) {
            return "区域、订单、品类判断均使用" + regionSource + "；用户、漏斗、退款和业务补齐证据沿主链数据。";
        }
        return String.format("区域、订单、品类判断分别使用%s、%s、%s；用户、漏斗、退款和业务补齐证据沿主链数据。",
                regionSource, orderSource, categorySource);
    }

    private String describeDataSource(String dataSource) {
        if ("olist_public_dataset".equals(dataSource)) {
            return "公开 Olist 数据支线";
        }
        return "当前 demo 数据主链";
    }
}
