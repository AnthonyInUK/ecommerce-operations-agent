package com.alibaba.assistant.agent.start.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RootCauseAnalysisResult {

    public record Section(String key,
                          String title,
                          String status,
                          String summary,
                          String source,
                          Map<String, Object> highlights) {

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("key", key);
            payload.put("title", title);
            payload.put("status", status);
            payload.put("summary", summary);
            payload.put("source", source);
            payload.put("confidence", confidenceFor(source, status));
            payload.put("highlights", highlights == null ? Map.of() : highlights);
            return payload;
        }

        private String confidenceFor(String source, String status) {
            if ("insufficient".equals(status)) {
                return "low";
            }
            if ("olist_public_dataset".equals(source)) {
                return "high";
            }
            if ("demo_seed".equals(source)) {
                return "medium";
            }
            return "low";
        }
    }

    private final String summary;
    private final Map<String, Object> overview;
    private final Map<String, Object> metricBridge;
    private final List<Map<String, Object>> impactDrivers;
    private final List<Map<String, Object>> verificationPlan;
    private final List<Section> sections;
    private final List<Map<String, Object>> decisionTrace;
    private final List<Map<String, Object>> actionRouting;
    private final Map<String, Object> notificationDraft;
    private final List<Map<String, Object>> productSellerDrilldown;
    private final Map<String, Object> evidenceConfidence;
    private final Map<String, Object> dataLineage;
    private final Map<String, Object> facts;

    public RootCauseAnalysisResult(String summary,
                                   Map<String, Object> overview,
                                   Map<String, Object> metricBridge,
                                   List<Map<String, Object>> impactDrivers,
                                   List<Map<String, Object>> verificationPlan,
                                   List<Section> sections,
                                   List<Map<String, Object>> decisionTrace,
                                   List<Map<String, Object>> actionRouting,
                                   Map<String, Object> notificationDraft,
                                   List<Map<String, Object>> productSellerDrilldown,
                                   Map<String, Object> evidenceConfidence,
                                   Map<String, Object> dataLineage,
                                   Map<String, Object> facts) {
        this.summary = summary;
        this.overview = overview;
        this.metricBridge = metricBridge;
        this.impactDrivers = impactDrivers;
        this.verificationPlan = verificationPlan;
        this.sections = sections;
        this.decisionTrace = decisionTrace;
        this.actionRouting = actionRouting;
        this.notificationDraft = notificationDraft;
        this.productSellerDrilldown = productSellerDrilldown;
        this.evidenceConfidence = evidenceConfidence;
        this.dataLineage = dataLineage;
        this.facts = facts;
    }

    public String summary() {
        return summary;
    }

    public Map<String, Object> facts() {
        return facts;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("overview", overview);
        payload.put("metric_bridge", metricBridge);
        payload.put("impact_drivers", impactDrivers);
        payload.put("verification_plan", verificationPlan);
        payload.put("sections", sections.stream().map(Section::toMap).toList());
        payload.put("decision_trace", decisionTrace);
        payload.put("action_routing", actionRouting);
        payload.put("notification_draft", notificationDraft);
        payload.put("product_seller_drilldown", productSellerDrilldown);
        payload.put("evidence_confidence", evidenceConfidence);
        payload.put("data_lineage", dataLineage);
        payload.put("facts", facts);
        return payload;
    }
}
