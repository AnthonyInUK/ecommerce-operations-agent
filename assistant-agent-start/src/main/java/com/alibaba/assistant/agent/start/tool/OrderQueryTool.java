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
public class OrderQueryTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public OrderQueryTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "OrderQueryTool",
                "查询某一天的订单结构指标，可按区域或品类筛选，返回订单量、支付订单量、退款订单量、总支付金额和客单价，适合拆解 GMV 变化到底是订单量还是客单价驱动。",
                List.of(
                        requiredString("stat_date", "统计日期，格式 YYYY-MM-DD"),
                        optionalString("region_name", "可选区域名，例如华东、华南"),
                        optionalString("category_l1", "可选一级品类，例如女装、家电")
                ),
                "ecommerce_order_tools",
                "电商订单结构分析工具集合",
                new CodeExample(
                        "查看华东某天的订单结构",
                        "result = OrderQueryTool(stat_date=\"2026-05-17\", region_name=\"华东\")\nprint(result['rows'][0]['order_count'], result['rows'][0]['avg_order_value'])",
                        "返回指定日期的订单结构指标，适合判断是订单量变化还是客单价变化"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        LocalDate statDate = resolveDate(params);
        String regionName = resolveOptionalString(params, "region_name");
        String categoryName = resolveOptionalString(params, "category_l1");
        List<Map<String, Object>> rows = warehouseQueryService.getOrderDailyMetrics(statDate, regionName, categoryName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "overview");
        result.put("query_type", "order_daily_metrics");
        result.put("stat_date", statDate.toString());
        if (regionName != null) {
            result.put("region_name", regionName);
        }
        if (categoryName != null) {
            result.put("category_l1", categoryName);
        }
        result.put("data_source", resolveDataSource(rows, warehouseQueryService.getOrderAnalyticsSource()));
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
