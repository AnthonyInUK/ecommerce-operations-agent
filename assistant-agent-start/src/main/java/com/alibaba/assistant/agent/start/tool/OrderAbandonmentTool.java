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
public class OrderAbandonmentTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public OrderAbandonmentTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "OrderAbandonmentTool",
                "查询下单后的弃单率和支付失败率，按日期范围、区域、品类筛选，数据从订单状态实时计算："
                        + "弃单率=未成功支付订单/全部下单，支付失败率=支付失败订单/发起过支付的订单。"
                        + "适合在掉单时快速判断是「用户放弃」还是「支付环节出故障」。",
                List.of(
                        requiredString("start_date", "查询起始日期，格式 YYYY-MM-DD"),
                        optionalString("end_date", "查询结束日期，格式 YYYY-MM-DD，不填默认等于 start_date"),
                        optionalString("region_name", "可选区域名，例如华东、华南"),
                        optionalString("category_l1", "可选一级品类，例如女装、家电")
                ),
                "ecommerce_ops_tools",
                "电商运营诊断工具集合",
                new CodeExample(
                        "查询某日下单的弃单率和支付失败率",
                        "result = OrderAbandonmentTool(start_date=\"2018-08-30\", end_date=\"2018-08-31\")\nprint(result['rows'][0]['abandonment_rate'], result['rows'][0]['payment_failure_rate'])",
                        "返回按天统计的弃单率和支付失败率，帮助区分掉单是用户放弃还是支付故障"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String startStr = resolveOptionalString(params, "start_date");
        LocalDate startDate = startStr != null ? LocalDate.parse(startStr) : LocalDate.of(2018, 8, 30);
        String endStr = resolveOptionalString(params, "end_date");
        LocalDate endDate = endStr != null ? LocalDate.parse(endStr) : startDate;
        String regionName = resolveOptionalString(params, "region_name");
        String categoryName = resolveOptionalString(params, "category_l1");

        List<Map<String, Object>> rows = warehouseQueryService.getOrderAbandonmentMetrics(
                startDate, endDate, regionName, categoryName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "order_health");
        result.put("query_type", "order_abandonment_metrics");
        result.put("start_date", startDate.toString());
        result.put("end_date", endDate.toString());
        if (regionName != null) {
            result.put("region_name", regionName);
        }
        if (categoryName != null) {
            result.put("category_l1", categoryName);
        }
        result.put("data_source", "demo_seed");
        result.put("rows", rows);
        result.put("count", rows.size());
        return result;
    }
}
