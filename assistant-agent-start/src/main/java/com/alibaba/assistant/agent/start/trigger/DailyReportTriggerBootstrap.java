package com.alibaba.assistant.agent.start.trigger;

import com.alibaba.assistant.agent.extension.trigger.manager.TriggerManager;
import com.alibaba.assistant.agent.extension.trigger.model.ScheduleMode;
import com.alibaba.assistant.agent.extension.trigger.model.SourceType;
import com.alibaba.assistant.agent.extension.trigger.model.TriggerDefinition;
import com.alibaba.assistant.agent.start.config.AppOperationsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(100)
public class DailyReportTriggerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DailyReportTriggerBootstrap.class);
    private static final String DAILY_REPORT_EVENT_KEY = "ecommerce_daily_report";
    private static final String DAILY_REPORT_FUNCTION = "send_daily_report";

    private final AppOperationsProperties operationsProperties;
    private final TriggerManager triggerManager;

    public DailyReportTriggerBootstrap(AppOperationsProperties operationsProperties,
                                       TriggerManager triggerManager) {
        this.operationsProperties = operationsProperties;
        this.triggerManager = triggerManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppOperationsProperties.DailyReport config = operationsProperties.getDailyReport();
        if (!config.isEnabled()) {
            log.info("DailyReportTriggerBootstrap#run - reason=daily report trigger disabled");
            return;
        }

        boolean exists = triggerManager.list(SourceType.GLOBAL, config.getSourceId()).stream()
                .anyMatch(definition -> DAILY_REPORT_EVENT_KEY.equals(definition.getEventKey()));
        if (exists) {
            log.info("DailyReportTriggerBootstrap#run - reason=daily report trigger already exists, sourceId={}",
                    config.getSourceId());
            return;
        }

        TriggerDefinition definition = new TriggerDefinition();
        definition.setName("daily_ecommerce_report");
        definition.setDescription("每天定时汇总昨日 GMV / 订单 / 用户核心指标并发送通知");
        definition.setSourceType(SourceType.GLOBAL);
        definition.setSourceId(config.getSourceId());
        definition.setCreatedBy(config.getCreatedBy());
        definition.setEventProtocol("time");
        definition.setEventKey(DAILY_REPORT_EVENT_KEY);
        definition.setScheduleMode(ScheduleMode.CRON);
        definition.setScheduleValue(config.getCron());
        definition.setExecuteFunction(DAILY_REPORT_FUNCTION);
        definition.setRequireConfirmation(false);
        definition.setMaxRetries(1);
        definition.setRetryDelay(1000L);
        definition.setFunctionCodeSnapshot(Map.of(
                DAILY_REPORT_FUNCTION,
                """
                def send_daily_report():
                    import datetime

                    report_date = (datetime.date.today() - datetime.timedelta(days=1)).isoformat()
                    overview = GmvQueryTool(stat_date=report_date)
                    orders = OrderQueryTool(stat_date=report_date)
                    users = UserMetricTool(stat_date=report_date)
                    regions = RegionPerformanceQueryTool(stat_date=report_date)
                    categories = CategoryRankTool(stat_date=report_date)

                    overview_row = overview["rows"][0] if overview.get("rows") else {}
                    order_row = orders["rows"][0] if orders.get("rows") else {}
                    user_row = users["rows"][0] if users.get("rows") else {}
                    region_row = regions["rows"][0] if regions.get("rows") else {}
                    category_row = categories["rows"][0] if categories.get("rows") else {}

                    message = (
                        f"电商经营日报｜{report_date}\\n"
                        f"一、核心看板\\n"
                        f"- GMV: {overview_row.get('gmv', 'N/A')}\\n"
                        f"- 支付订单量: {order_row.get('paid_order_count', order_row.get('order_count', 'N/A'))}\\n"
                        f"- 客单价: {order_row.get('avg_order_value', 'N/A')}\\n"
                        f"- DAU: {user_row.get('dau', 'N/A')}\\n"
                        f"- 活跃买家: {user_row.get('active_buyer_count', user_row.get('active_buyers', 'N/A'))}\\n\\n"
                        f"二、结构观察\\n"
                        f"- Top 区域: {region_row.get('region_name', 'N/A')} / GMV={region_row.get('gmv', 'N/A')}\\n"
                        f"- Top 品类: {category_row.get('category_l1', category_row.get('category_name', 'N/A'))} / GMV={category_row.get('gmv', 'N/A')}\\n\\n"
                        f"三、系统备注\\n"
                        f"- 当前日报只做“昨日核心经营概览”，异常归因由单独巡检 trigger 负责\\n"
                        f"- 数据源: 大盘={overview.get('data_source', 'unknown')} / 订单={orders.get('data_source', 'unknown')} / "
                        f"用户={users.get('data_source', 'unknown')} / 区域={regions.get('data_source', 'unknown')} / "
                        f"品类={categories.get('data_source', 'unknown')}"
                    )

                    return send_notification(title="电商经营日报", text=message)
                """
        ));
        definition.setParameters(new LinkedHashMap<>());
        definition.getMetadata().put("analysis_space", "overview");
        definition.getMetadata().put("reply_channel", "send_notification");

        String triggerId = triggerManager.subscribe(definition);
        log.info("DailyReportTriggerBootstrap#run - reason=daily report trigger subscribed, triggerId={}, cron={}",
                triggerId, config.getCron());
    }
}
