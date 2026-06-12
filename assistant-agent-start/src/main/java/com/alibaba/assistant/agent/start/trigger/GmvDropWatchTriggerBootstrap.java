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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(110)
public class GmvDropWatchTriggerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GmvDropWatchTriggerBootstrap.class);
    private static final String EVENT_KEY = "ecommerce_gmv_drop_watch";
    private static final String FUNCTION = "watch_gmv_drop";

    private final AppOperationsProperties operationsProperties;
    private final TriggerManager triggerManager;

    public GmvDropWatchTriggerBootstrap(AppOperationsProperties operationsProperties,
                                        TriggerManager triggerManager) {
        this.operationsProperties = operationsProperties;
        this.triggerManager = triggerManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppOperationsProperties.GmvDropWatch config = operationsProperties.getGmvDropWatch();
        if (!config.isEnabled()) {
            log.info("GmvDropWatchTriggerBootstrap#run - reason=gmv drop watch disabled");
            return;
        }

        boolean exists = triggerManager.list(SourceType.GLOBAL, config.getSourceId()).stream()
                .anyMatch(definition -> EVENT_KEY.equals(definition.getEventKey()));
        if (exists) {
            log.info("GmvDropWatchTriggerBootstrap#run - reason=gmv drop watch already exists, sourceId={}",
                    config.getSourceId());
            return;
        }

        TriggerDefinition definition = new TriggerDefinition();
        definition.setName("gmv_drop_watch");
        definition.setDescription("定时巡检昨日 GMV 是否显著下滑，并自动输出初步归因");
        definition.setSourceType(SourceType.GLOBAL);
        definition.setSourceId(config.getSourceId());
        definition.setCreatedBy(config.getCreatedBy());
        definition.setEventProtocol("time");
        definition.setEventKey(EVENT_KEY);
        definition.setScheduleMode(ScheduleMode.CRON);
        definition.setScheduleValue(config.getCron());
        definition.setExecuteFunction(FUNCTION);
        definition.setRequireConfirmation(false);
        definition.setMaxRetries(1);
        definition.setRetryDelay(1000L);
        definition.setFunctionCodeSnapshot(Map.of(
                FUNCTION,
                buildFunctionCode(config)
        ));
        definition.setParameters(new LinkedHashMap<>());
        definition.getMetadata().put("analysis_space", "anomaly_detection");
        definition.getMetadata().put("reply_channel", "send_notification");
        definition.getMetadata().put("relative_drop_threshold", String.valueOf(config.getRelativeDropThreshold()));
        definition.getMetadata().put("week_over_week_drop_threshold",
                String.valueOf(config.getWeekOverWeekDropThreshold()));
        definition.getMetadata().put("rolling_average_window_days",
                String.valueOf(config.getRollingAverageWindowDays()));
        definition.getMetadata().put("rolling_average_drop_threshold",
                String.valueOf(config.getRollingAverageDropThreshold()));
        definition.getMetadata().put("year_over_year_drop_threshold",
                String.valueOf(config.getYearOverYearDropThreshold()));
        definition.getMetadata().put("special_period_threshold_multiplier",
                String.valueOf(config.getSpecialPeriodThresholdMultiplier()));
        definition.getMetadata().put("demo_report_date", normalizeDemoReportDate(config));
        definition.getMetadata().put("holiday_dates", String.join(",", config.getHolidayDates()));
        definition.getMetadata().put("activity_windows", String.join(",", config.getActivityWindows()));

        String triggerId = triggerManager.subscribe(definition);
        log.info("GmvDropWatchTriggerBootstrap#run - reason=gmv drop watch subscribed, triggerId={}, cron={}, dayThreshold={}, weekThreshold={}, rollingWindow={}, rollingThreshold={}, yoyThreshold={}",
                triggerId, config.getCron(), config.getRelativeDropThreshold(), config.getWeekOverWeekDropThreshold(),
                config.getRollingAverageWindowDays(), config.getRollingAverageDropThreshold(),
                config.getYearOverYearDropThreshold());
    }

    private String buildFunctionCode(AppOperationsProperties.GmvDropWatch config) {
        String holidayDatesLiteral = pythonStringList(config.getHolidayDates());
        String activityWindowsLiteral = pythonStringList(config.getActivityWindows());
        String demoReportDateLiteral = pythonString(normalizeDemoReportDate(config));
        return """
                def watch_gmv_drop():
                    import datetime
                    import json

                    def _call_tool(tool_name, args):
                        result_json = __tool_registry__.callTool(tool_name, json.dumps(args))
                        if result_json is None or result_json == "":
                            return {}
                        return json.loads(str(result_json))

                    def _fetch_gmv(stat_date_str):
                        result = _call_tool("GmvQueryTool", {"stat_date": stat_date_str})
                        row = result["rows"][0] if result.get("rows") else {}
                        return float(row.get("gmv", 0) or 0), result.get("data_source", "unknown")

                    def _safe_drop(base_value, current_value):
                        if base_value <= 0:
                            return None
                        return (base_value - current_value) / base_value

                    def _is_special_period(report_date_value):
                        date_text = report_date_value.isoformat()
                        if date_text in HOLIDAY_DATES:
                            return True, "holiday"
                        for window in ACTIVITY_WINDOWS:
                            if ":" not in window:
                                continue
                            start_text, end_text = window.split(":", 1)
                            try:
                                start_date = datetime.date.fromisoformat(start_text)
                                end_date = datetime.date.fromisoformat(end_text)
                            except ValueError:
                                continue
                            if start_date <= report_date_value <= end_date:
                                return True, f"activity_window({start_text}~{end_text})"
                        return False, None

                    demo_report_date = DEMO_REPORT_DATE.strip()
                    if demo_report_date:
                        report_date = datetime.date.fromisoformat(demo_report_date)
                    else:
                        report_date = datetime.date.today() - datetime.timedelta(days=1)
                    baseline_date = report_date - datetime.timedelta(days=1)
                    last_week_same_day = report_date - datetime.timedelta(days=7)
                    last_year_same_day = report_date - datetime.timedelta(days=365)
                    report_date_str = report_date.isoformat()
                    baseline_date_str = baseline_date.isoformat()
                    last_week_same_day_str = last_week_same_day.isoformat()
                    last_year_same_day_str = last_year_same_day.isoformat()

                    current_gmv, current_source = _fetch_gmv(report_date_str)
                    baseline_gmv, _ = _fetch_gmv(baseline_date_str)
                    weekly_gmv, _ = _fetch_gmv(last_week_same_day_str)
                    yoy_gmv, _ = _fetch_gmv(last_year_same_day_str)

                    rolling_values = []
                    rolling_window_days = __ROLLING_WINDOW_DAYS__
                    for offset in range(1, rolling_window_days + 1):
                        past_date = report_date - datetime.timedelta(days=offset)
                        past_gmv, _ = _fetch_gmv(past_date.isoformat())
                        if past_gmv > 0:
                            rolling_values.append(past_gmv)

                    if baseline_gmv <= 0:
                        return {
                            "status": "skipped",
                            "reason": "baseline_missing",
                            "report_date": report_date_str,
                            "report_date_mode": "demo_fixed" if demo_report_date else "yesterday"
                        }
                    if weekly_gmv <= 0:
                        return {
                            "status": "skipped",
                            "reason": "weekly_baseline_missing",
                            "report_date": report_date_str,
                            "report_date_mode": "demo_fixed" if demo_report_date else "yesterday",
                            "baseline_date": baseline_date_str,
                            "last_week_same_day": last_week_same_day_str
                        }
                    if not rolling_values:
                        return {
                            "status": "skipped",
                            "reason": "rolling_baseline_missing",
                            "report_date": report_date_str,
                            "report_date_mode": "demo_fixed" if demo_report_date else "yesterday",
                            "rolling_window_days": rolling_window_days
                        }

                    rolling_avg_gmv = sum(rolling_values) / len(rolling_values)
                    drop_ratio = _safe_drop(baseline_gmv, current_gmv)
                    week_drop_ratio = _safe_drop(weekly_gmv, current_gmv)
                    rolling_drop_ratio = _safe_drop(rolling_avg_gmv, current_gmv)
                    yoy_drop_ratio = _safe_drop(yoy_gmv, current_gmv) if yoy_gmv > 0 else None

                    threshold = __DAY_THRESHOLD__
                    week_threshold = __WEEK_THRESHOLD__
                    rolling_threshold = __ROLLING_THRESHOLD__
                    yoy_threshold = __YOY_THRESHOLD__
                    multiplier = __SPECIAL_MULTIPLIER__
                    special_period, special_reason = _is_special_period(report_date)
                    if special_period:
                        threshold = threshold * multiplier
                        week_threshold = week_threshold * multiplier
                        rolling_threshold = rolling_threshold * multiplier
                        yoy_threshold = yoy_threshold * multiplier

                    day_signal = drop_ratio is not None and drop_ratio >= threshold
                    week_signal = week_drop_ratio is not None and week_drop_ratio >= week_threshold
                    rolling_signal = rolling_drop_ratio is not None and rolling_drop_ratio >= rolling_threshold
                    yoy_signal = yoy_drop_ratio is not None and yoy_drop_ratio >= yoy_threshold
                    yoy_gmv_display = f"{yoy_gmv:.2f}" if yoy_gmv > 0 else "N/A"
                    yoy_drop_display = f"{yoy_drop_ratio:.1%}" if yoy_drop_ratio is not None else "N/A"

                    if not (day_signal and week_signal and rolling_signal):
                        return {
                            "status": "healthy",
                            "report_date": report_date_str,
                            "report_date_mode": "demo_fixed" if demo_report_date else "yesterday",
                            "baseline_date": baseline_date_str,
                            "last_week_same_day": last_week_same_day_str,
                            "drop_ratio": round(drop_ratio, 4),
                            "week_drop_ratio": round(week_drop_ratio, 4),
                            "rolling_avg_gmv": round(rolling_avg_gmv, 2),
                            "rolling_drop_ratio": round(rolling_drop_ratio, 4) if rolling_drop_ratio is not None else None,
                            "yoy_date": last_year_same_day_str,
                            "yoy_drop_ratio": round(yoy_drop_ratio, 4) if yoy_drop_ratio is not None else None,
                            "threshold": threshold,
                            "week_threshold": week_threshold,
                            "rolling_threshold": rolling_threshold,
                            "yoy_threshold": yoy_threshold,
                            "special_period": special_period,
                            "special_reason": special_reason
                        }

                    root_cause_result = _call_tool("RootCauseWorkflowTool", {"stat_date": report_date_str, "region_name": "华东"})
                    draft = root_cause_result.get("notification_draft", {}) if root_cause_result else {}
                    draft_title = draft.get("title", f"【经营异常待排查】{report_date_str} GMV 回落")
                    draft_body = draft.get("body", "root cause workflow did not return notification body")
                    trigger_context = (
                        f"\\n\\n触发判定：\\n"
                        f"- 前日 GMV: {baseline_gmv:.2f}，昨日 GMV: {current_gmv:.2f}，日环比跌幅: {drop_ratio:.1%}（阈值 {threshold:.0%}）\\n"
                        f"- 上周同天 GMV: {weekly_gmv:.2f}，周同比跌幅: {week_drop_ratio:.1%}（阈值 {week_threshold:.0%}）\\n"
                        f"- 最近 {len(rolling_values)} 天均值: {rolling_avg_gmv:.2f}，近均值跌幅: {rolling_drop_ratio:.1%}（阈值 {rolling_threshold:.0%}）\\n"
                        f"- 去年同期 GMV: {yoy_gmv_display}，去年同期跌幅: {yoy_drop_display}（阈值 {yoy_threshold:.0%}）\\n"
                        f"- 特殊时期: {'是' if special_period else '否'}{f' / {special_reason}' if special_reason else ''}\\n"
                        f"- 发送策略：Trigger 只负责异常判定，root cause、责任分发和通知草稿复用 RootCauseWorkflowTool；Reply 负责幂等、去重和降级。"
                    )
                    message = draft_body + trigger_context

                    _call_tool("send_notification", {"title": draft_title, "text": message})
                    return {
                        "status": "alerted",
                        "report_date": report_date_str,
                        "report_date_mode": "demo_fixed" if demo_report_date else "yesterday",
                        "baseline_date": baseline_date_str,
                        "last_week_same_day": last_week_same_day_str,
                        "last_year_same_day": last_year_same_day_str,
                        "drop_ratio": round(drop_ratio, 4),
                        "week_drop_ratio": round(week_drop_ratio, 4),
                        "rolling_drop_ratio": round(rolling_drop_ratio, 4) if rolling_drop_ratio is not None else None,
                        "yoy_drop_ratio": round(yoy_drop_ratio, 4) if yoy_drop_ratio is not None else None,
                        "special_period": special_period,
                        "special_reason": special_reason,
                        "gmv_source": current_source,
                        "root_cause_success": root_cause_result.get("success", False) if root_cause_result else False,
                        "notification_title": draft_title,
                        "notification_severity": draft.get("severity", "unknown") if draft else "unknown"
                    }
                """.replace("__DAY_THRESHOLD__", String.valueOf(config.getRelativeDropThreshold()))
                        .replace("__WEEK_THRESHOLD__", String.valueOf(config.getWeekOverWeekDropThreshold()))
                        .replace("__ROLLING_WINDOW_DAYS__", String.valueOf(config.getRollingAverageWindowDays()))
                        .replace("__ROLLING_THRESHOLD__", String.valueOf(config.getRollingAverageDropThreshold()))
                        .replace("__YOY_THRESHOLD__", String.valueOf(config.getYearOverYearDropThreshold()))
                        .replace("__SPECIAL_MULTIPLIER__", String.valueOf(config.getSpecialPeriodThresholdMultiplier()))
                        .replace("DEMO_REPORT_DATE", demoReportDateLiteral)
                        .replace("HOLIDAY_DATES", holidayDatesLiteral)
                        .replace("ACTIVITY_WINDOWS", activityWindowsLiteral);
    }

    private String normalizeDemoReportDate(AppOperationsProperties.GmvDropWatch config) {
        String demoReportDate = config.getDemoReportDate();
        if (demoReportDate == null || demoReportDate.isBlank()) {
            return "";
        }
        String normalized = demoReportDate.trim();
        LocalDate.parse(normalized);
        return normalized;
    }

    private String pythonString(String value) {
        return "'" + value.replace("'", "\\'") + "'";
    }

    private String pythonStringList(Iterable<String> values) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) {
                builder.append(", ");
            }
            builder.append('\'').append(value.replace("'", "\\'")).append('\'');
            first = false;
        }
        builder.append(']');
        return builder.toString();
    }
}
