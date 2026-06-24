package com.alibaba.assistant.agent.start.web;

import com.alibaba.assistant.agent.extension.trigger.executor.TriggerExecutor;
import com.alibaba.assistant.agent.extension.trigger.manager.TriggerManager;
import com.alibaba.assistant.agent.extension.trigger.model.SourceType;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerExecutionResult;
import com.alibaba.assistant.agent.extension.reply.model.ChannelExecutionContext;
import com.alibaba.assistant.agent.extension.reply.model.ReplyResult;
import com.alibaba.assistant.agent.start.config.AppOperationsProperties;
import com.alibaba.assistant.agent.start.config.AppReplyProperties;
import com.alibaba.assistant.agent.start.config.JdbcWarehouseQueryService;
import com.alibaba.assistant.agent.start.reply.FeishuWebhookChannelDefinition;
import com.alibaba.assistant.agent.start.service.AnomalyWorkflowService;
import com.alibaba.assistant.agent.start.service.AnomalyScannerService;
import com.alibaba.assistant.agent.start.service.CodeActInterpretationValidator;
import com.alibaba.assistant.agent.start.service.CodeActPlanExecutor;
import com.alibaba.assistant.agent.start.service.EcommerceQuestionAnswerService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/ecommerce")
@CrossOrigin(originPatterns = "*")
public class EcommerceAnalysisController {

    private static final String GMV_DROP_WATCH_EVENT_KEY = "ecommerce_gmv_drop_watch";

    private final EcommerceQuestionAnswerService questionAnswerService;
    private final AppOperationsProperties operationsProperties;
    private final JdbcWarehouseQueryService warehouseQueryService;
    private final TriggerManager triggerManager;
    private final TriggerExecutor triggerExecutor;
    private final FeishuWebhookChannelDefinition feishuWebhookChannelDefinition;
    private final AppReplyProperties replyProperties;
    private final AnomalyWorkflowService anomalyWorkflowService;
    private final AnomalyScannerService anomalyScannerService;
    private final CodeActPlanExecutor codeActPlanExecutor;
    private final CodeActInterpretationValidator interpretationValidator;

    public EcommerceAnalysisController(EcommerceQuestionAnswerService questionAnswerService,
                                       AppOperationsProperties operationsProperties,
                                       JdbcWarehouseQueryService warehouseQueryService,
                                       TriggerManager triggerManager,
                                       TriggerExecutor triggerExecutor,
                                       FeishuWebhookChannelDefinition feishuWebhookChannelDefinition,
                                       AppReplyProperties replyProperties,
                                       AnomalyWorkflowService anomalyWorkflowService,
                                       AnomalyScannerService anomalyScannerService,
                                       CodeActPlanExecutor codeActPlanExecutor,
                                       CodeActInterpretationValidator interpretationValidator) {
        this.questionAnswerService = questionAnswerService;
        this.operationsProperties = operationsProperties;
        this.warehouseQueryService = warehouseQueryService;
        this.triggerManager = triggerManager;
        this.triggerExecutor = triggerExecutor;
        this.feishuWebhookChannelDefinition = feishuWebhookChannelDefinition;
        this.replyProperties = replyProperties;
        this.anomalyWorkflowService = anomalyWorkflowService;
        this.anomalyScannerService = anomalyScannerService;
        this.codeActPlanExecutor = codeActPlanExecutor;
        this.interpretationValidator = interpretationValidator;
    }

    @GetMapping("/demo-question")
    public Map<String, Object> demoQuestion() {
        return Map.of(
                "session_id", "demo-ui-session",
                "question", "2018-08-29 华东 GMV 为什么跌了？"
        );
    }

    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cache", warehouseQueryService.cacheStats());
        response.put("degradation_policy", Map.of(
                "root_cause", "partial_result",
                "notification", "fallback_to_ide_log"
        ));
        response.put("idempotency_policy", Map.of(
                "in_memory_dedup", true,
                "persistent_dedup", true,
                "scope", "single shared database"
        ));
        response.put("security_policy", Map.of(
                "sql", "read_only",
                "prompt_injection", "risk_detect_and_audit"
        ));
        response.put("reply_policy", Map.of(
                "feishu_webhook_configured", replyProperties.getFeishuWebhook() != null && !replyProperties.getFeishuWebhook().isBlank(),
                "notification_dedup_enabled", replyProperties.isNotificationDedupEnabled(),
                "persistent_dedup_enabled", replyProperties.isPersistentNotificationDedupEnabled(),
                "fallback_to_ide_log_enabled", replyProperties.isNotificationFallbackToIdeLogEnabled(),
                "webhook_timeout_seconds", replyProperties.getFeishuWebhookTimeoutSeconds()
        ));
        return response;
    }

    @GetMapping("/anomalies")
    public Map<String, Object> anomalies() {
        List<Map<String, Object>> items = anomalyItems();
        Map<String, Map<String, Object>> workflows = anomalyWorkflowService.getOrCreateAll(items);
        items.forEach(item -> {
            Map<String, Object> workflow = workflows.get(String.valueOf(item.get("id")));
            if (workflow != null) {
                item.put("workflow", workflow);
                item.put("status", workflow.getOrDefault("process_status", item.get("status")));
                item.put("notification_status", workflow.getOrDefault("notification_status", "未发送"));
                item.put("owner_role", workflow.getOrDefault("assignee_role", item.get("owner_role")));
            }
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("count", items.size());
        response.put("items", items);
        response.put("workflows", workflows);
        response.put("status_options", List.of("待确认", "已派发", "处理中", "已关闭"));
        response.put("metrics", List.of("GMV", "订单量", "客单价", "退款率", "转化率", "品类 GMV", "区域 GMV"));
        response.put("responsibility_mapping", responsibilityMappingCatalog());
        response.put("integration_story", "模拟原业务系统异常池：BI / 监控规则 / 指标巡检 -> anomaly_id -> Agent root cause 分析。");
        response.put("note", "第一版异常中心使用 Olist 公开数据和 demo 补齐口径生成可复现样本，后续可替换为规则扫描或调度结果。");
        return response;
    }

    @PostMapping("/anomalies/{anomalyId}/workflow/{action}")
    public Map<String, Object> updateWorkflow(@PathVariable String anomalyId,
                                              @PathVariable String action,
                                              @RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> anomaly = anomalyItems().stream()
                .filter(item -> anomalyId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElse(null);
        if (anomaly == null) {
            return Map.of(
                    "success", false,
                    "message", "未找到异常事件：" + anomalyId
            );
        }

        Map<String, String> request = body == null ? Map.of() : body;
        String actor = request.getOrDefault("actor", "系统").trim();
        String assigneeRole = request.getOrDefault("assignee_role", String.valueOf(anomaly.getOrDefault("owner_role", ""))).trim();
        String assigneeUser = request.getOrDefault("assignee_user", "").trim();
        String note = request.getOrDefault("note", "").trim();
        String finalReason = request.getOrDefault("final_reason", "").trim();
        String closeNote = request.getOrDefault("close_note", "").trim();
        String notificationStatus = request.getOrDefault("notification_status", "").trim();

        Map<String, Object> workflow = switch (action) {
            case "confirm" -> anomalyWorkflowService.confirm(anomalyId, actor);
            case "dispatch" -> anomalyWorkflowService.dispatch(anomalyId, actor, assigneeRole, assigneeUser);
            case "accept" -> anomalyWorkflowService.accept(anomalyId, actor, assigneeRole);
            case "record" -> anomalyWorkflowService.record(anomalyId, actor, note);
            case "close" -> anomalyWorkflowService.close(anomalyId, actor, finalReason, closeNote);
            case "false-positive" -> anomalyWorkflowService.falsePositive(anomalyId, actor, finalReason.isBlank() ? note : finalReason);
            case "notification" -> anomalyWorkflowService.updateNotification(anomalyId, actor, notificationStatus.isBlank() ? "未发送" : notificationStatus, note);
            default -> null;
        };

        if (workflow == null) {
            return Map.of(
                    "success", false,
                    "message", "不支持的 workflow action：" + action
            );
        }

        return Map.of(
                "success", true,
                "anomaly_id", anomalyId,
                "workflow", workflow
        );
    }

    @PostMapping("/cache/refund/evict")
    public Map<String, Object> evictRefundCache() {
        warehouseQueryService.evictRefundCategoryBreakdownCache();
        return Map.of("success", true, "evicted", "refundCategoryBreakdown");
    }

    @PostMapping("/anomalies/{anomalyId}/analyze")
    public Map<String, Object> analyzeAnomaly(@PathVariable String anomalyId,
                                              @RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> anomaly = anomalyItems().stream()
                .filter(item -> anomalyId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElse(null);
        if (anomaly == null) {
            return Map.of(
                    "success", false,
                    "message", "未找到异常事件：" + anomalyId
            );
        }

        String sessionId = body == null ? "demo-ui-session" : body.getOrDefault("session_id", "demo-ui-session").trim();
        Map<String, Object> analysisRoute = analysisRouteFor(anomaly);
        String question = focusedQuestion(anomaly, analysisRoute);
        Map<String, Object> analysis = questionAnswerService.answer(
                sessionId.isEmpty() ? "demo-ui-session" : sessionId,
                question
        );

        Map<String, Object> response = new LinkedHashMap<>(analysis);
        Map<String, Object> focusedRootCause = focusRootCauseForRoute(analysis, anomaly, analysisRoute);
        response.put("success", analysis.getOrDefault("success", true));
        response.put("session_id", sessionId.isEmpty() ? "demo-ui-session" : sessionId);
        response.put("anomaly_id", anomalyId);
        response.put("anomaly_event", anomaly);
        response.put("analysis_route", analysisRoute);
        response.put("agent_question", question);
        response.put("integration_source", anomaly.getOrDefault("source_system", "metric_monitor"));
        response.put("integration_contract", "business_system_anomaly_id -> agent_analysis_result");
        response.put("root_cause", focusedRootCause);
        response.put("notification_draft", extractNestedMap(focusedRootCause, "notification_draft"));
        response.put("workflow", anomalyWorkflowService.getOrCreate(
                anomalyId,
                String.valueOf(anomaly.getOrDefault("status", "待确认")),
                String.valueOf(anomaly.getOrDefault("owner_role", ""))
        ));
        return response;
    }

    @GetMapping("/anomalies/{anomalyId}/codeact-planner-input")
    public Map<String, Object> codeActPlannerInput(@PathVariable String anomalyId) {
        Map<String, Object> anomaly = anomalyItems().stream()
                .filter(item -> anomalyId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElse(null);
        if (anomaly == null) {
            return Map.of(
                    "success", false,
                    "message", "未找到异常事件：" + anomalyId
            );
        }

        Map<String, Object> analysisRoute = analysisRouteFor(anomaly);
        Map<String, Object> plannerInput = plannerInputFor(anomaly, analysisRoute);
        return Map.of(
                "success", true,
                "anomaly_id", anomalyId,
                "anomaly_event", anomaly,
                "analysis_route", analysisRoute,
                "planner_input", plannerInput
        );
    }

    @PostMapping("/anomalies/{anomalyId}/codeact-plan/execute")
    public Map<String, Object> executeCodeActPlanForAnomaly(@PathVariable String anomalyId,
                                                            @RequestBody Map<String, Object> body) {
        Map<String, Object> anomaly = anomalyItems().stream()
                .filter(item -> anomalyId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElse(null);
        if (anomaly == null) {
            return Map.of(
                    "success", false,
                    "message", "未找到异常事件：" + anomalyId
            );
        }

        Map<String, Object> analysisRoute = analysisRouteFor(anomaly);
        Map<String, Object> request = new LinkedHashMap<>(body == null ? Map.of() : body);
        request.put("anomaly_id", anomalyId);
        request.put("anomaly_event", anomaly);
        request.put("analysis_route", analysisRoute);
        request.put("planner_input", plannerInputFor(anomaly, analysisRoute));
        request.putIfAbsent("stat_date", anomaly.getOrDefault("stat_date", "2018-08-29"));
        request.putIfAbsent("region_name", anomaly.getOrDefault("scope_name", "华东"));
        request.putIfAbsent("fallback_question", focusedQuestion(anomaly, analysisRoute));

        Map<String, Object> execution = codeActPlanExecutor.execute(request);
        Map<String, Object> response = new LinkedHashMap<>(execution);
        response.put("anomaly_id", anomalyId);
        response.put("anomaly_event", anomaly);
        response.put("analysis_route", analysisRoute);
        response.put("business_contract", "anomaly_id -> cropped planner input -> validated read-only tool execution -> human workflow");
        return response;
    }

    @PostMapping("/anomalies/{anomalyId}/evidence/prepare")
    public Map<String, Object> prepareEvidenceForInterpretation(@PathVariable String anomalyId,
                                                                @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> anomaly = anomalyItems().stream()
                .filter(item -> anomalyId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElse(null);
        if (anomaly == null) {
            return Map.of(
                    "success", false,
                    "message", "未找到异常事件：" + anomalyId
            );
        }

        Map<String, Object> analysisRoute = analysisRouteFor(anomaly);
        Map<String, Object> request = new LinkedHashMap<>(body == null ? Map.of() : body);
        request.put("anomaly_id", anomalyId);
        request.put("anomaly_event", anomaly);
        request.put("analysis_route", analysisRoute);
        request.putIfAbsent("stat_date", anomaly.getOrDefault("stat_date", "2018-08-29"));
        request.putIfAbsent("region_name", anomaly.getOrDefault("scope_name", "华东"));
        request.putIfAbsent("fallback_question", focusedQuestion(anomaly, analysisRoute));

        Map<String, Object> evidenceExecution = codeActPlanExecutor.executeFixedRoute(
                request,
                listValue(analysisRoute.get("tool_priority"))
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("anomaly_id", anomalyId);
        response.put("anomaly_event", anomaly);
        response.put("analysis_route", analysisRoute);
        response.put("evidence_execution", evidenceExecution);
        response.put("interpretation_input", interpretationInputFor(anomaly, analysisRoute, evidenceExecution));
        response.put("business_contract", "anomaly_id -> fixed route tools -> structured evidence -> LLM interpretation -> Java validation -> human workflow");
        return response;
    }

    @PostMapping("/anomalies/{anomalyId}/evidence/interpretation/validate")
    public Map<String, Object> validateEvidenceInterpretation(@PathVariable String anomalyId,
                                                              @RequestBody Map<String, Object> body) {
        Map<String, Object> anomaly = anomalyItems().stream()
                .filter(item -> anomalyId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElse(null);
        if (anomaly == null) {
            return Map.of(
                    "success", false,
                    "message", "未找到异常事件：" + anomalyId
            );
        }

        Map<String, Object> interpretation = extractNestedMap(body == null ? Map.of() : body, "interpretation");
        if (interpretation.isEmpty() && body != null) {
            interpretation = body;
        }

        Map<String, Object> validation = interpretationValidator.validate(interpretation);
        return Map.of(
                "success", Boolean.TRUE.equals(validation.get("valid")),
                "anomaly_id", anomalyId,
                "validation", validation,
                "interpretation", interpretation,
                "business_contract", "LLM interpretation must pass Java schema validation before notification or workflow handoff"
        );
    }

    /**
     * 动态扫描快照表生成异常列表。
     * AnomalyScannerService 会查 ads_olist_region_daily / ads_olist_category_daily /
     * ads_olist_daily_core_metrics / ads_daily_core_metrics，对比前后两日环比。
     * 若快照表无数据（尚未导入 Olist 数据集），则兜底返回一条说明性占位条目。
     */
    private List<Map<String, Object>> anomalyItems() {
        List<Map<String, Object>> scanned = anomalyScannerService.scan();
        if (!scanned.isEmpty()) {
            // 补充 analysisRouteFor 字段（Scanner 生成的条目不含该字段）
            scanned.forEach(item -> item.put("analysis_route", analysisRouteFor(item)));
            return scanned;
        }
        // 兜底：Olist 数据未导入时给一条占位提示，告知接口仍可用
        Map<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("id", "no-data-placeholder");
        placeholder.put("stat_date", "N/A");
        placeholder.put("metric_id", "gmv");
        placeholder.put("metric_name", "GMV");
        placeholder.put("scope_type", "全站");
        placeholder.put("scope_name", "全站");
        placeholder.put("current_value", 0);
        placeholder.put("previous_value", 0);
        placeholder.put("delta", 0);
        placeholder.put("delta_rate", 0);
        placeholder.put("severity", "低");
        placeholder.put("status", "待确认");
        placeholder.put("owner_role", "平台运营");
        placeholder.put("confidence", "低");
        placeholder.put("source", "无数据");
        placeholder.put("root_cause_question", "");
        placeholder.put("description", "快照表暂无数据，请先完成 Olist 数据导入（运行 build_ads_from_dwd_olist.sql）后再查看异常中心。");
        placeholder.put("next_step", "导入 Olist 数据后，异常中心将自动从快照表扫描并展示真实异常。");
        placeholder.put("source_system", "system");
        placeholder.put("source_system_label", "系统提示");
        placeholder.put("analyze_endpoint", "");
        placeholder.put("analysis_route", analysisRouteFor(placeholder));
        return List.of(placeholder);
    }

    @PostMapping("/answer")
    public Map<String, Object> answer(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").trim();
        String sessionId = body.getOrDefault("session_id", "demo-ui-session").trim();
        if (question.isEmpty()) {
            return Map.of(
                    "success", false,
                    "message", "question 不能为空"
            );
        }

        Map<String, Object> result = questionAnswerService.answer(sessionId.isEmpty() ? "demo-ui-session" : sessionId, question);
        Map<String, Object> response = new LinkedHashMap<>(result);
        response.put("session_id", sessionId.isEmpty() ? "demo-ui-session" : sessionId);
        return response;
    }

    @PostMapping("/codeact-plan/execute")
    public Map<String, Object> executeCodeActPlan(@RequestBody Map<String, Object> body) {
        return codeActPlanExecutor.execute(body);
    }

    @PostMapping("/triggers/gmv-drop-watch/run-once")
    public Map<String, Object> runGmvDropWatchOnce() {
        AppOperationsProperties.GmvDropWatch config = operationsProperties.getGmvDropWatch();
        if (!config.isEnabled()) {
            return Map.of(
                    "success", false,
                    "message", "GMV drop watch trigger 未启用，请先设置 app.operations.gmv-drop-watch.enabled=true"
            );
        }

        TriggerDefinition definition = triggerManager.list(SourceType.GLOBAL, config.getSourceId()).stream()
                .filter(item -> GMV_DROP_WATCH_EVENT_KEY.equals(item.getEventKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GMV drop watch trigger was not registered"));
        TriggerExecutionResult executionResult = triggerExecutor.execute(definition);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", Boolean.TRUE.equals(executionResult.getExecutionSuccess()));
        response.put("trigger_id", definition.getTriggerId());
        response.put("event_key", definition.getEventKey());
        response.put("demo_report_date", definition.getMetadata().getOrDefault("demo_report_date", ""));
        response.put("condition_passed", executionResult.getConditionPassed());
        response.put("should_abandon", executionResult.getShouldAbandon());
        response.put("execution_time_ms", executionResult.getExecutionTime());
        response.put("result", executionResult.getExecutionResult());
        response.put("error_message", executionResult.getErrorMessage());
        if (Boolean.TRUE.equals(executionResult.getConditionPassed())) {
            String reportDate = extractResultValue(executionResult.getExecutionResult(), "report_date");
            if (reportDate == null || reportDate.isBlank()) {
                reportDate = String.valueOf(definition.getMetadata().getOrDefault("demo_report_date", "")).trim();
            }
            if (!reportDate.isBlank()) {
                Map<String, Object> analysis = questionAnswerService.answer(
                        "trigger-gmv-drop-watch-" + reportDate,
                        reportDate + " 华东 GMV 为什么跌了？"
                );
                response.put("analysis", analysis);
                response.put("root_cause", analysis.getOrDefault("root_cause", Map.of()));
                response.put("notification_draft", extractNestedMap(analysis, "root_cause", "notification_draft"));
            }
        }
        return response;
    }

    @PostMapping("/notifications/feishu/send")
    public Map<String, Object> sendFeishuNotification(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "电商经营异常通知").trim();
        String text = body.getOrDefault("text", "").trim();
        String sessionId = body.getOrDefault("session_id", "demo-ui-session").trim();
        if (text.isEmpty()) {
            return Map.of(
                    "success", false,
                    "message", "text 不能为空"
            );
        }

        ReplyResult result = feishuWebhookChannelDefinition.execute(
                ChannelExecutionContext.builder()
                        .toolName("send_notification")
                        .source(ChannelExecutionContext.ExecutionSource.MANUAL)
                        .sessionId(sessionId.isEmpty() ? "demo-ui-session" : sessionId)
                        .traceId("ui-feishu-send-" + System.currentTimeMillis())
                        .build(),
                Map.of(
                        "title", title.isEmpty() ? "电商经营异常通知" : title,
                        "text", text
                )
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("metadata", result.getMetadata());
        response.put("data", result.getData());
        return response;
    }

    private String extractResultValue(Object executionResult, String key) {
        if (executionResult instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private Map<String, Object> extractNestedMap(Map<String, Object> payload, String parentKey, String childKey) {
        Object parent = payload.get(parentKey);
        if (!(parent instanceof Map<?, ?> parentMap)) {
            return Map.of();
        }
        Object child = parentMap.get(childKey);
        if (!(child instanceof Map<?, ?> childMap)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        childMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Map<String, Object> extractNestedMap(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((entryKey, entryValue) -> result.put(String.valueOf(entryKey), entryValue));
        return result;
    }

    private Map<String, Object> focusRootCauseForRoute(Map<String, Object> analysis,
                                                       Map<String, Object> anomaly,
                                                       Map<String, Object> analysisRoute) {
        Object rawRootCause = analysis.get("root_cause");
        if (!(rawRootCause instanceof Map<?, ?> rootCauseMap)) {
            return Map.of();
        }

        Map<String, Object> focused = new LinkedHashMap<>();
        rootCauseMap.forEach((key, value) -> focused.put(String.valueOf(key), value));

        String routeKey = String.valueOf(analysisRoute.getOrDefault("route_key", "gmv_root_cause_full"));
        List<String> sectionPriority = sectionPriorityFor(routeKey);
        List<String> ownerPriority = ownerPriorityFor(routeKey);
        List<Map<String, Object>> sections = mapList(focused.get("sections"));
        List<Map<String, Object>> actionRouting = mapList(focused.get("action_routing"));
        List<Map<String, Object>> decisionTrace = mapList(focused.get("decision_trace"));

        List<Map<String, Object>> focusedSections = reorderBy(sections, sectionPriority, "key");
        List<Map<String, Object>> focusedRouting = reorderRoutingByPriority(actionRouting, ownerPriority);
        List<Map<String, Object>> focusedDecisionTrace = reorderBy(decisionTrace, sectionPriority, "key");

        focused.put("sections", focusedSections);
        focused.put("action_routing", focusedRouting);
        focused.put("decision_trace", focusedDecisionTrace);
        focused.put("analysis_focus", Map.of(
                "route_key", routeKey,
                "route_name", analysisRoute.getOrDefault("route_name", "GMV 完整 root cause"),
                "focus_summary", analysisRoute.getOrDefault("focus_summary", "按标准 root cause 链路排查"),
                "priority_dimensions", analysisRoute.getOrDefault("priority_dimensions", List.of()),
                "primary_owner", analysisRoute.getOrDefault("primary_owner", anomaly.getOrDefault("owner_role", "经营分析")),
                "handoff_roles", analysisRoute.getOrDefault("handoff_roles", List.of()),
                "assignment_rule", analysisRoute.getOrDefault("assignment_rule", ""),
                "notification_channel", analysisRoute.getOrDefault("notification_channel", "")
        ));
        focused.put("cause_ranking", focusedSections.stream()
                .filter(section -> "signal".equals(String.valueOf(section.get("status"))))
                .map(section -> Map.of(
                        "key", section.getOrDefault("key", ""),
                        "title", section.getOrDefault("title", ""),
                        "summary", section.getOrDefault("summary", ""),
                        "source", section.getOrDefault("source", ""),
                        "rank_reason", "按 " + analysisRoute.getOrDefault("route_name", "当前异常类型") + " 优先级排序"
                ))
                .toList());

        Map<String, Object> originalDraft = extractNestedMap(focused, "notification_draft");
        if (!originalDraft.isEmpty()) {
            focused.put("notification_draft", focusedNotificationDraft(originalDraft, anomaly, analysisRoute, focusedSections, focusedRouting, focused));
        }
        return focused;
    }

    private Map<String, Object> focusedNotificationDraft(Map<String, Object> originalDraft,
                                                         Map<String, Object> anomaly,
                                                         Map<String, Object> analysisRoute,
                                                         List<Map<String, Object>> sections,
                                                         List<Map<String, Object>> actionRouting,
                                                         Map<String, Object> rootCause) {
        Map<String, Object> draft = new LinkedHashMap<>(originalDraft);
        List<String> evidence = sections.stream()
                .filter(section -> "signal".equals(String.valueOf(section.get("status"))))
                .limit(4)
                .map(section -> "【" + section.getOrDefault("title", "证据") + "】" + section.getOrDefault("summary", ""))
                .toList();
        List<Object> targetRoles = actionRouting.stream()
                .limit(4)
                .map(route -> route.getOrDefault("owner_name", "业务负责人"))
                .distinct()
                .toList();

        draft.put("title", "【" + analysisRoute.getOrDefault("route_name", "经营异常") + "】"
                + anomaly.getOrDefault("stat_date", "") + " "
                + anomaly.getOrDefault("scope_name", "") + " "
                + anomaly.getOrDefault("metric_name", "指标") + " 异常");
        draft.put("target_roles", targetRoles);
        draft.put("evidence", evidence);
        draft.put("next_actions", actionRouting.stream()
                .limit(4)
                .map(route -> route.getOrDefault("next_action", "确认异常并继续下钻"))
                .distinct()
                .toList());
        draft.put("action_routing", actionRouting);
        draft.put("body", focusedNotificationBody(draft, anomaly, analysisRoute, evidence, actionRouting, rootCause));
        return draft;
    }

    private String focusedNotificationBody(Map<String, Object> draft,
                                           Map<String, Object> anomaly,
                                           Map<String, Object> analysisRoute,
                                           List<String> evidence,
                                           List<Map<String, Object>> actionRouting,
                                           Map<String, Object> rootCause) {
        StringBuilder body = new StringBuilder();
        body.append(draft.getOrDefault("title", "经营异常待排查")).append("\n\n");
        body.append("异常 ID：").append(anomaly.getOrDefault("id", "-")).append("\n");
        body.append("分析重点：").append(analysisRoute.getOrDefault("focus_summary", "-")).append("\n");
        body.append("核心结论：").append(rootCause.getOrDefault("summary", "-")).append("\n\n");
        body.append("优先证据：\n");
        evidence.forEach(line -> body.append("- ").append(line).append("\n"));
        body.append("\n优先责任分发：\n");
        actionRouting.stream().limit(4).forEach(route -> {
            body.append("\n【").append(route.getOrDefault("owner_name", "业务负责人")).append("】")
                    .append("优先级：").append(route.getOrDefault("priority", "P1")).append("\n");
            body.append("问题：").append(route.getOrDefault("problem", route.getOrDefault("reason", "-"))).append("\n");
            body.append("证据：").append(route.getOrDefault("evidence", "-")).append("\n");
            body.append("建议动作：").append(route.getOrDefault("next_action", "确认异常并继续下钻")).append("\n");
        });
        body.append("\nAgent 策略：").append(analysisRoute.getOrDefault("agent_policy", "单 Agent 根据异常类型选择分析重点；人工确认后再通知。"));
        return body.toString();
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

    private List<Map<String, Object>> reorderBy(List<Map<String, Object>> items, List<String> priority, String key) {
        List<Map<String, Object>> reordered = new ArrayList<>(items);
        reordered.sort((left, right) -> Integer.compare(rank(priority, left.get(key)), rank(priority, right.get(key))));
        return reordered;
    }

    private List<Map<String, Object>> reorderRoutingByPriority(List<Map<String, Object>> items, List<String> ownerPriority) {
        List<Map<String, Object>> reordered = new ArrayList<>(items);
        reordered.sort((left, right) -> {
            int priorityCompare = Integer.compare(priorityRank(left.get("priority")), priorityRank(right.get("priority")));
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Integer.compare(rank(ownerPriority, left.get("owner_key")), rank(ownerPriority, right.get("owner_key")));
        });
        return reordered;
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

    private int rank(List<String> priority, Object value) {
        int index = priority.indexOf(String.valueOf(value));
        return index < 0 ? priority.size() + 100 : index;
    }

    private List<String> sectionPriorityFor(String routeKey) {
        return switch (routeKey) {
            case "order_structure_first" -> List.of("order_structure", "funnel", "user_scale", "region", "category_drag", "business_evidence", "refund");
            case "category_drilldown_first" -> List.of("category_drag", "business_evidence", "order_structure", "region", "refund", "user_scale", "funnel");
            case "refund_governance_first" -> List.of("refund", "business_evidence", "category_drag", "order_structure", "region", "user_scale", "funnel");
            case "growth_funnel_first" -> List.of("user_scale", "funnel", "business_evidence", "order_structure", "region", "category_drag", "refund");
            case "aov_structure_first" -> List.of("order_structure", "category_drag", "business_evidence", "region", "user_scale", "funnel", "refund");
            default -> List.of("region", "order_structure", "user_scale", "category_drag", "business_evidence", "funnel", "refund");
        };
    }

    private List<String> ownerPriorityFor(String routeKey) {
        return switch (routeKey) {
            case "order_structure_first" -> List.of("business_analysis", "conversion_operation", "growth_operation", "platform_operation", "category_operation", "after_sales_governance");
            case "category_drilldown_first" -> List.of("category_operation", "business_analysis", "platform_operation", "after_sales_governance", "growth_operation", "conversion_operation");
            case "refund_governance_first" -> List.of("after_sales_governance", "category_operation", "business_analysis", "platform_operation", "growth_operation", "conversion_operation");
            case "growth_funnel_first" -> List.of("growth_operation", "conversion_operation", "business_analysis", "platform_operation", "category_operation", "after_sales_governance");
            case "aov_structure_first" -> List.of("business_analysis", "category_operation", "platform_operation", "growth_operation", "conversion_operation", "after_sales_governance");
            default -> List.of("platform_operation", "business_analysis", "growth_operation", "category_operation", "conversion_operation", "after_sales_governance");
        };
    }

    private List<Map<String, Object>> responsibilityMappingCatalog() {
        return List.of(
                responsibilityMapping("gmv", "平台运营", List.of("经营分析 / 平台运营", "类目运营", "增长运营", "售后治理"),
                        "经营异常总览群", "GMV 类异常先由平台运营确认影响面，再按证据拆给类目、增长或售后。"),
                responsibilityMapping("order_count", "经营分析 / 平台运营", List.of("增长运营", "类目运营"),
                        "经营分析群", "订单量类异常先由经营分析拆 GMV 公式和订单口径，再判断是否需要增长或类目协同。"),
                responsibilityMapping("category_gmv", "类目运营", List.of("商家运营", "平台运营", "售后治理"),
                        "类目运营群", "品类 GMV 类异常优先分发给类目运营，下钻商品、商家、库存和活动资源。"),
                responsibilityMapping("refund_rate", "售后治理", List.of("类目运营", "商家运营", "客服/物流侧"),
                        "售后治理群", "退款类异常优先分发给售后治理，确认退款原因、履约、客服和商品质量。"),
                responsibilityMapping("conversion_rate", "增长运营", List.of("商品转化运营", "平台运营"),
                        "增长运营群", "转化类异常优先分发给增长运营，拆浏览、下单、支付漏斗和渠道活动。"),
                responsibilityMapping("active_users", "增长运营", List.of("平台运营", "类目运营"),
                        "增长运营群", "用户规模类异常优先分发给增长运营，确认流量、人群触达和活动曝光。"),
                responsibilityMapping("avg_order_value", "经营分析 / 类目运营", List.of("平台运营", "类目运营"),
                        "经营分析群", "客单价类异常先由经营分析拆交易结构，再由类目运营看商品结构和价格带。")
        );
    }

    private Map<String, Object> responsibilityMapping(String metricId,
                                                      String primaryOwner,
                                                      List<String> handoffRoles,
                                                      String notificationChannel,
                                                      String assignmentRule) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("metric_id", metricId);
        mapping.put("primary_owner", primaryOwner);
        mapping.put("handoff_roles", handoffRoles);
        mapping.put("notification_channel", notificationChannel);
        mapping.put("assignment_rule", assignmentRule);
        return mapping;
    }

    private Map<String, Object> responsibilityMappingFor(String metricId, String primaryOwner) {
        return responsibilityMappingCatalog().stream()
                .filter(mapping -> metricId.equals(String.valueOf(mapping.get("metric_id"))))
                .findFirst()
                .orElseGet(() -> responsibilityMapping(metricId, primaryOwner, List.of("经营分析", "平台运营"),
                        "经营异常总览群", "未命中特定指标配置时，先进入经营分析复核，再决定责任分发。"));
    }

    private Map<String, Object> anomaly(String id,
                                        String statDate,
                                        String metricId,
                                        String metricName,
                                        String scopeType,
                                        String scopeName,
                                        Number currentValue,
                                        Number previousValue,
                                        Number delta,
                                        Number deltaRate,
                                        String severity,
                                        String status,
                                        String ownerRole,
                                        String confidence,
                                        String source,
                                        String rootCauseQuestion,
                                        String description,
                                        String nextStep) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("stat_date", statDate);
        item.put("metric_id", metricId);
        item.put("metric_name", metricName);
        item.put("scope_type", scopeType);
        item.put("scope_name", scopeName);
        item.put("current_value", currentValue);
        item.put("previous_value", previousValue);
        item.put("delta", delta);
        item.put("delta_rate", deltaRate);
        item.put("severity", severity);
        item.put("status", status);
        item.put("owner_role", ownerRole);
        item.put("confidence", confidence);
        item.put("source", source);
        item.put("root_cause_question", rootCauseQuestion);
        item.put("analysis_route", analysisRouteFor(item));
        item.put("description", description);
        item.put("next_step", nextStep);
        item.put("source_system", "metric_monitor");
        item.put("source_system_label", "BI / 监控规则 / 指标巡检");
        item.put("analyze_endpoint", "/api/ecommerce/anomalies/" + id + "/analyze");
        return item;
    }

    private String focusedQuestion(Map<String, Object> anomaly, Map<String, Object> analysisRoute) {
        String baseQuestion = String.valueOf(anomaly.getOrDefault("root_cause_question", "")).trim();
        if (baseQuestion.isEmpty()) {
            baseQuestion = anomaly.getOrDefault("stat_date", "2018-08-29")
                    + " " + anomaly.getOrDefault("scope_name", "华东")
                    + " GMV 为什么跌了？";
        }
        Object routeName = analysisRoute.getOrDefault("route_name", "完整 GMV root cause");
        Object focus = analysisRoute.getOrDefault("focus_summary", "按标准 root cause 链路排查");
        return baseQuestion + " 请按异常类型【" + routeName + "】优先分析：" + focus;
    }

    private Map<String, Object> plannerInputFor(Map<String, Object> anomaly, Map<String, Object> analysisRoute) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("planner_role", "你是电商运营异常分析 Agent 的 planner，只生成候选分析计划，不直接执行业务动作。");
        input.put("business_goal", "发现异常 -> 拆解维度 -> 形成解释 -> 人工确认后推动处理");
        Map<String, Object> currentAnomaly = new LinkedHashMap<>();
        currentAnomaly.put("anomaly_id", anomaly.getOrDefault("id", ""));
        currentAnomaly.put("metric_id", anomaly.getOrDefault("metric_id", ""));
        currentAnomaly.put("metric_name", anomaly.getOrDefault("metric_name", ""));
        currentAnomaly.put("scope_type", anomaly.getOrDefault("scope_type", ""));
        currentAnomaly.put("scope_name", anomaly.getOrDefault("scope_name", ""));
        currentAnomaly.put("stat_date", anomaly.getOrDefault("stat_date", ""));
        currentAnomaly.put("current_value", anomaly.getOrDefault("current_value", 0));
        currentAnomaly.put("previous_value", anomaly.getOrDefault("previous_value", 0));
        currentAnomaly.put("delta_rate", anomaly.getOrDefault("delta_rate", 0));
        currentAnomaly.put("confidence", anomaly.getOrDefault("confidence", "中"));
        currentAnomaly.put("process_status", anomaly.getOrDefault("status", "待确认"));
        input.put("current_anomaly", currentAnomaly);
        input.put("selected_route", Map.of(
                "route_key", analysisRoute.getOrDefault("route_key", ""),
                "route_name", analysisRoute.getOrDefault("route_name", ""),
                "focus_summary", analysisRoute.getOrDefault("focus_summary", ""),
                "priority_dimensions", analysisRoute.getOrDefault("priority_dimensions", List.of()),
                "deprioritized_dimensions", analysisRoute.getOrDefault("deprioritized_dimensions", List.of()),
                "primary_owner", analysisRoute.getOrDefault("primary_owner", ""),
                "handoff_roles", analysisRoute.getOrDefault("handoff_roles", List.of()),
                "human_sop", analysisRoute.getOrDefault("human_sop", List.of())
        ));
        input.put("relevant_business_rules", relevantBusinessRules(analysisRoute));
        input.put("allowed_tools", analysisRoute.getOrDefault("tool_priority", List.of()));
        input.put("forbidden_actions", List.of(
                "不能发送飞书或任何外部通知",
                "不能修改 workflow 状态",
                "不能关闭异常或标记误报",
                "不能调用非白名单 Tool",
                "不能生成写库、删库或非只读查询动作"
        ));
        input.put("output_schema", Map.of(
                "selected_tools", "string[]，只能来自 allowed_tools",
                "execution_plan", "array，每步包含 step/tool/reason/expected_evidence",
                "cause_ranking", "array，按主因优先级输出 cause/evidence/priority",
                "action_routing", "array，按 P0/P1/P2 输出 owner_name/problem/evidence/next_action",
                "notification_decision", "object，必须包含 recommendation、confidence、manual_confirmation_required=true"
        ));
        input.put("prompt", plannerPromptFor(input));
        return input;
    }

    private Map<String, Object> interpretationInputFor(Map<String, Object> anomaly,
                                                       Map<String, Object> analysisRoute,
                                                       Map<String, Object> evidenceExecution) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("interpreter_role", "你是电商运营异常分析 Agent 的证据解释器。Java 已经按固定 route 完成只读查数，你只负责解释证据。");
        input.put("business_goal", "基于结构化证据输出主因排序、责任分发、通知建议和追问建议。");
        input.put("current_anomaly", extractCurrentAnomaly(anomaly));
        input.put("selected_route", Map.of(
                "route_key", analysisRoute.getOrDefault("route_key", ""),
                "route_name", analysisRoute.getOrDefault("route_name", ""),
                "focus_summary", analysisRoute.getOrDefault("focus_summary", ""),
                "priority_dimensions", analysisRoute.getOrDefault("priority_dimensions", List.of()),
                "primary_owner", analysisRoute.getOrDefault("primary_owner", ""),
                "handoff_roles", analysisRoute.getOrDefault("handoff_roles", List.of()),
                "human_sop", analysisRoute.getOrDefault("human_sop", List.of())
        ));
        input.put("business_rules", relevantBusinessRules(analysisRoute));
        input.put("tool_evidence", evidenceExecution.getOrDefault("tool_results", List.of()));
        input.put("fixed_route_tools", evidenceExecution.getOrDefault("fixed_route_tools", List.of()));
        input.put("skipped_route_tools", evidenceExecution.getOrDefault("skipped_route_tools", List.of()));
        input.put("required_output", Map.of(
                "cause_ranking", "array，按影响大小输出 cause/evidence/priority/confidence",
                "action_routing", "array，按 P0/P1/P2 输出 owner_name/problem/evidence/next_action/downstream_object",
                "notification_decision", "object，包含 recommendation/confidence/manual_confirmation_required/reason",
                "follow_up_suggestions", "array，给业务方下一步追问或下钻建议"
        ));
        input.put("forbidden_actions", List.of(
                "不能发送飞书",
                "不能修改 workflow 状态",
                "不能关闭异常或标记误报",
                "不能声称已经完成业务处理",
                "不能编造 tool_evidence 中不存在的数字"
        ));
        input.put("prompt", interpretationPromptFor(input));
        return input;
    }

    private Map<String, Object> extractCurrentAnomaly(Map<String, Object> anomaly) {
        Map<String, Object> currentAnomaly = new LinkedHashMap<>();
        currentAnomaly.put("anomaly_id", anomaly.getOrDefault("id", ""));
        currentAnomaly.put("metric_id", anomaly.getOrDefault("metric_id", ""));
        currentAnomaly.put("metric_name", anomaly.getOrDefault("metric_name", ""));
        currentAnomaly.put("scope_type", anomaly.getOrDefault("scope_type", ""));
        currentAnomaly.put("scope_name", anomaly.getOrDefault("scope_name", ""));
        currentAnomaly.put("stat_date", anomaly.getOrDefault("stat_date", ""));
        currentAnomaly.put("current_value", anomaly.getOrDefault("current_value", 0));
        currentAnomaly.put("previous_value", anomaly.getOrDefault("previous_value", 0));
        currentAnomaly.put("delta_rate", anomaly.getOrDefault("delta_rate", 0));
        currentAnomaly.put("confidence", anomaly.getOrDefault("confidence", "中"));
        currentAnomaly.put("process_status", anomaly.getOrDefault("status", "待确认"));
        return currentAnomaly;
    }

    private List<String> relevantBusinessRules(Map<String, Object> analysisRoute) {
        String routeKey = String.valueOf(analysisRoute.getOrDefault("route_key", ""));
        return switch (routeKey) {
            case "order_structure_first" -> List.of(
                    "订单量异常优先拆订单量、支付订单量、支付口径和浏览到支付转化。",
                    "只有当交易结构无法解释时，才把退款和售后作为补充线索。",
                    "责任分发优先给经营分析 / 平台运营，必要时协同增长运营。"
            );
            case "category_drilldown_first" -> List.of(
                    "品类 GMV 异常优先定位拖累品类，再继续下钻商品、商家、库存和活动资源。",
                    "如果少数商品或商家贡献了主要下滑，应优先派给类目运营。",
                    "用户规模和泛漏斗不作为第一优先级，除非品类证据不足。"
            );
            case "refund_governance_first" -> List.of(
                    "退款异常优先看退款金额、退款订单、退款原因和退款集中对象。",
                    "若退款集中在某品类/商家，需要协同类目运营或商家运营。",
                    "售后治理负责最终确认是否涉及物流、客服、商品质量或退换货规则。"
            );
            case "growth_funnel_first" -> List.of(
                    "用户/转化异常优先拆 DAU、活跃买家、浏览、下单和支付漏斗。",
                    "先判断是流量变少，还是转化变弱，再判断是否需要活动、渠道或投放介入。",
                    "责任分发优先给增长运营，必要时协同平台运营和类目运营。"
            );
            case "aov_structure_first" -> List.of(
                    "客单价异常优先拆客单价、商品结构、价格带和高客单商品贡献。",
                    "如果客单价变化来自商品结构，应协同类目运营。",
                    "如果变化来自优惠力度或活动门槛，应协同平台运营。"
            );
            default -> List.of(
                    "GMV = 支付订单量 × 客单价。",
                    "GMV 异常优先拆区域、订单结构、用户规模、品类、漏斗和退款。",
                    "先找主拖累，再做原因排序和责任分发。",
                    "通知必须人工确认后发送，模型只能给通知建议。"
            );
        };
    }

    private String plannerPromptFor(Map<String, Object> plannerInput) {
        Map<String, Object> anomaly = extractNestedMap(plannerInput, "current_anomaly");
        Map<String, Object> route = extractNestedMap(plannerInput, "selected_route");
        return """
                你是电商运营异常分析 Agent 的 planner。请基于当前异常生成候选分析计划。

                当前异常：
                - anomaly_id: %s
                - 指标: %s / %s
                - 范围: %s %s
                - 日期: %s
                - 变化: %s -> %s
                - 置信度: %s

                分析路线：
                - %s
                - %s

                业务规则：
                %s

                可用 Tool：
                %s

                安全边界：
                - 只能输出 JSON plan
                - 不能发送飞书
                - 不能修改 workflow
                - 不能关闭异常或标记误报
                - notification_decision.manual_confirmation_required 必须为 true

                请输出字段：selected_tools, execution_plan, cause_ranking, action_routing, notification_decision。
                """.formatted(
                anomaly.getOrDefault("anomaly_id", ""),
                anomaly.getOrDefault("metric_id", ""),
                anomaly.getOrDefault("metric_name", ""),
                anomaly.getOrDefault("scope_type", ""),
                anomaly.getOrDefault("scope_name", ""),
                anomaly.getOrDefault("stat_date", ""),
                anomaly.getOrDefault("previous_value", ""),
                anomaly.getOrDefault("current_value", ""),
                anomaly.getOrDefault("confidence", ""),
                route.getOrDefault("route_name", ""),
                route.getOrDefault("focus_summary", ""),
                String.join("\n", relevantBusinessRules(route).stream().map(rule -> "- " + rule).toList()),
                plannerInput.getOrDefault("allowed_tools", List.of())
        );
    }

    private String interpretationPromptFor(Map<String, Object> interpretationInput) {
        Map<String, Object> anomaly = extractNestedMap(interpretationInput, "current_anomaly");
        Map<String, Object> route = extractNestedMap(interpretationInput, "selected_route");
        return """
                你是电商运营异常分析 Agent 的证据解释器。Java 后端已经按固定业务 route 执行了只读 Tool。

                当前异常：
                - anomaly_id: %s
                - 指标: %s / %s
                - 范围: %s %s
                - 日期: %s
                - 变化: %s -> %s
                - 置信度: %s

                固定分析路线：
                - %s
                - %s

                业务规则：
                %s

                已执行 Tool 证据：
                %s

                请只基于上面的证据输出 JSON：
                - cause_ranking
                - action_routing
                - notification_decision
                - follow_up_suggestions

                安全要求：
                - notification_decision.manual_confirmation_required 必须为 true
                - 不要声称已经发飞书
                - 不要声称已经关闭异常
                - 不要编造证据里没有的数字
                """.formatted(
                anomaly.getOrDefault("anomaly_id", ""),
                anomaly.getOrDefault("metric_id", ""),
                anomaly.getOrDefault("metric_name", ""),
                anomaly.getOrDefault("scope_type", ""),
                anomaly.getOrDefault("scope_name", ""),
                anomaly.getOrDefault("stat_date", ""),
                anomaly.getOrDefault("previous_value", ""),
                anomaly.getOrDefault("current_value", ""),
                anomaly.getOrDefault("confidence", ""),
                route.getOrDefault("route_name", ""),
                route.getOrDefault("focus_summary", ""),
                String.join("\n", relevantBusinessRules(route).stream().map(rule -> "- " + rule).toList()),
                interpretationInput.getOrDefault("tool_evidence", List.of())
        );
    }

    private List<?> listValue(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private Map<String, Object> analysisRouteFor(Map<String, Object> anomaly) {
        String metricId = String.valueOf(anomaly.getOrDefault("metric_id", "gmv"));
        return switch (metricId) {
            case "order_count" -> analysisRoute(
                    "order_structure_first",
                    "订单量异常优先",
                    "先判断订单量/支付订单数是否为主拖累，再看流量与转化，不把退款放在第一优先级。",
                    List.of("OrderQueryTool", "FunnelAnalysisTool", "UserMetricTool", "RegionPerformanceQueryTool"),
                    List.of("订单量", "支付订单数", "浏览/支付转化", "流量与活动承接"),
                    List.of("退款原因", "售后履约"),
                    "经营分析 / 平台运营",
                    List.of(
                            "确认订单量下降是否只发生在目标区域或目标品类。",
                            "拆 GMV = 支付订单量 × 客单价，判断主拖累是单量还是客单价。",
                            "检查浏览、下单、支付链路，确认是否存在流量或支付承接问题。",
                            "需要时拉增长运营补充渠道、活动曝光和人群触达证据。"
                    )
            );
            case "category_gmv" -> analysisRoute(
                    "category_drilldown_first",
                    "品类 GMV 异常优先",
                    "先定位拖累品类，再下钻商品、商家、库存和活动资源。",
                    List.of("CategoryRankTool", "ProductSellerDrilldown", "OrderQueryTool", "RefundAnalysisTool"),
                    List.of("品类 GMV", "商品/SKU", "商家", "库存与活动资源"),
                    List.of("全局用户规模", "泛漏斗转化"),
                    "类目运营",
                    List.of(
                            "确认品类下滑是否为当前 GMV 回落的主要贡献项。",
                            "下钻重点商品和商家，判断是否由少数对象集中拖累。",
                            "检查库存、价格、活动资源和商家履约。",
                            "输出需要类目运营优先处理的商品/商家清单。"
                    )
            );
            case "refund_rate" -> analysisRoute(
                    "refund_governance_first",
                    "退款率异常优先",
                    "先看退款金额、退款订单和退款原因，再判断是否涉及物流、客服或商品质量。",
                    List.of("RefundAnalysisTool", "CategoryRankTool", "ProductSellerDrilldown", "OrderQueryTool"),
                    List.of("退款金额", "退款订单", "退款原因", "物流/客服/质量线索"),
                    List.of("流量增长", "活动曝光"),
                    "售后治理",
                    List.of(
                            "确认退款率或退款金额是否超过正常波动。",
                            "定位退款集中的品类、商品和商家。",
                            "检查物流履约、客服处理、商品质量和退换货原因。",
                            "判断是否需要售后治理介入或商家整改。"
                    )
            );
            case "conversion_rate", "active_users" -> analysisRoute(
                    "growth_funnel_first",
                    "用户/转化异常优先",
                    "先看用户规模、活跃买家和浏览到支付转化，再判断渠道、活动、人群触达和投放节奏。",
                    List.of("UserMetricTool", "FunnelAnalysisTool", "OrderQueryTool", "RegionPerformanceQueryTool"),
                    List.of("DAU", "活跃买家", "浏览到支付转化", "渠道/活动/人群"),
                    List.of("退款原因", "售后履约"),
                    "增长运营",
                    List.of(
                            "确认用户规模或活跃买家是否同步回落。",
                            "拆浏览、下单、支付漏斗，定位掉点环节。",
                            "检查渠道流量、活动曝光、人群触达和投放节奏。",
                            "输出是否需要增长运营补投放、补活动或修复承接链路。"
                    )
            );
            case "avg_order_value" -> analysisRoute(
                    "aov_structure_first",
                    "客单价异常优先",
                    "先看客单价变化，再拆商品结构、优惠力度和高客单商品贡献。",
                    List.of("OrderQueryTool", "CategoryRankTool", "ProductSellerDrilldown", "RegionPerformanceQueryTool"),
                    List.of("客单价", "商品结构", "价格带", "优惠活动"),
                    List.of("退款原因", "泛用户规模"),
                    "经营分析 / 类目运营",
                    List.of(
                            "确认 GMV 回落是否主要由客单价下降造成。",
                            "拆商品结构、价格带和高客单商品贡献变化。",
                            "检查优惠力度、满减门槛和活动资源变化。",
                            "判断是否需要类目运营调整商品池或促销策略。"
                    )
            );
            default -> analysisRoute(
                    "gmv_root_cause_full",
                    "GMV 完整 root cause",
                    "完整拆 GMV、订单、用户、品类、漏斗和退款，先找主拖累，再做责任分发。",
                    List.of("RegionPerformanceQueryTool", "OrderQueryTool", "UserMetricTool", "CategoryRankTool", "FunnelAnalysisTool", "RefundAnalysisTool"),
                    List.of("GMV", "订单量", "客单价", "用户规模", "品类", "漏斗", "退款"),
                    List.of(),
                    "平台运营",
                    List.of(
                            "确认异常影响范围：目标区域、目标品类还是全站同步波动。",
                            "拆 GMV = 支付订单量 × 客单价，定位交易结构问题。",
                            "下钻用户、品类、漏斗和退款线索，形成主因排序。",
                            "按证据把问题分发给平台运营、类目运营、增长运营或售后治理。"
                    )
            );
        };
    }

    private Map<String, Object> analysisRoute(String routeKey,
                                              String routeName,
                                              String focusSummary,
                                              List<String> toolPriority,
                                              List<String> priorityDimensions,
                                              List<String> deprioritizedDimensions,
                                              String primaryOwner,
                                              List<String> humanSop) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("route_key", routeKey);
        route.put("route_name", routeName);
        route.put("focus_summary", focusSummary);
        route.put("tool_priority", toolPriority);
        route.put("priority_dimensions", priorityDimensions);
        route.put("deprioritized_dimensions", deprioritizedDimensions);
        route.put("primary_owner", primaryOwner);
        route.put("human_sop", humanSop);
        route.put("section_priority", sectionPriorityFor(routeKey));
        route.put("owner_priority", ownerPriorityFor(routeKey));
        String metricId = routeKeyToMetricId(routeKey);
        Map<String, Object> responsibilityMapping = responsibilityMappingFor(metricId, primaryOwner);
        route.put("responsibility_mapping", responsibilityMapping);
        route.put("handoff_roles", responsibilityMapping.getOrDefault("handoff_roles", List.of()));
        route.put("notification_channel", responsibilityMapping.getOrDefault("notification_channel", "经营异常总览群"));
        route.put("assignment_rule", responsibilityMapping.getOrDefault("assignment_rule", "先由数据分析师复核，再派发给主责角色。"));
        route.put("agent_policy", "单 Agent 根据 anomaly_id 选择分析重点；人负责定义 SOP 和最终确认。");
        return route;
    }

    private String routeKeyToMetricId(String routeKey) {
        return switch (routeKey) {
            case "order_structure_first" -> "order_count";
            case "category_drilldown_first" -> "category_gmv";
            case "refund_governance_first" -> "refund_rate";
            case "growth_funnel_first" -> "active_users";
            case "aov_structure_first" -> "avg_order_value";
            default -> "gmv";
        };
    }
}
