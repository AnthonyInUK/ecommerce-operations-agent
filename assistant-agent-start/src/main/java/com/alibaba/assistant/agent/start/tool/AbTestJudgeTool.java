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
public class AbTestJudgeTool extends AbstractWarehouseQueryCodeactTool {

    private static final double MIN_RELATIVE_LIFT = 0.01;

    private final JdbcWarehouseQueryService warehouseQueryService;

    public AbTestJudgeTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "AbTestJudgeTool",
                "查询 A/B 实验两个分组的关键指标（转化率、订单量、GMV），自动判断哪个分组胜出并给出相对提升幅度，省去人工对比表格的步骤，适合实验结束后的快速结论输出。",
                List.of(
                        requiredString("experiment_name", "实验名称，与数据库中录入的名称完全一致，例如「新版结算页_v2」"),
                        requiredString("start_date", "实验开始日期，格式 YYYY-MM-DD"),
                        requiredString("end_date", "实验结束日期，格式 YYYY-MM-DD"),
                        optionalString("metric", "判断胜负所用指标：conversion_rate（转化率，默认）、order_count（订单量）或 gmv（总销售额）")
                ),
                "ecommerce_ops_tools",
                "电商运营诊断工具集合",
                new CodeExample(
                        "判断新版结算页 A/B 实验的胜者",
                        "result = AbTestJudgeTool(experiment_name=\"新版结算页_v2\", start_date=\"2026-05-11\", end_date=\"2026-05-17\", metric=\"conversion_rate\")\nprint(result['winner'], result['lift_pct'])",
                        "返回胜出分组、对照组和实验组的核心指标，以及相对提升百分比，直接输出实验结论"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String experimentName = resolveOptionalString(params, "experiment_name");
        if (experimentName == null) experimentName = "新版结算页_v2";

        String startStr = resolveOptionalString(params, "start_date");
        LocalDate startDate = startStr != null ? LocalDate.parse(startStr) : LocalDate.of(2026, 5, 11);
        String endStr = resolveOptionalString(params, "end_date");
        LocalDate endDate = endStr != null ? LocalDate.parse(endStr) : LocalDate.of(2026, 5, 17);

        String metric = resolveOptionalString(params, "metric");
        if (metric == null) metric = "conversion_rate";

        List<Map<String, Object>> rows = warehouseQueryService.getAbTestMetrics(experimentName, startDate, endDate);

        if (rows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("success", false);
            empty.put("message", "未找到实验数据，请检查实验名称和日期范围");
            empty.put("experiment_name", experimentName);
            return empty;
        }

        Map<String, Object> groupA = null;
        Map<String, Object> groupB = null;
        for (Map<String, Object> row : rows) {
            String gid = String.valueOf(row.get("group_id"));
            if ("A".equalsIgnoreCase(gid)) groupA = row;
            else if ("B".equalsIgnoreCase(gid)) groupB = row;
        }

        if (groupA == null || groupB == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("experiment_name", experimentName);
            result.put("message", "数据中分组不完整，仅找到以下分组");
            result.put("groups", rows);
            return result;
        }

        double metricA = extractMetric(groupA, metric);
        double metricB = extractMetric(groupB, metric);

        String winner;
        double liftPct;
        String conclusion;
        if (Math.abs(metricA - metricB) / Math.max(metricA, 1e-9) < MIN_RELATIVE_LIFT) {
            winner = "无显著差异";
            liftPct = (metricB - metricA) / Math.max(metricA, 1e-9) * 100;
            conclusion = "两组差异小于 1%，建议延长实验观察期或增大样本量";
        } else if (metricB > metricA) {
            winner = "B";
            liftPct = (metricB - metricA) / metricA * 100;
            conclusion = "B 组（实验组）在 " + metricLabel(metric) + " 上显著优于 A 组（对照组），建议全量上线 B 方案";
        } else {
            winner = "A";
            liftPct = (metricA - metricB) / metricB * 100;
            conclusion = "A 组（对照组）表现更好，建议保持现有方案，回滚 B 方案的改动";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "ab_test");
        result.put("query_type", "ab_test_judgment");
        result.put("experiment_name", experimentName);
        result.put("start_date", startDate.toString());
        result.put("end_date", endDate.toString());
        result.put("judge_metric", metric);
        result.put("data_source", "demo_seed");
        result.put("group_a", groupA);
        result.put("group_b", groupB);
        result.put("winner", winner);
        result.put("lift_pct", Math.round(liftPct * 100.0) / 100.0);
        result.put("conclusion", conclusion);
        return result;
    }

    private double extractMetric(Map<String, Object> group, String metric) {
        return switch (metric) {
            case "order_count" -> toDouble(group.get("total_orders"));
            case "gmv" -> toDouble(group.get("total_gmv"));
            default -> toDouble(group.get("avg_conversion_rate"));
        };
    }

    private String metricLabel(String metric) {
        return switch (metric) {
            case "order_count" -> "订单量";
            case "gmv" -> "GMV";
            default -> "转化率";
        };
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0.0;
        try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException e) { return 0.0; }
    }
}
