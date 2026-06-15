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
public class ApiErrorRateTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public ApiErrorRateTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "ApiErrorRateTool",
                "查询结算 / 支付 API 的 HTTP 400 和 500 错误率，可按日期范围和接口名称筛选，返回每天每个接口的请求量、错误数和错误率，适合在上线后快速确认接口是否出现异常。",
                List.of(
                        requiredString("start_date", "查询起始日期，格式 YYYY-MM-DD"),
                        optionalString("end_date", "查询结束日期，格式 YYYY-MM-DD，不填默认等于 start_date"),
                        optionalString("api_name", "可选接口路径，例如 /api/checkout/submit；不填则返回所有接口")
                ),
                "ecommerce_ops_tools",
                "电商运营诊断工具集合",
                new CodeExample(
                        "查询近 7 天结算接口的错误率",
                        "result = ApiErrorRateTool(start_date=\"2026-05-11\", end_date=\"2026-05-17\", api_name=\"/api/checkout/submit\")\nprint(result['rows'])",
                        "返回按天统计的错误数和错误率，帮助快速判断上线后 API 是否出现异常"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String startStr = resolveOptionalString(params, "start_date");
        LocalDate startDate = startStr != null ? LocalDate.parse(startStr) : LocalDate.of(2026, 5, 17);
        String endStr = resolveOptionalString(params, "end_date");
        LocalDate endDate = endStr != null ? LocalDate.parse(endStr) : startDate;
        String apiName = resolveOptionalString(params, "api_name");

        List<Map<String, Object>> rows = warehouseQueryService.getApiErrorMetrics(startDate, endDate, apiName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "api_error_rate");
        result.put("query_type", "api_error_metrics");
        result.put("start_date", startDate.toString());
        result.put("end_date", endDate.toString());
        if (apiName != null) {
            result.put("api_name", apiName);
        }
        result.put("data_source", "demo_seed");
        result.put("rows", rows);
        result.put("count", rows.size());
        return result;
    }
}
