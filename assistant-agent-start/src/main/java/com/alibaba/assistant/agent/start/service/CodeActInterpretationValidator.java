package com.alibaba.assistant.agent.start.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CodeActInterpretationValidator {

    public Map<String, Object> validate(Map<String, Object> interpretation) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (interpretation == null || interpretation.isEmpty()) {
            errors.add("interpretation is empty");
            return result(false, errors, warnings);
        }

        List<Map<String, Object>> causeRanking = mapList(interpretation.get("cause_ranking"));
        if (causeRanking.isEmpty()) {
            errors.add("cause_ranking is required");
        }
        for (Map<String, Object> cause : causeRanking) {
            require(cause, "cause", "cause_ranking[].cause", errors);
            require(cause, "evidence", "cause_ranking[].evidence", errors);
            require(cause, "priority", "cause_ranking[].priority", errors);
        }

        List<Map<String, Object>> actionRouting = mapList(interpretation.get("action_routing"));
        if (actionRouting.isEmpty()) {
            errors.add("action_routing is required");
        }
        if (!actionRouting.isEmpty() && !isPrioritySorted(actionRouting)) {
            warnings.add("action_routing is not sorted by P0/P1/P2");
        }
        for (Map<String, Object> route : actionRouting) {
            require(route, "owner_name", "action_routing[].owner_name", errors);
            require(route, "problem", "action_routing[].problem", errors);
            require(route, "evidence", "action_routing[].evidence", errors);
            require(route, "next_action", "action_routing[].next_action", errors);
        }

        Map<String, Object> notificationDecision = map(interpretation.get("notification_decision"));
        if (notificationDecision.isEmpty()) {
            errors.add("notification_decision is required");
        }
        if (!Boolean.TRUE.equals(notificationDecision.get("manual_confirmation_required"))) {
            errors.add("notification_decision.manual_confirmation_required must be true");
        }
        require(notificationDecision, "recommendation", "notification_decision.recommendation", errors);
        require(notificationDecision, "confidence", "notification_decision.confidence", errors);
        require(notificationDecision, "reason", "notification_decision.reason", errors);

        if (mapList(interpretation.get("follow_up_suggestions")).isEmpty()
                && !(interpretation.get("follow_up_suggestions") instanceof List<?>)) {
            warnings.add("follow_up_suggestions is missing or not a list");
        }

        return result(errors.isEmpty(), errors, warnings);
    }

    private Map<String, Object> result(boolean valid, List<String> errors, List<String> warnings) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("errors", errors);
        result.put("warnings", warnings);
        result.put("policy", "LLM can rank evidence and draft routing suggestions only. Notification and workflow actions still require human confirmation.");
        return result;
    }

    private void require(Map<String, Object> payload, String key, String label, List<String> errors) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            errors.add(label + " is required");
        }
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
}
