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
public class RefundAnalysisTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public RefundAnalysisTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "RefundAnalysisTool",
                "分析某一天退款最重的品类，可按区域筛选，返回退款金额、关联 GMV 和退款金额占比，适合售后排查和异常归因。",
                List.of(
                        requiredString("stat_date", "统计日期，格式 YYYY-MM-DD"),
                        optionalString("region_name", "可选区域名，例如华东、华南"),
                        optionalInteger("limit", "返回前 N 个退款风险品类，默认 10", 10)
                ),
                "ecommerce_refund_tools",
                "电商退款分析工具集合",
                new CodeExample(
                        "查看华东退款最重的品类",
                        "result = RefundAnalysisTool(stat_date=\"2026-05-17\", region_name=\"华东\", limit=3)\nfor row in result['rows']:\n    print(row['category_l1'], row['refund_amount'])",
                        "返回指定日期和区域的退款风险品类"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        LocalDate statDate = resolveDate(params);
        String regionName = resolveOptionalString(params, "region_name");
        Integer limit = resolveLimit(params, 10);
        List<Map<String, Object>> rows = warehouseQueryService.getRefundCategoryBreakdown(statDate, regionName, limit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "refund");
        result.put("query_type", "refund_category_breakdown");
        result.put("stat_date", statDate.toString());
        if (regionName != null) {
            result.put("region_name", regionName);
        }
        result.put("limit", limit);
        result.put("rows", rows);
        result.put("count", rows.size());
        return result;
    }
}
