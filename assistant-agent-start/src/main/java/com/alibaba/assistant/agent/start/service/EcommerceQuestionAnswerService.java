package com.alibaba.assistant.agent.start.service;

import com.alibaba.assistant.agent.start.config.SemanticModelCatalog;
import com.alibaba.assistant.agent.start.config.JdbcWarehouseQueryService;
import com.alibaba.assistant.agent.start.security.PromptInjectionGuard;
import com.alibaba.assistant.agent.start.security.SecurityAuditLogger;
import com.alibaba.assistant.agent.start.tool.CategoryRankTool;
import com.alibaba.assistant.agent.start.config.MetricDictionaryCatalog;
import com.alibaba.assistant.agent.start.tool.FunnelAnalysisTool;
import com.alibaba.assistant.agent.start.tool.GmvQueryTool;
import com.alibaba.assistant.agent.start.tool.OrderQueryTool;
import com.alibaba.assistant.agent.start.tool.RefundAnalysisTool;
import com.alibaba.assistant.agent.start.tool.RegionPerformanceQueryTool;
import com.alibaba.assistant.agent.start.tool.UserMetricTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EcommerceQuestionAnswerService {

    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})");
    private static final LocalDate LATEST_DEMO_DATE = LocalDate.of(2026, 5, 17);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationSessionStore sessionStore;
    private final MetricDictionaryCatalog metricDictionaryCatalog;
    private final SemanticModelCatalog semanticModelCatalog;
    private final GmvQueryTool gmvQueryTool;
    private final OrderQueryTool orderQueryTool;
    private final UserMetricTool userMetricTool;
    private final RegionPerformanceQueryTool regionPerformanceQueryTool;
    private final CategoryRankTool categoryRankTool;
    private final RefundAnalysisTool refundAnalysisTool;
    private final FunnelAnalysisTool funnelAnalysisTool;
    private final RootCauseAnalysisBuilder rootCauseAnalysisBuilder;
    private final JdbcWarehouseQueryService warehouseQueryService;
    private final PromptInjectionGuard promptInjectionGuard;
    private final SecurityAuditLogger securityAuditLogger;

    public EcommerceQuestionAnswerService(ConversationSessionStore sessionStore,
                                          MetricDictionaryCatalog metricDictionaryCatalog,
                                          SemanticModelCatalog semanticModelCatalog,
                                          GmvQueryTool gmvQueryTool,
                                          OrderQueryTool orderQueryTool,
                                          UserMetricTool userMetricTool,
                                          RegionPerformanceQueryTool regionPerformanceQueryTool,
                                          CategoryRankTool categoryRankTool,
                                          RefundAnalysisTool refundAnalysisTool,
                                          FunnelAnalysisTool funnelAnalysisTool,
                                          RootCauseAnalysisBuilder rootCauseAnalysisBuilder,
                                          JdbcWarehouseQueryService warehouseQueryService,
                                          PromptInjectionGuard promptInjectionGuard,
                                          SecurityAuditLogger securityAuditLogger) {
        this.sessionStore = sessionStore;
        this.metricDictionaryCatalog = metricDictionaryCatalog;
        this.semanticModelCatalog = semanticModelCatalog;
        this.gmvQueryTool = gmvQueryTool;
        this.orderQueryTool = orderQueryTool;
        this.userMetricTool = userMetricTool;
        this.regionPerformanceQueryTool = regionPerformanceQueryTool;
        this.categoryRankTool = categoryRankTool;
        this.refundAnalysisTool = refundAnalysisTool;
        this.funnelAnalysisTool = funnelAnalysisTool;
        this.rootCauseAnalysisBuilder = rootCauseAnalysisBuilder;
        this.warehouseQueryService = warehouseQueryService;
        this.promptInjectionGuard = promptInjectionGuard;
        this.securityAuditLogger = securityAuditLogger;
    }

    public Map<String, Object> answer(String question) {
        return answer("default-session", question);
    }

    public Map<String, Object> answer(String sessionId, String question) {
        PromptInjectionGuard.PromptRisk promptRisk = promptInjectionGuard.assess(sessionId, question);
        if (promptRisk.blocked()) {
            securityAuditLogger.recordBlockedPrompt(sessionId, question, promptRisk);
            return securityBlockedAnswer(question, promptRisk);
        }
        ConversationSessionState sessionState = sessionStore.get(sessionId);
        ResolvedQuestion resolvedQuestion = resolveQuestion(question, sessionState);
        if (resolvedQuestion.requiresClarification()) {
            sessionStore.save(sessionId, sessionState.withPendingClarification(
                    resolvedQuestion.clarificationType(),
                    resolvedQuestion.intent(),
                    resolvedQuestion.metricId(),
                    resolvedQuestion.statDate(),
                    resolvedQuestion.regionName(),
                    resolvedQuestion.categoryName(),
                    question
            ));
            return clarificationAnswer(question, resolvedQuestion.clarificationMessage());
        }

        Map<String, Object> result;
        switch (resolvedQuestion.intent()) {
            case "root_cause" -> result = answerRootCause(question, resolvedQuestion);
            case "order_metrics" -> result = answerOrderMetrics(question, resolvedQuestion);
            case "user_metrics" -> result = answerUserMetrics(question, resolvedQuestion);
            case "channel_conversion" -> result = answerChannelConversion(question, resolvedQuestion);
            case "daily_gmv" -> result = answerDailyGmv(question, resolvedQuestion);
            case "refund_breakdown" -> result = answerRefundBreakdown(question, resolvedQuestion);
            case "refund_rate_metrics" -> result = answerRefundRate(question, resolvedQuestion);
            case "region_compare" -> result = answerRegionCompare(question, resolvedQuestion);
            case "category_rank" -> result = answerCategoryRank(question, resolvedQuestion);
            case "business_general" -> result = answerBusinessGeneral(question, resolvedQuestion);
            default -> result = Map.of(
                    "success", false,
                    "question", question,
                    "message", "这个问题暂时没有落在已授权的电商经营分析范围内。你可以问 GMV、订单、用户、区域、品类、转化、退款、异常原因或运营处理建议。"
            );
        }

        if (Boolean.TRUE.equals(result.get("success"))) {
            sessionStore.save(sessionId, sessionState.with(
                    resolvedQuestion.intent(),
                    resolvedQuestion.metricId(),
                    resolvedQuestion.statDate(),
                    resolvedQuestion.regionName(),
                    resolvedQuestion.categoryName(),
                    question
            ));
        }
        return result;
    }

    private Map<String, Object> answerDailyGmv(String question, ResolvedQuestion resolvedQuestion) {
        LocalDate statDate = resolvedQuestion.statDate();
        if (resolvedQuestion.categoryName() != null) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("stat_date", statDate.toString());
            params.put("limit", 10);
            if (resolvedQuestion.regionName() != null) {
                params.put("region_name", resolvedQuestion.regionName());
            }
            Map<String, Object> toolResult = invokeTool(categoryRankTool, params);
            List<Map<String, Object>> rows = rows(toolResult);
            Map<String, Object> row = findByValue(rows, "CATEGORY_L1", resolvedQuestion.categoryName());
            String answer = String.format("%s 在 %s 的 GMV 是 %s，支付订单数 %s，退款率 %s。",
                    resolvedQuestion.categoryName(),
                    statDate,
                    value(row, "GMV"),
                    value(row, "PAID_ORDER_COUNT"),
                    value(row, "REFUND_RATE"));
            return baseAnswer(question, "fast", List.of("CategoryRankTool"), answer, Map.of("category_metrics", rows));
        }
        if (resolvedQuestion.regionName() != null) {
            Map<String, Object> toolResult = invokeTool(regionPerformanceQueryTool, Map.of(
                    "stat_date", statDate.toString(),
                    "region_name", resolvedQuestion.regionName()
            ));
            List<Map<String, Object>> rows = rows(toolResult);
            Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
            String answer = String.format("%s 在 %s 的 GMV 是 %s，支付订单数 %s，退款率 %s。",
                    resolvedQuestion.regionName(),
                    statDate,
                    value(row, "GMV"),
                    value(row, "PAID_ORDER_COUNT"),
                    value(row, "REFUND_RATE"));
            return baseAnswer(question, "fast", List.of("RegionPerformanceQueryTool"), answer, Map.of("region_metrics", rows));
        }

        Map<String, Object> toolResult = invokeTool(gmvQueryTool, Map.of("stat_date", statDate.toString()));
        List<Map<String, Object>> rows = rows(toolResult);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);

        String answer = String.format("%s 的 GMV 是 %s，支付订单数 %s，活跃买家数 %s，DAU %s，退款率 %s。",
                statDate,
                value(row, "GMV"),
                value(row, "PAID_ORDER_COUNT"),
                value(row, "ACTIVE_BUYER_COUNT"),
                value(row, "DAU"),
                value(row, "REFUND_RATE"));

        return baseAnswer(question, "fast", List.of("GmvQueryTool"), answer, Map.of("daily_core_metrics", rows));
    }

    private Map<String, Object> answerRefundRate(String question, ResolvedQuestion resolvedQuestion) {
        LocalDate statDate = resolvedQuestion.statDate();
        if (resolvedQuestion.categoryName() != null) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("stat_date", statDate.toString());
            params.put("limit", 10);
            if (resolvedQuestion.regionName() != null) {
                params.put("region_name", resolvedQuestion.regionName());
            }
            Map<String, Object> toolResult = invokeTool(categoryRankTool, params);
            List<Map<String, Object>> rows = rows(toolResult);
            Map<String, Object> row = findByValue(rows, "CATEGORY_L1", resolvedQuestion.categoryName());
            String answer = String.format("%s 在 %s 的退款率是 %s。",
                    resolvedQuestion.categoryName(),
                    statDate,
                    value(row, "REFUND_RATE"));
            return baseAnswer(question, "fast", List.of("CategoryRankTool"), answer, Map.of("category_metrics", rows));
        }
        if (resolvedQuestion.regionName() != null) {
            Map<String, Object> toolResult = invokeTool(regionPerformanceQueryTool, Map.of(
                    "stat_date", statDate.toString(),
                    "region_name", resolvedQuestion.regionName()
            ));
            List<Map<String, Object>> rows = rows(toolResult);
            Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
            String answer = String.format("%s 在 %s 的退款率是 %s。",
                    resolvedQuestion.regionName(),
                    statDate,
                    value(row, "REFUND_RATE"));
            return baseAnswer(question, "fast", List.of("RegionPerformanceQueryTool"), answer, Map.of("region_metrics", rows));
        }
        Map<String, Object> toolResult = invokeTool(gmvQueryTool, Map.of("stat_date", statDate.toString()));
        List<Map<String, Object>> rows = rows(toolResult);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        String answer = String.format("%s 的大盘退款率是 %s。", statDate, value(row, "REFUND_RATE"));
        return baseAnswer(question, "fast", List.of("GmvQueryTool"), answer, Map.of("daily_core_metrics", rows));
    }

    private Map<String, Object> answerRefundBreakdown(String question, ResolvedQuestion resolvedQuestion) {
        LocalDate statDate = resolvedQuestion.statDate();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("stat_date", statDate.toString());
        params.put("limit", 5);
        if (resolvedQuestion.regionName() != null) {
            params.put("region_name", resolvedQuestion.regionName());
        }
        Map<String, Object> toolResult = invokeTool(refundAnalysisTool, params);
        List<Map<String, Object>> rows = rows(toolResult);
        String scope = resolvedQuestion.regionName() == null ? "全站" : resolvedQuestion.regionName();
        if (rows.isEmpty()) {
            String answer = String.format("%s %s 暂无明显退款品类集中数据。", statDate, scope);
            return baseAnswer(question, "fast", List.of("RefundAnalysisTool"), answer, Map.of("refund_breakdown", rows), resolvedQuestion);
        }
        String ranking = rows.stream()
                .limit(5)
                .map(row -> String.format("%s（退款金额%s，关联GMV%s，退款金额占比%s）",
                        value(row, "CATEGORY_L1"),
                        value(row, "REFUND_AMOUNT"),
                        value(row, "RELATED_GMV"),
                        value(row, "REFUND_AMOUNT_RATE")))
                .reduce((left, right) -> left + "；" + right)
                .orElse("暂无数据");
        Map<String, Object> top = rows.get(0);
        String answer = String.format("%s %s 退款主要集中在%s。Top 品类依次是：%s。",
                statDate,
                scope,
                value(top, "CATEGORY_L1"),
                ranking);
        return baseAnswer(question, "fast", List.of("RefundAnalysisTool"), answer, Map.of("refund_breakdown", rows), resolvedQuestion);
    }

    private Map<String, Object> answerOrderMetrics(String question, ResolvedQuestion resolvedQuestion) {
        LocalDate statDate = resolvedQuestion.statDate();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("stat_date", statDate.toString());
        if (resolvedQuestion.regionName() != null) {
            params.put("region_name", resolvedQuestion.regionName());
        }
        if (resolvedQuestion.categoryName() != null) {
            params.put("category_l1", resolvedQuestion.categoryName());
        }
        Map<String, Object> toolResult = invokeTool(orderQueryTool, params);
        List<Map<String, Object>> rows = rows(toolResult);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);

        String scopePrefix = buildScopePrefix(resolvedQuestion.regionName(), resolvedQuestion.categoryName(), statDate);
        String answer = String.format("%s订单量是 %s，支付订单数 %s，退款订单数 %s，客单价 %s，总支付金额 %s。",
                scopePrefix,
                value(row, "ORDER_COUNT"),
                value(row, "PAID_ORDER_COUNT"),
                value(row, "REFUNDED_ORDER_COUNT"),
                value(row, "AVG_ORDER_VALUE"),
                value(row, "GROSS_PAY_AMOUNT"));

        return baseAnswer(question, "fast", List.of("OrderQueryTool"), answer, Map.of("order_metrics", rows));
    }

    private Map<String, Object> answerUserMetrics(String question, ResolvedQuestion resolvedQuestion) {
        LocalDate statDate = resolvedQuestion.statDate();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("stat_date", statDate.toString());
        if (resolvedQuestion.regionName() != null) {
            params.put("region_name", resolvedQuestion.regionName());
        }
        Map<String, Object> toolResult = invokeTool(userMetricTool, params);
        List<Map<String, Object>> rows = rows(toolResult);
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);

        String scopePrefix = buildScopePrefix(resolvedQuestion.regionName(), null, statDate);
        String metricId = resolvedQuestion.metricId();
        String answer;
        if ("active_buyer".equals(metricId)) {
            answer = String.format("%s活跃买家数是 %s，支付订单数 %s，买家激活率 %s。",
                    scopePrefix,
                    value(row, "ACTIVE_BUYER_COUNT"),
                    value(row, "PAID_ORDER_COUNT"),
                    value(row, "BUYER_ACTIVATION_RATE"));
        }
        else if ("dau".equals(metricId)) {
            answer = String.format("%sDAU 是 %s，活跃买家数 %s，支付订单数 %s，买家激活率 %s。",
                    scopePrefix,
                    value(row, "DAU"),
                    value(row, "ACTIVE_BUYER_COUNT"),
                    value(row, "PAID_ORDER_COUNT"),
                    value(row, "BUYER_ACTIVATION_RATE"));
        }
        else {
            answer = String.format("%sDAU 是 %s，活跃买家数 %s，支付订单数 %s，买家激活率 %s。",
                    scopePrefix,
                    value(row, "DAU"),
                    value(row, "ACTIVE_BUYER_COUNT"),
                    value(row, "PAID_ORDER_COUNT"),
                    value(row, "BUYER_ACTIVATION_RATE"));
        }

        return baseAnswer(question, "fast", List.of("UserMetricTool"), answer, Map.of("user_metrics", rows));
    }

    private Map<String, Object> answerChannelConversion(String question, ResolvedQuestion resolvedQuestion) {
        LocalDate statDate = resolvedQuestion.statDate();
        Map<String, Object> toolResult = invokeTool(userMetricTool, Map.of(
                "stat_date", statDate.toString(),
                "view_type", "channel",
                "limit", 3
        ));
        List<Map<String, Object>> rows = rows(toolResult);
        Map<String, Object> top = rows.isEmpty() ? Map.of() : rows.get(0);

        String ranking = rows.stream()
                .map(row -> value(row, "CHANNEL_CODE") + "(" + value(row, "CONVERSION_RATE") + ")")
                .reduce((left, right) -> left + "，" + right)
                .orElse("暂无数据");

        String answer = String.format("%s 渠道转化率最高的是 %s，转化率 %s；当前 TOP 渠道依次是：%s。",
                statDate,
                value(top, "CHANNEL_CODE"),
                value(top, "CONVERSION_RATE"),
                ranking);

        return baseAnswer(question, "fast", List.of("UserMetricTool"), answer, Map.of("channel_user_metrics", rows));
    }

    private Map<String, Object> answerRegionCompare(String question, ResolvedQuestion resolvedQuestion) {
        LocalDate statDate = resolvedQuestion.statDate();
        Map<String, Object> toolResult = invokeTool(regionPerformanceQueryTool, Map.of("stat_date", statDate.toString()));
        List<Map<String, Object>> rows = rows(toolResult);
        Map<String, Object> east = findByValue(rows, "REGION_NAME", "华东");
        Map<String, Object> south = findByValue(rows, "REGION_NAME", "华南");

        if (east.isEmpty() || south.isEmpty()) {
            Map<String, Object> available = rows.isEmpty() ? Map.of() : rows.get(0);
            String answer = String.format("%s 的公开数据区域快照当前只覆盖到 %s：GMV=%s，支付订单数=%s。另一侧区域暂无可比数据，系统后续会继续补齐。",
                    statDate,
                    value(available, "REGION_NAME"),
                    value(available, "GMV"),
                    value(available, "PAID_ORDER_COUNT"));
            return baseAnswer(question, "fast", List.of("RegionPerformanceQueryTool"), answer, Map.of("region_metrics", rows));
        }

        Map<String, Object> worse = compareByGmv(east, south);
        String worseRegion = String.valueOf(value(worse, "REGION_NAME"));
        String answer = String.format("%s 的区域对比里，%s 表现更差：GMV=%s，退款率=%s。对照区域 GMV=%s。",
                statDate,
                worseRegion,
                value(worse, "GMV"),
                value(worse, "REFUND_RATE"),
                "华东".equals(worseRegion) ? value(south, "GMV") : value(east, "GMV"));

        return baseAnswer(question, "fast", List.of("RegionPerformanceQueryTool"), answer, Map.of("region_metrics", rows));
    }

    private Map<String, Object> answerCategoryRank(String question, ResolvedQuestion resolvedQuestion) {
        LocalDate statDate = resolvedQuestion.statDate();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("stat_date", statDate.toString());
        params.put("limit", 3);
        if (resolvedQuestion.regionName() != null) {
            params.put("region_name", resolvedQuestion.regionName());
        }
        Map<String, Object> toolResult = invokeTool(categoryRankTool, params);
        List<Map<String, Object>> rows = rows(toolResult);
        String ranking = rows.stream()
                .map(row -> value(row, "CATEGORY_L1") + "(" + value(row, "GMV") + ")")
                .reduce((left, right) -> left + "，" + right)
                .orElse("暂无数据");

        String answer = resolvedQuestion.regionName() == null
                ? String.format("%s 的品类排行是：%s。", statDate, ranking)
                : String.format("%s 在 %s 的品类排行是：%s。", resolvedQuestion.regionName(), statDate, ranking);
        return baseAnswer(question, "fast", List.of("CategoryRankTool"), answer, Map.of("category_rank", rows));
    }

    private Map<String, Object> answerRootCause(String question, ResolvedQuestion resolvedQuestion) {
        String regionName = resolvedQuestion.regionName() == null ? "华东" : resolvedQuestion.regionName();
        LocalDate statDate = resolvedQuestion.statDate();
        LocalDate previousDate = statDate.minusDays(1);

        List<Map<String, Object>> degradations = new ArrayList<>();
        Map<String, Object> currentRegion = safeInvokeTool("current_region", regionPerformanceQueryTool, Map.of("stat_date", statDate.toString(), "region_name", regionName), degradations);
        Map<String, Object> previousRegion = safeInvokeTool("previous_region", regionPerformanceQueryTool, Map.of("stat_date", previousDate.toString(), "region_name", regionName), degradations);
        Map<String, Object> currentOrder = safeInvokeTool("current_order", orderQueryTool, Map.of("stat_date", statDate.toString(), "region_name", regionName), degradations);
        Map<String, Object> previousOrder = safeInvokeTool("previous_order", orderQueryTool, Map.of("stat_date", previousDate.toString(), "region_name", regionName), degradations);
        Map<String, Object> currentUser = safeInvokeTool("current_user", userMetricTool, Map.of("stat_date", statDate.toString(), "region_name", regionName), degradations);
        Map<String, Object> previousUser = safeInvokeTool("previous_user", userMetricTool, Map.of("stat_date", previousDate.toString(), "region_name", regionName), degradations);
        Map<String, Object> currentCategory = safeInvokeTool("current_category", categoryRankTool, Map.of("stat_date", statDate.toString(), "region_name", regionName, "limit", 5), degradations);
        Map<String, Object> previousCategory = safeInvokeTool("previous_category", categoryRankTool, Map.of("stat_date", previousDate.toString(), "region_name", regionName, "limit", 5), degradations);
        Map<String, Object> refund = safeInvokeTool("refund", refundAnalysisTool, Map.of("stat_date", statDate.toString(), "region_name", regionName, "limit", 3), degradations);
        Map<String, Object> funnelCurrent = safeInvokeTool("current_funnel", funnelAnalysisTool, Map.of("stat_date", statDate.toString(), "region_name", regionName), degradations);
        Map<String, Object> funnelPrevious = safeInvokeTool("previous_funnel", funnelAnalysisTool, Map.of("stat_date", previousDate.toString(), "region_name", regionName), degradations);
        String dragCategory = resolveLargestDropCategory(rows(currentCategory), rows(previousCategory));
        List<Map<String, Object>> productSellerDrilldown;
        try {
            productSellerDrilldown = warehouseQueryService.getProductSellerDrilldown(
                    statDate,
                    previousDate,
                    regionName,
                    dragCategory,
                    5
            );
        }
        catch (Exception ex) {
            degradations.add(degradation("product_seller_drilldown", "JdbcWarehouseQueryService", ex));
            productSellerDrilldown = List.of();
        }
        List<Map<String, Object>> businessEvidence;
        try {
            businessEvidence = warehouseQueryService.getBusinessEvidence(
                    statDate,
                    regionName,
                    dragCategory,
                    6
            );
        }
        catch (Exception ex) {
            degradations.add(degradation("business_evidence", "JdbcWarehouseQueryService", ex));
            businessEvidence = List.of();
        }
        RootCauseAnalysisResult rootCause = rootCauseAnalysisBuilder.build(
                regionName,
                statDate,
                previousDate,
                currentRegion,
                previousRegion,
                currentOrder,
                previousOrder,
                currentUser,
                previousUser,
                currentCategory,
                previousCategory,
                funnelCurrent,
                funnelPrevious,
                refund,
                productSellerDrilldown,
                businessEvidence
        );

        Map<String, Object> result = baseAnswer(
                question,
                "deep",
                List.of("RegionPerformanceQueryTool", "OrderQueryTool", "UserMetricTool", "CategoryRankTool", "FunnelAnalysisTool", "RefundAnalysisTool"),
                rootCause.summary(),
                rootCause.facts(),
                resolvedQuestion
        );
        result.put("root_cause", rootCause.toMap());
        result.put("degraded", !degradations.isEmpty());
        result.put("degradation_policy", Map.of(
                "mode", "partial_result",
                "rule", "单个 Tool 失败时保留已完成证据，并将失败维度标记为证据不足；核心区域/订单/品类全部失败时才需要人工复核。"
        ));
        result.put("degradations", degradations);
        return result;
    }

    private Map<String, Object> answerBusinessGeneral(String question, ResolvedQuestion resolvedQuestion) {
        ResolvedQuestion rootCauseQuestion = ResolvedQuestion.normal(
                "root_cause",
                resolvedQuestion.metricId() == null ? "gmv" : resolvedQuestion.metricId(),
                resolvedQuestion.statDate(),
                resolvedQuestion.regionName() == null ? "华东" : resolvedQuestion.regionName(),
                resolvedQuestion.categoryName()
        );
        Map<String, Object> result = new LinkedHashMap<>(answerRootCause(question, rootCauseQuestion));
        result.put("intent_route", "business_general");
        result.put("route_policy", Map.of(
                "domain_gate", "ecommerce_operations",
                "fallback", "root_cause_workflow",
                "reason", "问题和电商经营业务相关，但没有命中固定问数意图，因此进入通用业务分析入口。"
        ));
        result.put("answer", "我先按电商经营问题接住这个问题，并用当前可用的 GMV/订单/用户/品类/漏斗/退款证据做通用分析。\n\n"
                + result.getOrDefault("answer", ""));
        return result;
    }

    private Map<String, Object> safeInvokeTool(String evidenceKey,
                                               Object tool,
                                               Map<String, Object> params,
                                               List<Map<String, Object>> degradations) {
        try {
            return invokeTool(tool, params);
        }
        catch (Exception ex) {
            degradations.add(degradation(evidenceKey, tool.getClass().getSimpleName(), ex));
            return Map.of(
                    "rows", List.of(),
                    "data_source", "degraded",
                    "degraded", true,
                    "error", ex.getMessage()
            );
        }
    }

    private Map<String, Object> degradation(String evidenceKey, String component, Exception ex) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("evidence_key", evidenceKey);
        item.put("component", component);
        item.put("reason", ex.getMessage());
        item.put("fallback", "partial_result");
        return item;
    }

    private String resolveLargestDropCategory(List<Map<String, Object>> currentRows, List<Map<String, Object>> previousRows) {
        Map<String, Double> current = new LinkedHashMap<>();
        for (Map<String, Object> row : currentRows) {
            current.put(String.valueOf(value(row, "CATEGORY_L1")), numberValue(value(row, "GMV")));
        }
        Map<String, Double> previous = new LinkedHashMap<>();
        for (Map<String, Object> row : previousRows) {
            previous.put(String.valueOf(value(row, "CATEGORY_L1")), numberValue(value(row, "GMV")));
        }
        String bestCategory = null;
        double biggestDrop = 0D;
        for (String category : previous.keySet()) {
            double delta = current.getOrDefault(category, 0D) - previous.getOrDefault(category, 0D);
            if (delta < biggestDrop) {
                biggestDrop = delta;
                bestCategory = category;
            }
        }
        return bestCategory;
    }

    private Map<String, Object> baseAnswer(String question,
                                           String pathType,
                                           List<String> toolChain,
                                           String answer,
                                           Map<String, Object> facts) {
        return baseAnswer(question, pathType, toolChain, answer, facts, null);
    }

    private Map<String, Object> baseAnswer(String question,
                                           String pathType,
                                           List<String> toolChain,
                                           String answer,
                                           Map<String, Object> facts,
                                           ResolvedQuestion resolvedQuestion) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("question", question);
        result.put("path_type", pathType);
        result.put("tool_chain", toolChain);
        result.put("answer", answer);
        result.put("facts", facts);
        result.put("analysis_trace", buildAnalysisTrace(pathType, toolChain, facts, resolvedQuestion));
        result.put("trace_tags", buildTraceTags(pathType, toolChain, resolvedQuestion));
        return result;
    }

    private Map<String, Object> clarificationAnswer(String question, String clarificationMessage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("question", question);
        result.put("requires_clarification", true);
        result.put("message", clarificationMessage);
        return result;
    }

    private Map<String, Object> securityBlockedAnswer(String question, PromptInjectionGuard.PromptRisk risk) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("question", question);
        result.put("blocked", true);
        result.put("risk_type", risk.riskType());
        result.put("message", "这个问题包含越权或 Prompt Injection 风险，系统已拒绝执行。当前 Agent 只允许查询和分析已授权的电商经营指标。");
        result.put("trace_tags", Map.of(
                "analysis_path", "blocked",
                "risk_type", risk.riskType(),
                "matched_pattern", risk.matchedPattern()
        ));
        return result;
    }

    private Map<String, Object> invokeTool(Object tool, Map<String, Object> params) {
        try {
            String input = objectMapper.writeValueAsString(params);
            String output;
            if (tool instanceof GmvQueryTool typedTool) {
                output = typedTool.call(input);
            }
            else if (tool instanceof RegionPerformanceQueryTool typedTool) {
                output = typedTool.call(input);
            }
            else if (tool instanceof OrderQueryTool typedTool) {
                output = typedTool.call(input);
            }
            else if (tool instanceof UserMetricTool typedTool) {
                output = typedTool.call(input);
            }
            else if (tool instanceof CategoryRankTool typedTool) {
                output = typedTool.call(input);
            }
            else if (tool instanceof RefundAnalysisTool typedTool) {
                output = typedTool.call(input);
            }
            else if (tool instanceof FunnelAnalysisTool typedTool) {
                output = typedTool.call(input);
            }
            else {
                throw new IllegalArgumentException("Unsupported tool: " + tool.getClass().getSimpleName());
            }
            return objectMapper.readValue(output, new TypeReference<>() {});
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to invoke tool " + tool.getClass().getSimpleName(), ex);
        }
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

    private Map<String, Object> findByValue(List<Map<String, Object>> rows, String key, String expectedValue) {
        return rows.stream()
                .filter(row -> expectedValue.equals(String.valueOf(value(row, key))))
                .findFirst()
                .orElse(Map.of());
    }

    private Map<String, Object> compareByGmv(Map<String, Object> first, Map<String, Object> second) {
        double firstGmv = numberValue(value(first, "GMV"));
        double secondGmv = numberValue(value(second, "GMV"));
        return firstGmv <= secondGmv ? first : second;
    }

    private List<Map<String, Object>> buildAnalysisTrace(String pathType,
                                                         List<String> toolChain,
                                                         Map<String, Object> facts,
                                                         ResolvedQuestion resolvedQuestion) {
        List<Map<String, Object>> trace = new ArrayList<>();
        int index = 1;
        for (String toolName : toolChain) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step", index++);
            step.put("tool", toolName);
            step.put("stage", stageName(toolName));
            step.put("summary", stageSummary(toolName, facts, resolvedQuestion));
            trace.add(step);
        }
        return trace;
    }

    private Map<String, Object> buildTraceTags(String pathType,
                                               List<String> toolChain,
                                               ResolvedQuestion resolvedQuestion) {
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("analysis_path", pathType);
        tags.put("analysis_depth", "deep".equalsIgnoreCase(pathType) ? "multi_step" : "single_step");
        tags.put("tool_count", toolChain.size());
        if (resolvedQuestion != null) {
            tags.put("intent", resolvedQuestion.intent());
            if (resolvedQuestion.metricId() != null) {
                tags.put("metric_id", resolvedQuestion.metricId());
            }
            if (resolvedQuestion.regionName() != null) {
                tags.put("region_name", resolvedQuestion.regionName());
            }
            if (resolvedQuestion.categoryName() != null) {
                tags.put("category_name", resolvedQuestion.categoryName());
            }
            if (resolvedQuestion.statDate() != null) {
                tags.put("stat_date", resolvedQuestion.statDate().toString());
            }
        }
        return tags;
    }

    private String stageName(String toolName) {
        return switch (toolName) {
            case "GmvQueryTool" -> "大盘快照";
            case "OrderQueryTool" -> "订单结构";
            case "UserMetricTool" -> "用户规模";
            case "RegionPerformanceQueryTool" -> "区域表现";
            case "CategoryRankTool" -> "品类拆解";
            case "FunnelAnalysisTool" -> "漏斗线索";
            case "RefundAnalysisTool" -> "退款线索";
            default -> "分析步骤";
        };
    }

    private String stageSummary(String toolName,
                                Map<String, Object> facts,
                                ResolvedQuestion resolvedQuestion) {
        return switch (toolName) {
            case "GmvQueryTool" -> "先确认目标日期的大盘指标，判断是否是单次问数场景。";
            case "OrderQueryTool" -> "检查订单量、支付订单量和客单价，判断是单量问题还是客单价问题。";
            case "UserMetricTool" -> "查看 DAU、活跃买家和买家激活率，判断用户规模是否先走弱。";
            case "RegionPerformanceQueryTool" -> resolvedQuestion != null && resolvedQuestion.regionName() != null
                    ? "先锁定目标区域表现，确认异常是否集中在指定区域。"
                    : "先看区域表现，判断哪个区域拖累整体结果。";
            case "CategoryRankTool" -> resolvedQuestion != null && resolvedQuestion.categoryName() != null
                    ? "查看目标品类指标，确认该品类是否成为主要拖累。"
                    : "拆到品类层，找出主要拖累或拉动的品类。";
            case "FunnelAnalysisTool" -> "查看漏斗转化，判断问题是否来自前链路承接。";
            case "RefundAnalysisTool" -> "查看退款压力是否抬升，确认售后是否成为异常来源。";
            default -> "执行标准分析步骤。";
        };
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

    private LocalDate resolveDate(String question) {
        Matcher matcher = DATE_PATTERN.matcher(question);
        if (matcher.find()) {
            return LocalDate.parse(matcher.group(1));
        }
        if (question.contains("去年同期")) {
            return LATEST_DEMO_DATE.minusYears(1);
        }
        if (question.contains("前天")) {
            return LATEST_DEMO_DATE.minusDays(1);
        }
        if (question.contains("今天")) {
            return LATEST_DEMO_DATE;
        }
        if (question.contains("昨天")) {
            return LATEST_DEMO_DATE;
        }
        return LATEST_DEMO_DATE;
    }

    private ResolvedQuestion resolveQuestion(String question, ConversationSessionState sessionState) {
        ResolvedQuestion clarificationFollowUp = resolveClarificationFollowUp(question, sessionState);
        if (clarificationFollowUp != null) {
            return clarificationFollowUp;
        }

        String metricClarification = metricDictionaryCatalog.resolveDisambiguationQuestion(question);
        if (metricClarification != null && !question.contains("DAU") && !question.contains("活跃买家")) {
            return ResolvedQuestion.clarification("user_metrics", "dau", resolveDate(question), sessionState.lastRegionName(), null,
                    "user_metric_scope",
                    "您说的活跃用户，是指 DAU（日活）还是活跃买家？当前演示链默认支持 DAU 和活跃买家两类口径。");
        }

        String regionName = detectRegion(question, sessionState);
        String categoryName = detectCategory(question, sessionState);
        LocalDate statDate = resolveDateWithContext(question, sessionState);
        String metricId = detectMetricId(question, sessionState);

        if (isRootCauseQuestion(question)) {
            return ResolvedQuestion.normal("root_cause", metricId == null ? "gmv" : metricId, statDate, regionName == null ? "华东" : regionName, categoryName);
        }
        if ((question.contains("订单量") || question.contains("客单价")) && isMetricQueryLike(question)) {
            return ResolvedQuestion.normal("order_metrics", "paid_order_count", statDate, regionName, categoryName);
        }
        if ((question.contains("DAU") || question.contains("活跃买家") || question.contains("活跃用户")) && isMetricQueryLike(question)) {
            return ResolvedQuestion.normal("user_metrics", "dau", statDate, regionName, categoryName);
        }
        if (question.contains("渠道") && question.contains("转化")) {
            return ResolvedQuestion.normal("channel_conversion", "conversion_rate", statDate, regionName, categoryName);
        }
        if (isRefundBreakdownQuestion(question)) {
            return ResolvedQuestion.normal("refund_breakdown", "refund_amount", statDate, regionName, categoryName);
        }
        if ((metricDictionaryCatalog.matches("refund_rate", question) || "refund_rate".equals(metricId))
                && (isMetricQueryLike(question) || isFollowUpQuestion(question))) {
            return ResolvedQuestion.normal("refund_rate_metrics", "refund_rate", statDate, regionName, categoryName);
        }
        if ((question.contains("GMV") || metricDictionaryCatalog.matches("gmv", question)) && isMetricQueryLike(question)) {
            return ResolvedQuestion.normal("daily_gmv", "gmv", statDate, regionName, categoryName);
        }
        if (question.contains("华东") && question.contains("华南") && (question.contains("哪个") || question.contains("谁"))) {
            return ResolvedQuestion.normal("region_compare", "gmv", statDate, null, null);
        }
        if (question.contains("品类") && (question.contains("排行") || question.contains("排名") || question.contains("TOP"))) {
            return ResolvedQuestion.normal("category_rank", "gmv", statDate, regionName, categoryName);
        }

        if (isFollowUpQuestion(question) && sessionState.lastIntent() != null) {
            String inheritedMetric = metricId != null ? metricId : sessionState.lastMetricId();
            String inheritedIntent = "refund_rate".equals(inheritedMetric) ? "refund_rate_metrics" : sessionState.lastIntent();
            return ResolvedQuestion.normal(inheritedIntent,
                    inheritedMetric,
                    statDate,
                    regionName != null ? regionName : sessionState.lastRegionName(),
                    categoryName != null ? categoryName : sessionState.lastCategoryName());
        }
        if (isBusinessDomainQuestion(question, sessionState)) {
            return ResolvedQuestion.normal("business_general",
                    metricId == null ? "gmv" : metricId,
                    statDate,
                    regionName == null ? sessionState.lastRegionName() : regionName,
                    categoryName == null ? sessionState.lastCategoryName() : categoryName);
        }
        return ResolvedQuestion.normal("unknown", metricId, statDate, regionName, categoryName);
    }

    private boolean isRootCauseQuestion(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        boolean hasGmvOrAnomalyContext = normalized.contains("gmv")
                || normalized.contains("异常")
                || normalized.contains("anomaly")
                || normalized.contains("经营")
                || normalized.contains("运营");
        boolean asksForCause = normalized.contains("为什么")
                || normalized.contains("为何")
                || normalized.contains("原因")
                || normalized.contains("归因")
                || normalized.contains("root cause")
                || normalized.contains("root_cause")
                || normalized.contains("分析")
                || normalized.contains("详情")
                || normalized.contains("排查");
        boolean hasDropSignal = normalized.contains("跌")
                || normalized.contains("下降")
                || normalized.contains("下滑")
                || normalized.contains("回落")
                || normalized.contains("drop");
        return normalized.contains("为什么跌")
                || (hasGmvOrAnomalyContext && asksForCause)
                || (hasGmvOrAnomalyContext && hasDropSignal);
    }

    private boolean isBusinessDomainQuestion(String question, ConversationSessionState sessionState) {
        String normalized = question == null ? "" : question.trim().toLowerCase();
        if (normalized.isBlank()) {
            return false;
        }
        if (List.of("?", "？", "??", "？？", "怎么说", "怎么看").contains(normalized)
                && sessionState.lastIntent() != null) {
            return true;
        }
        if (detectRegion(question, sessionState) != null || detectCategory(question, sessionState) != null) {
            return true;
        }
        if (detectMetricId(question, sessionState) != null) {
            return true;
        }
        return List.of(
                        "电商", "运营", "经营", "业务", "gmv", "订单", "客单价", "用户", "dau", "活跃买家",
                        "区域", "华东", "华南", "品类", "商品", "商家", "库存", "履约", "物流",
                        "售后", "退款", "退货", "转化", "漏斗", "广告", "投放", "活动", "优惠券",
                        "异常", "下跌", "下降", "下滑", "回落", "增长", "原因", "归因", "建议", "策略",
                        "处理", "排查", "复盘", "飞书", "通知", "负责人"
                )
                .stream()
                .anyMatch(normalized::contains);
    }

    private boolean isRefundBreakdownQuestion(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        boolean refundContext = normalized.contains("退款")
                || normalized.contains("售后")
                || normalized.contains("退货")
                || normalized.contains("refund");
        boolean asksBreakdown = normalized.contains("集中")
                || normalized.contains("哪些品类")
                || normalized.contains("什么品类")
                || normalized.contains("品类")
                || normalized.contains("分布")
                || normalized.contains("top")
                || normalized.contains("排行")
                || normalized.contains("排名");
        return refundContext && asksBreakdown;
    }

    private ResolvedQuestion resolveClarificationFollowUp(String question, ConversationSessionState sessionState) {
        if (!"user_metric_scope".equals(sessionState.pendingClarificationType())) {
            return null;
        }
        if (question == null || question.isBlank()) {
            return null;
        }
        if (question.contains("DAU") || question.contains("日活")) {
            return ResolvedQuestion.normal(
                    sessionState.pendingIntent() == null ? "user_metrics" : sessionState.pendingIntent(),
                    "dau",
                    sessionState.pendingDate() == null ? resolveDate(question) : sessionState.pendingDate(),
                    sessionState.pendingRegionName(),
                    sessionState.pendingCategoryName()
            );
        }
        if (question.contains("活跃买家") || question.contains("买家")) {
            return ResolvedQuestion.normal(
                    sessionState.pendingIntent() == null ? "user_metrics" : sessionState.pendingIntent(),
                    "active_buyer",
                    sessionState.pendingDate() == null ? resolveDate(question) : sessionState.pendingDate(),
                    sessionState.pendingRegionName(),
                    sessionState.pendingCategoryName()
            );
        }
        return null;
    }

    private boolean containsHowMany(String question) {
        return question.contains("多少") || question.contains("怎么样") || question.contains("如何");
    }

    private boolean isMetricQueryLike(String question) {
        if (containsHowMany(question) || isFollowUpQuestion(question)) {
            return true;
        }
        String normalized = question == null ? "" : question.trim()
                .replace("？", "")
                .replace("?", "")
                .replace("，", "")
                .replace(",", "")
                .replace("、", "/")
                .replace("／", "/");
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.contains("/") || normalized.contains("+") || normalized.contains("&")) {
            return true;
        }
        return List.of("GMV", "订单量", "客单价", "DAU", "活跃买家", "退款率", "转化率")
                .stream()
                .anyMatch(metric -> normalized.equals(metric) || normalized.endsWith(metric));
    }

    private boolean isFollowUpQuestion(String question) {
        return question.contains("那") || question.contains("呢") || question.contains("同比") || question.contains("环比");
    }

    private String detectRegion(String question, ConversationSessionState sessionState) {
        for (String region : semanticModelCatalog.regions()) {
            if (question.contains(region)) {
                return region;
            }
        }
        if (isFollowUpQuestion(question)) {
            return sessionState.lastRegionName();
        }
        return null;
    }

    private String detectCategory(String question, ConversationSessionState sessionState) {
        for (String category : semanticModelCatalog.categories()) {
            if (question.contains(category)) {
                return category;
            }
        }
        if (isFollowUpQuestion(question)) {
            return sessionState.lastCategoryName();
        }
        return null;
    }

    private String detectMetricId(String question, ConversationSessionState sessionState) {
        if (metricDictionaryCatalog.matches("refund_rate", question)) {
            return "refund_rate";
        }
        if (metricDictionaryCatalog.matches("conversion_rate", question)) {
            return "conversion_rate";
        }
        if (metricDictionaryCatalog.matches("active_buyer", question)) {
            return "active_buyer";
        }
        if (semanticModelCatalog.isRefundFollowUp(question)) {
            return "refund_rate";
        }
        if (metricDictionaryCatalog.matches("dau", question) || question.contains("活跃买家")) {
            return "dau";
        }
        if (semanticModelCatalog.isUserScaleFollowUp(question)) {
            return "dau";
        }
        if (metricDictionaryCatalog.matches("paid_order_count", question) || question.contains("客单价")) {
            return "paid_order_count";
        }
        if (semanticModelCatalog.isOrderStructureFollowUp(question)) {
            return "paid_order_count";
        }
        if (metricDictionaryCatalog.matches("gmv", question)) {
            return "gmv";
        }
        if (isFollowUpQuestion(question)) {
            return sessionState.lastMetricId();
        }
        return null;
    }

    private LocalDate resolveDateWithContext(String question, ConversationSessionState sessionState) {
        if (DATE_PATTERN.matcher(question).find() || question.contains("今天") || question.contains("昨天") || question.contains("前天") || question.contains("去年同期")) {
            return resolveDate(question);
        }
        if (isFollowUpQuestion(question) && sessionState.lastDate() != null) {
            return sessionState.lastDate();
        }
        return resolveDate(question);
    }

    private String buildScopePrefix(String regionName, String categoryName, LocalDate statDate) {
        StringBuilder builder = new StringBuilder();
        if (regionName != null) {
            builder.append(regionName).append("在 ");
        }
        builder.append(statDate);
        if (categoryName != null) {
            builder.append(" 的").append(categoryName);
        }
        builder.append(" ");
        return builder.toString();
    }

    private record ResolvedQuestion(String intent,
                                    String metricId,
                                    LocalDate statDate,
                                    String regionName,
                                    String categoryName,
                                    boolean requiresClarification,
                                    String clarificationMessage,
                                    String clarificationType) {

        static ResolvedQuestion normal(String intent, String metricId, LocalDate statDate, String regionName, String categoryName) {
            return new ResolvedQuestion(intent, metricId, statDate, regionName, categoryName, false, null, null);
        }

        static ResolvedQuestion clarification(String intent,
                                             String metricId,
                                             LocalDate statDate,
                                             String regionName,
                                             String categoryName,
                                             String clarificationType,
                                             String clarificationMessage) {
            return new ResolvedQuestion(intent, metricId, statDate, regionName, categoryName, true, clarificationMessage, clarificationType);
        }
    }
}
