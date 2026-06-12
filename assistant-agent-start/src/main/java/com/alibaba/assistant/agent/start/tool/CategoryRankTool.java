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
public class CategoryRankTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public CategoryRankTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "CategoryRankTool",
                "查询某一天按 GMV 排序的品类表现，返回品类 GMV、支付订单数和退款率，适合品类排行和结构拆解场景。",
                List.of(
                        requiredString("stat_date", "统计日期，格式 YYYY-MM-DD"),
                        optionalString("region_name", "可选区域名，例如华东、华南"),
                        optionalInteger("limit", "返回前 N 个品类，默认 10", 10)
                ),
                "ecommerce_category_tools",
                "电商品类排行与结构分析工具集合",
                new CodeExample(
                        "获取某天品类 GMV 排行",
                        "result = CategoryRankTool(stat_date=\"2026-05-17\", limit=3)\nfor row in result['rows']:\n    print(row['category_l1'], row['gmv'])",
                        "返回指定日期的品类排行榜"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        LocalDate statDate = resolveDate(params);
        Integer limit = resolveLimit(params, 10);
        String regionName = resolveOptionalString(params, "region_name");
        List<Map<String, Object>> rows = warehouseQueryService.getCategoryDailyMetrics(statDate, limit, regionName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "overview");
        result.put("query_type", "category_rank");
        result.put("stat_date", statDate.toString());
        result.put("data_source", resolveDataSource(rows, warehouseQueryService.getOverviewAnalyticsSource()));
        if (regionName != null) {
            result.put("region_name", regionName);
        }
        result.put("limit", limit);
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
