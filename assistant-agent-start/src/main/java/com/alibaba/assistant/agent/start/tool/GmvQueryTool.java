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
public class GmvQueryTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public GmvQueryTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "GmvQueryTool",
                "查询某一天的大盘 GMV、支付订单数、活跃买家数、DAU 和退款率，适合日报和高频看数场景。",
                List.of(requiredString("stat_date", "统计日期，格式 YYYY-MM-DD")),
                "ecommerce_overview_tools",
                "电商大盘高频看数工具集合",
                new CodeExample(
                        "查看昨天的大盘 GMV",
                        "result = GmvQueryTool(stat_date=\"2026-05-17\")\nprint(result['rows'][0]['gmv'])",
                        "返回指定日期的大盘核心指标"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        LocalDate statDate = resolveDate(params);
        List<Map<String, Object>> rows = warehouseQueryService.getDailyCoreMetrics(statDate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "overview");
        result.put("query_type", "daily_core_metrics");
        result.put("stat_date", statDate.toString());
        result.put("data_source", resolveDataSource(rows, warehouseQueryService.getOverviewAnalyticsSource()));
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
