package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.extension.trigger.manager.TriggerManager;
import com.alibaba.assistant.agent.extension.trigger.model.SourceType;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.alibaba.assistant.agent.start.config.AppOperationsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(200)
public class DailyReportTriggerValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DailyReportTriggerValidator.class);
    private static final String DAILY_REPORT_EVENT_KEY = "ecommerce_daily_report";

    private final AppOperationsProperties operationsProperties;
    private final TriggerManager triggerManager;

    public DailyReportTriggerValidator(AppOperationsProperties operationsProperties,
                                       TriggerManager triggerManager) {
        this.operationsProperties = operationsProperties;
        this.triggerManager = triggerManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!operationsProperties.getDailyReport().isEnabled()) {
            return;
        }
        List<TriggerDefinition> definitions = triggerManager.list(SourceType.GLOBAL,
                operationsProperties.getDailyReport().getSourceId());
        TriggerDefinition definition = definitions.stream()
                .filter(item -> DAILY_REPORT_EVENT_KEY.equals(item.getEventKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Daily report trigger was not registered"));

        if (!"send_daily_report".equals(definition.getExecuteFunction())) {
            throw new IllegalStateException("Daily report trigger execute function drifted");
        }

        Object replyChannel = definition.getMetadata().get("reply_channel");
        if (replyChannel == null || !"send_notification".equals(String.valueOf(replyChannel))) {
            throw new IllegalStateException("Daily report trigger reply channel drifted");
        }

        String functionCode = definition.getFunctionCodeSnapshot().get("send_daily_report");
        if (functionCode == null
                || !functionCode.contains("RegionPerformanceQueryTool")
                || !functionCode.contains("CategoryRankTool")
                || !functionCode.contains("send_notification(title=\"电商经营日报\"")) {
            throw new IllegalStateException("Daily report trigger format drifted");
        }

        log.info("DailyReportTriggerValidator#run - reason=daily report trigger validated, triggerId={}, cron={}",
                definition.getTriggerId(), definition.getScheduleValue());
    }
}
