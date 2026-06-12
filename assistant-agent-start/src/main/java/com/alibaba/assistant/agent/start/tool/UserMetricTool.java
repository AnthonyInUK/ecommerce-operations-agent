package com.alibaba.assistant.agent.start.tool;

import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.start.config.JdbcWarehouseQueryService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class UserMetricTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public UserMetricTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "UserMetricTool",
                "查询某一天的用户增长核心指标，可返回大盘 DAU、活跃买家数、买家激活率，也可查看渠道维度的活跃和转化表现，适合判断成交变化是否来自用户规模或渠道质量变化。",
                List.of(
                        requiredString("stat_date", "统计日期，格式 YYYY-MM-DD"),
                        optionalString("region_name", "可选区域名，例如华东、华南；默认查大盘"),
                        optionalString("view_type", "可选视图类型：overview 或 channel，默认 overview"),
                        optionalInteger("limit", "当 view_type=channel 时返回前 N 个渠道", 5)
                ),
                "ecommerce_growth_tools",
                "电商用户增长和渠道质量分析工具集合",
                new CodeExample(
                        "查看昨天的用户增长指标和渠道转化",
                        "overview = UserMetricTool(stat_date=\"2026-05-17\")\nchannels = UserMetricTool(stat_date=\"2026-05-17\", view_type=\"channel\", limit=3)\nprint(overview['rows'][0]['dau'], channels['rows'][0]['channel_code'])",
                        "返回大盘用户指标或渠道维度用户质量指标"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        LocalDate statDate = resolveDate(params);
        String regionName = resolveOptionalString(params, "region_name");
        String viewType = resolveOptionalString(params, "view_type");
        String safeViewType = viewType == null ? "overview" : viewType;
        Integer limit = resolveLimit(params, 5);

        List<Map<String, Object>> rows = "channel".equalsIgnoreCase(safeViewType)
                ? warehouseQueryService.getChannelUserMetrics(statDate, limit)
                : warehouseQueryService.getUserDailyMetrics(statDate, regionName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "growth");
        result.put("query_type", "channel".equalsIgnoreCase(safeViewType) ? "channel_user_metrics" : "daily_user_metrics");
        result.put("view_type", safeViewType);
        result.put("stat_date", statDate.toString());
        if (regionName != null) {
            result.put("region_name", regionName);
        }
        result.put("rows", rows);
        result.put("count", rows.size());
        return result;
    }
}
