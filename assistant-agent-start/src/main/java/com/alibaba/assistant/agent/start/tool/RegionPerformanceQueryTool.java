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
public class RegionPerformanceQueryTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public RegionPerformanceQueryTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "RegionPerformanceQueryTool",
                "查询某一天各区域或指定区域的 GMV、支付订单数和退款率，适合区域对比和大区复盘场景。",
                List.of(
                        requiredString("stat_date", "统计日期，格式 YYYY-MM-DD"),
                        optionalString("region_name", "可选区域名，例如华东、华南")
                ),
                "ecommerce_region_tools",
                "电商区域分析工具集合",
                new CodeExample(
                        "对比某天各区域表现",
                        "result = RegionPerformanceQueryTool(stat_date=\"2026-05-17\")\nfor row in result['rows']:\n    print(row['region_name'], row['gmv'])",
                        "返回指定日期各区域的表现数据"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        LocalDate statDate = resolveDate(params);
        String regionName = resolveOptionalString(params, "region_name");
        List<Map<String, Object>> rows = warehouseQueryService.getRegionDailyMetrics(statDate, regionName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "overview");
        result.put("query_type", regionName == null ? "region_daily_metrics" : "single_region_daily_metrics");
        result.put("stat_date", statDate.toString());
        result.put("data_source", resolveDataSource(rows, warehouseQueryService.getOverviewAnalyticsSource()));
        if (regionName != null) {
            result.put("region_name", regionName);
        }
        result.put("rows", rows);
        result.put("count", rows.size());
        return result;
    }

    private String resolveDataSource(List<Map<String, Object>> rows, String fallback) {
        if (rows == null || rows.isEmpty()) {
            return normalizeDataSource(fallback);
        }
        Object sourceTag = rows.get(0).get("source_tag");
        return sourceTag == null ? normalizeDataSource(fallback) : normalizeDataSource(String.valueOf(sourceTag));
    }

    private String normalizeDataSource(String sourceTag) {
        if (sourceTag != null && sourceTag.startsWith("olist")) {
            return "olist_public_dataset";
        }
        return sourceTag;
    }
}
