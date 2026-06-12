package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.extension.trigger.manager.TriggerManager;
import com.alibaba.assistant.agent.extension.trigger.model.SourceType;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.alibaba.assistant.agent.start.config.AppOperationsProperties;
import com.alibaba.assistant.agent.start.service.EcommerceQuestionAnswerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(210)
public class GmvDropWatchTriggerValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GmvDropWatchTriggerValidator.class);
    private static final String EVENT_KEY = "ecommerce_gmv_drop_watch";

    private final AppOperationsProperties operationsProperties;
    private final TriggerManager triggerManager;
    private final EcommerceQuestionAnswerService questionAnswerService;

    public GmvDropWatchTriggerValidator(AppOperationsProperties operationsProperties,
                                        TriggerManager triggerManager,
                                        EcommerceQuestionAnswerService questionAnswerService) {
        this.operationsProperties = operationsProperties;
        this.triggerManager = triggerManager;
        this.questionAnswerService = questionAnswerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppOperationsProperties.GmvDropWatch config = operationsProperties.getGmvDropWatch();
        if (!config.isEnabled()) {
            return;
        }
        List<TriggerDefinition> definitions = triggerManager.list(SourceType.GLOBAL, config.getSourceId());
        TriggerDefinition definition = definitions.stream()
                .filter(item -> EVENT_KEY.equals(item.getEventKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GMV drop watch trigger was not registered"));

        if (!"watch_gmv_drop".equals(definition.getExecuteFunction())) {
            throw new IllegalStateException("GMV drop watch execute function drifted");
        }

        Object thresholdValue = definition.getMetadata().get("relative_drop_threshold");
        String threshold = thresholdValue == null ? null : String.valueOf(thresholdValue);
        if (threshold == null || threshold.isBlank()) {
            throw new IllegalStateException("GMV drop watch threshold metadata missing");
        }

        Object weekThresholdValue = definition.getMetadata().get("week_over_week_drop_threshold");
        String weekThreshold = weekThresholdValue == null ? null : String.valueOf(weekThresholdValue);
        if (weekThreshold == null || weekThreshold.isBlank()) {
            throw new IllegalStateException("GMV drop watch week-over-week threshold metadata missing");
        }

        requireMetadata(definition, "rolling_average_window_days");
        requireMetadata(definition, "rolling_average_drop_threshold");
        requireMetadata(definition, "year_over_year_drop_threshold");
        requireMetadata(definition, "special_period_threshold_multiplier");
        if (!definition.getMetadata().containsKey("demo_report_date")) {
            throw new IllegalStateException("GMV drop watch demo report date metadata missing");
        }

        String functionCode = definition.getFunctionCodeSnapshot().get("watch_gmv_drop");
        if (functionCode == null
                || !functionCode.contains("demo_report_date")
                || !functionCode.contains("report_date_mode")
                || !functionCode.contains("__tool_registry__.callTool")
                || !functionCode.contains("\"GmvQueryTool\"")
                || !functionCode.contains("\"RootCauseWorkflowTool\"")
                || !functionCode.contains("\"send_notification\"")
                || !functionCode.contains("notification_draft")
                || (!functionCode.contains("action") && !functionCode.contains("责任分发"))
                || !functionCode.contains("root_cause_success")) {
            throw new IllegalStateException("GMV drop watch root cause workflow drifted");
        }

        validateDemoRootCauseChain(definition);

        log.info("GmvDropWatchTriggerValidator#run - reason=gmv drop watch validated, triggerId={}, cron={}, dayThreshold={}, weekThreshold={}, rollingWindow={}, rollingThreshold={}, yoyThreshold={}, demoReportDate={}, rootCauseWorkflow=enabled",
                definition.getTriggerId(), definition.getScheduleValue(), threshold, weekThreshold,
                definition.getMetadata().get("rolling_average_window_days"),
                definition.getMetadata().get("rolling_average_drop_threshold"),
                definition.getMetadata().get("year_over_year_drop_threshold"),
                definition.getMetadata().get("demo_report_date"));
    }

    private void requireMetadata(TriggerDefinition definition, String key) {
        Object value = definition.getMetadata().get(key);
        String text = value == null ? null : String.valueOf(value);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("GMV drop watch metadata missing: " + key);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateDemoRootCauseChain(TriggerDefinition definition) {
        Object demoDateValue = definition.getMetadata().get("demo_report_date");
        String demoDate = demoDateValue == null ? "" : String.valueOf(demoDateValue).trim();
        if (demoDate.isBlank()) {
            return;
        }
        Map<String, Object> result = questionAnswerService.answer(
                "validator-gmv-drop-watch-" + demoDate,
                demoDate + " 华东 GMV 为什么跌了？"
        );
        if (!Boolean.TRUE.equals(result.get("success"))) {
            throw new IllegalStateException("GMV drop watch demo root cause chain did not answer successfully");
        }
        Object rootCauseValue = result.get("root_cause");
        if (!(rootCauseValue instanceof Map<?, ?> rootCause)) {
            throw new IllegalStateException("GMV drop watch demo root cause result missing");
        }
        Object actionRouting = rootCause.get("action_routing");
        if (!(actionRouting instanceof List<?> routes) || routes.isEmpty()) {
            throw new IllegalStateException("GMV drop watch demo action routing missing");
        }
        Object notificationDraft = rootCause.get("notification_draft");
        if (!(notificationDraft instanceof Map<?, ?> draft)
                || draft.get("title") == null
                || draft.get("body") == null) {
            throw new IllegalStateException("GMV drop watch demo notification draft missing");
        }
        log.info("GmvDropWatchTriggerValidator#validateDemoRootCauseChain - reason=demo root cause chain validated, demoDate={}, routeCount={}, notificationTitle={}",
                demoDate, routes.size(), draft.get("title"));
    }
}
