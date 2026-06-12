package com.alibaba.assistant.agent.start.service;

import com.alibaba.assistant.agent.start.tool.CategoryRankTool;
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

@Service
public class CodeActPlanExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CodeActPlanValidator validator;
    private final EcommerceQuestionAnswerService questionAnswerService;
    private final RegionPerformanceQueryTool regionPerformanceQueryTool;
    private final OrderQueryTool orderQueryTool;
    private final UserMetricTool userMetricTool;
    private final CategoryRankTool categoryRankTool;
    private final FunnelAnalysisTool funnelAnalysisTool;
    private final RefundAnalysisTool refundAnalysisTool;
    private final GmvQueryTool gmvQueryTool;

    public CodeActPlanExecutor(CodeActPlanValidator validator,
                               EcommerceQuestionAnswerService questionAnswerService,
                               RegionPerformanceQueryTool regionPerformanceQueryTool,
                               OrderQueryTool orderQueryTool,
                               UserMetricTool userMetricTool,
                               CategoryRankTool categoryRankTool,
                               FunnelAnalysisTool funnelAnalysisTool,
                               RefundAnalysisTool refundAnalysisTool,
                               GmvQueryTool gmvQueryTool) {
        this.validator = validator;
        this.questionAnswerService = questionAnswerService;
        this.regionPerformanceQueryTool = regionPerformanceQueryTool;
        this.orderQueryTool = orderQueryTool;
        this.userMetricTool = userMetricTool;
        this.categoryRankTool = categoryRankTool;
        this.funnelAnalysisTool = funnelAnalysisTool;
        this.refundAnalysisTool = refundAnalysisTool;
        this.gmvQueryTool = gmvQueryTool;
    }

    public Map<String, Object> executeFixedRoute(Map<String, Object> request, List<?> routeTools) {
        List<String> selectedTools = routeTools == null ? List.of() : routeTools.stream()
                .map(String::valueOf)
                .filter(validator::allowedTool)
                .distinct()
                .toList();
        List<String> skippedTools = routeTools == null ? List.of() : routeTools.stream()
                .map(String::valueOf)
                .filter(tool -> !validator.allowedTool(tool))
                .distinct()
                .toList();

        Map<String, Object> fixedPlan = new LinkedHashMap<>();
        fixedPlan.put("selected_tools", selectedTools);
        fixedPlan.put("execution_plan", fixedExecutionPlan(selectedTools));
        fixedPlan.put("cause_ranking", List.of());
        fixedPlan.put("action_routing", List.of());
        fixedPlan.put("notification_decision", Map.of(
                "recommendation", "review_before_send",
                "confidence", "中",
                "manual_confirmation_required", true
        ));

        Map<String, Object> safeRequest = new LinkedHashMap<>(request == null ? Map.of() : request);
        safeRequest.put("plan", fixedPlan);

        Map<String, Object> result = new LinkedHashMap<>(execute(safeRequest));
        result.put("mode", "fixed_route_evidence_first");
        result.put("fixed_route_tools", selectedTools);
        result.put("skipped_route_tools", skippedTools);
        result.put("execution_policy", Map.of(
                "java_route", "metric_id_selects_fixed_route",
                "java_execution", "execute_read_only_core_tools_first",
                "model_role", "interpret_evidence_after_tool_execution",
                "notification_policy", "manual_confirmation_required"
        ));
        return result;
    }

    public Map<String, Object> execute(Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        Map<String, Object> plan = resolvePlan(safeRequest);
        LocalDate statDate = resolveDate(safeRequest.get("stat_date"));
        String regionName = stringOrDefault(safeRequest.get("region_name"), "华东");
        String fallbackQuestion = stringOrDefault(
                safeRequest.get("fallback_question"),
                statDate + " " + regionName + " GMV 为什么跌了？"
        );

        Map<String, Object> validation = validator.validate(plan);
        if (!Boolean.TRUE.equals(validation.get("valid"))) {
            return fallback("plan_validation_failed", validation, fallbackQuestion);
        }

        List<Map<String, Object>> executions = new ArrayList<>();
        List<Map<String, Object>> degradations = new ArrayList<>();
        List<String> selectedTools = stringList(plan.get("selected_tools"));
        for (String toolName : selectedTools) {
            if (!validator.allowedTool(toolName)) {
                degradations.add(degradation(toolName, "tool_not_allowed"));
                continue;
            }
            try {
                executions.add(executeTool(toolName, statDate, regionName));
            }
            catch (Exception ex) {
                degradations.add(degradation(toolName, ex.getMessage()));
            }
        }

        boolean shouldFallback = executions.isEmpty()
                || !containsAnyExecutedTool(executions, List.of("RegionPerformanceQueryTool", "OrderQueryTool", "CategoryRankTool"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("mode", "dynamic_codeact_plan");
        response.put("plan_validation", validation);
        response.put("executed_tools", executions.stream().map(item -> item.get("tool")).toList());
        response.put("tool_results", executions);
        response.put("degraded", !degradations.isEmpty() || shouldFallback);
        response.put("degradations", degradations);
        response.put("plan", plan);
        response.put("notification_decision", plan.getOrDefault("notification_decision", Map.of()));
        response.put("action_routing", sortRoutingByPriority(mapList(plan.get("action_routing"))));
        response.put("cause_ranking", plan.getOrDefault("cause_ranking", List.of()));
        response.put("execution_policy", Map.of(
                "model_role", "planner_only",
                "java_role", "validate_and_execute_read_only_tools",
                "notification_policy", "manual_confirmation_required",
                "fallback", "fixed_gmv_root_cause"
        ));

        if (shouldFallback) {
            response.put("fallback_used", true);
            response.put("fallback_reason", "dynamic plan did not produce enough core evidence");
            response.put("fallback_result", questionAnswerService.answer("codeact-plan-fallback", fallbackQuestion));
        }
        else {
            response.put("fallback_used", false);
        }
        return response;
    }

    private Map<String, Object> fallback(String reason, Map<String, Object> validation, String fallbackQuestion) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("mode", "fixed_root_cause_fallback");
        response.put("fallback_used", true);
        response.put("fallback_reason", reason);
        response.put("plan_validation", validation);
        response.put("execution_policy", Map.of(
                "model_role", "planner_rejected",
                "java_role", "fallback_to_deterministic_root_cause",
                "notification_policy", "manual_confirmation_required"
        ));
        response.put("fallback_result", questionAnswerService.answer("codeact-plan-fallback", fallbackQuestion));
        return response;
    }

    private Map<String, Object> executeTool(String toolName, LocalDate statDate, String regionName) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("stat_date", statDate.toString());
        if (!"GmvQueryTool".equals(toolName)) {
            params.put("region_name", regionName);
        }
        if ("CategoryRankTool".equals(toolName) || "RefundAnalysisTool".equals(toolName)) {
            params.put("limit", 5);
        }

        String input = objectMapper.writeValueAsString(params);
        String output = switch (toolName) {
            case "GmvQueryTool" -> gmvQueryTool.call(input);
            case "RegionPerformanceQueryTool" -> regionPerformanceQueryTool.call(input);
            case "OrderQueryTool" -> orderQueryTool.call(input);
            case "UserMetricTool" -> userMetricTool.call(input);
            case "CategoryRankTool" -> categoryRankTool.call(input);
            case "FunnelAnalysisTool" -> funnelAnalysisTool.call(input);
            case "RefundAnalysisTool" -> refundAnalysisTool.call(input);
            default -> throw new IllegalArgumentException("Unsupported tool: " + toolName);
        };

        Map<String, Object> parsed = objectMapper.readValue(output, new TypeReference<>() {});
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tool", toolName);
        item.put("params", params);
        item.put("success", parsed.getOrDefault("success", false));
        item.put("data_source", parsed.getOrDefault("data_source", ""));
        item.put("query_type", parsed.getOrDefault("query_type", ""));
        item.put("row_count", parsed.getOrDefault("count", 0));
        item.put("preview", previewRows(parsed.get("rows")));
        return item;
    }

    private List<Map<String, Object>> previewRows(Object rows) {
        List<Map<String, Object>> normalized = mapList(rows);
        return normalized.stream().limit(2).toList();
    }

    private boolean containsAnyExecutedTool(List<Map<String, Object>> executions, List<String> requiredTools) {
        List<String> executedTools = executions.stream()
                .map(item -> String.valueOf(item.get("tool")))
                .toList();
        return requiredTools.stream().anyMatch(executedTools::contains);
    }

    private Map<String, Object> degradation(String component, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("component", component);
        item.put("reason", reason == null ? "unknown" : reason);
        item.put("fallback", "partial_result_or_fixed_root_cause");
        return item;
    }

    private List<Map<String, Object>> fixedExecutionPlan(List<String> selectedTools) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < selectedTools.size(); i++) {
            String tool = selectedTools.get(i);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step", i + 1);
            step.put("tool", tool);
            step.put("reason", fixedToolReason(tool));
            step.put("expected_evidence", fixedToolEvidence(tool));
            steps.add(step);
        }
        return steps;
    }

    private String fixedToolReason(String tool) {
        return switch (tool) {
            case "GmvQueryTool" -> "先确认大盘 GMV 是否真的发生异常。";
            case "RegionPerformanceQueryTool" -> "确认异常是否集中在目标区域，还是全局同步波动。";
            case "OrderQueryTool" -> "拆 GMV = 支付订单量 × 客单价，判断交易结构。";
            case "UserMetricTool" -> "查看用户规模和活跃买家是否走弱。";
            case "CategoryRankTool" -> "定位拖累品类，判断是否由品类或商品结构造成。";
            case "FunnelAnalysisTool" -> "查看浏览到支付转化，判断链路承接是否变差。";
            case "RefundAnalysisTool" -> "查看退款金额和退款占比，判断售后是否成为异常来源。";
            default -> "按固定 route 补充业务证据。";
        };
    }

    private String fixedToolEvidence(String tool) {
        return switch (tool) {
            case "GmvQueryTool" -> "GMV、订单量、客单价基础变化。";
            case "RegionPerformanceQueryTool" -> "目标区域 GMV、支付订单数、退款率。";
            case "OrderQueryTool" -> "订单量、支付订单量、退款订单量、支付金额、客单价。";
            case "UserMetricTool" -> "DAU、活跃买家、买家激活率。";
            case "CategoryRankTool" -> "品类 GMV 贡献、拖累品类、拉动/拖累幅度。";
            case "FunnelAnalysisTool" -> "浏览、加购、下单、支付或 view->pay 转化。";
            case "RefundAnalysisTool" -> "退款金额、退款金额占比、退款集中品类。";
            default -> "补充业务证据。";
        };
    }

    private LocalDate resolveDate(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return LocalDate.of(2018, 8, 29);
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private String stringOrDefault(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private Map<String, Object> resolvePlan(Map<String, Object> request) {
        Map<String, Object> nestedPlan = map(request.get("plan"));
        if (!nestedPlan.isEmpty()) {
            return nestedPlan;
        }
        if (request.containsKey("selected_tools") || request.containsKey("execution_plan")) {
            return request;
        }
        return Map.of();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
                result.add(normalized);
            }
        }
        return result;
    }

    private List<Map<String, Object>> sortRoutingByPriority(List<Map<String, Object>> routes) {
        List<Map<String, Object>> sorted = new ArrayList<>(routes);
        sorted.sort((left, right) -> Integer.compare(priorityRank(left.get("priority")), priorityRank(right.get("priority"))));
        return sorted;
    }

    private int priorityRank(Object value) {
        String priority = String.valueOf(value);
        if ("P0".equals(priority)) {
            return 0;
        }
        if ("P1".equals(priority)) {
            return 1;
        }
        if ("P2".equals(priority)) {
            return 2;
        }
        return 99;
    }
}
