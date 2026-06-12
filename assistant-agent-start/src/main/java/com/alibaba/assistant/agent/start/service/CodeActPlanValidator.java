package com.alibaba.assistant.agent.start.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CodeActPlanValidator {

    private static final Set<String> ALLOWED_ANALYSIS_TOOLS = Set.of(
            "GmvQueryTool",
            "RegionPerformanceQueryTool",
            "OrderQueryTool",
            "UserMetricTool",
            "CategoryRankTool",
            "FunnelAnalysisTool",
            "RefundAnalysisTool"
    );

    private static final Set<String> FORBIDDEN_ACTION_TOOLS = Set.of(
            "send_notification",
            "send_success_message",
            "send_error_message",
            "RootCauseWorkflowTool"
    );

    public Map<String, Object> validate(Map<String, Object> plan) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (plan == null || plan.isEmpty()) {
            errors.add("plan is empty");
            return result(false, errors, warnings, List.of());
        }

        List<String> selectedTools = selectedTools(plan);
        if (selectedTools.isEmpty()) {
            errors.add("selected_tools is required");
        }
        if (selectedTools.size() > 8) {
            errors.add("selected_tools exceeds max size 8");
        }
        for (String tool : selectedTools) {
            if (FORBIDDEN_ACTION_TOOLS.contains(tool)) {
                errors.add("forbidden action tool in planning phase: " + tool);
            }
            else if (!ALLOWED_ANALYSIS_TOOLS.contains(tool)) {
                errors.add("tool is not in analysis whitelist: " + tool);
            }
        }

        List<Map<String, Object>> executionPlan = mapList(plan.get("execution_plan"));
        if (executionPlan.size() < 2) {
            errors.add("execution_plan must contain at least 2 analysis steps");
        }
        for (Map<String, Object> step : executionPlan) {
            String tool = String.valueOf(step.getOrDefault("tool", ""));
            if (!tool.isBlank() && !ALLOWED_ANALYSIS_TOOLS.contains(tool)) {
                errors.add("execution step uses non-whitelisted tool: " + tool);
            }
        }

        List<Map<String, Object>> actionRouting = mapList(plan.get("action_routing"));
        if (!actionRouting.isEmpty() && !isPrioritySorted(actionRouting)) {
            warnings.add("action_routing is not sorted by P0/P1/P2; executor will keep analysis safe but caller should reorder display");
        }

        Map<String, Object> notificationDecision = map(plan.get("notification_decision"));
        boolean autoSendRequested = "send".equals(String.valueOf(notificationDecision.getOrDefault("recommendation", "")))
                && !Boolean.TRUE.equals(notificationDecision.get("manual_confirmation_required"));
        if (autoSendRequested) {
            errors.add("notification decision cannot bypass manual confirmation");
        }

        return result(errors.isEmpty(), errors, warnings, selectedTools);
    }

    public boolean allowedTool(String toolName) {
        return ALLOWED_ANALYSIS_TOOLS.contains(toolName);
    }

    private Map<String, Object> result(boolean valid, List<String> errors, List<String> warnings, List<String> selectedTools) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("errors", errors);
        result.put("warnings", warnings);
        result.put("allowed_tools", ALLOWED_ANALYSIS_TOOLS.stream().sorted().toList());
        result.put("selected_tools", selectedTools);
        result.put("policy", "Only read-only analysis tools can execute. Notification, workflow updates and close actions require human confirmation.");
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> selectedTools(Map<String, Object> plan) {
        Object raw = plan.get("selected_tools");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
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

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private boolean isPrioritySorted(List<Map<String, Object>> routes) {
        List<Integer> priorities = routes.stream()
                .map(route -> priorityRank(route.get("priority")))
                .toList();
        return priorities.equals(priorities.stream().sorted().toList());
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
