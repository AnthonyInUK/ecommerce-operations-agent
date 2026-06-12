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
public class FunnelAnalysisTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public FunnelAnalysisTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "FunnelAnalysisTool",
                "分析某一天浏览到支付的漏斗，可按区域和品类筛选，适合判断问题出在流量还是转化。",
                List.of(
                        requiredString("stat_date", "统计日期，格式 YYYY-MM-DD"),
                        optionalString("region_name", "可选区域名，例如华东、华南"),
                        optionalString("category_l1", "可选品类名，例如家电、女装")
                ),
                "ecommerce_funnel_tools",
                "电商漏斗分析工具集合",
                new CodeExample(
                        "分析华东某天的漏斗",
                        "result = FunnelAnalysisTool(stat_date=\"2026-05-17\", region_name=\"华东\")\nprint(result['rows'][0]['view_to_pay_rate'])",
                        "返回指定日期和区域的浏览到支付转化"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        LocalDate statDate = resolveDate(params);
        String regionName = resolveOptionalString(params, "region_name");
        String categoryName = resolveOptionalString(params, "category_l1");
        List<Map<String, Object>> rawRows = warehouseQueryService.getFunnelMetrics(statDate, regionName, categoryName);

        List<Map<String, Object>> rows = rawRows.stream().map(row -> {
            Number viewCount = (Number) row.getOrDefault("VIEW_COUNT", row.getOrDefault("view_count", 0));
            Number payCount = (Number) row.getOrDefault("PAY_COUNT", row.getOrDefault("pay_count", 0));
            double view = viewCount == null ? 0D : viewCount.doubleValue();
            double pay = payCount == null ? 0D : payCount.doubleValue();
            double rate = view == 0 ? 0D : pay / view;

            Map<String, Object> normalized = new LinkedHashMap<>(row);
            normalized.put("view_count", (int) view);
            normalized.put("pay_count", (int) pay);
            normalized.put("view_to_pay_rate", rate);
            return normalized;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "growth");
        result.put("query_type", "view_pay_funnel");
        result.put("stat_date", statDate.toString());
        if (regionName != null) {
            result.put("region_name", regionName);
        }
        if (categoryName != null) {
            result.put("category_l1", categoryName);
        }
        result.put("rows", rows);
        result.put("count", rows.size());
        return result;
    }
}
