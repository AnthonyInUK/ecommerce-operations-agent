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
public class ReleaseImpactTool extends AbstractWarehouseQueryCodeactTool {

    private final JdbcWarehouseQueryService warehouseQueryService;

    public ReleaseImpactTool(JdbcWarehouseQueryService warehouseQueryService) {
        super(
                "ReleaseImpactTool",
                "对比某次发布前后的订单量、GMV 和客单价，自动划定「发布前 N 天」和「发布后 N 天」两个窗口并做差异计算，帮助快速判断本次上线是正向还是负向影响。",
                List.of(
                        requiredString("release_date", "发布日期，即上线当天，格式 YYYY-MM-DD"),
                        optionalString("window_days", "前后各对比几天，默认 7，最大 30"),
                        optionalString("region_name", "可选区域名，例如华东、华南"),
                        optionalString("category_l1", "可选一级品类，例如女装、家电")
                ),
                "ecommerce_ops_tools",
                "电商运营诊断工具集合",
                new CodeExample(
                        "查看 2026-05-17 上线对订单量的影响",
                        "result = ReleaseImpactTool(release_date=\"2026-05-17\", window_days=\"1\")\nprint(result['before'], result['after'], result['delta'])",
                        "返回发布前后的 GMV 和订单量对比及变化率，帮助判断上线是否带来业务影响"
                )
        );
        this.warehouseQueryService = warehouseQueryService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String releaseDateStr = resolveOptionalString(params, "release_date");
        LocalDate releaseDate = releaseDateStr != null ? LocalDate.parse(releaseDateStr) : LocalDate.of(2026, 5, 17);

        String windowStr = resolveOptionalString(params, "window_days");
        int windowDays = 7;
        if (windowStr != null) {
            try {
                int parsed = Integer.parseInt(windowStr);
                windowDays = Math.max(1, Math.min(30, parsed));
            } catch (NumberFormatException ignored) {
            }
        }

        String regionName = resolveOptionalString(params, "region_name");
        String categoryName = resolveOptionalString(params, "category_l1");

        LocalDate beforeStart = releaseDate.minusDays(windowDays);
        LocalDate beforeEnd = releaseDate.minusDays(1);
        LocalDate afterEnd = releaseDate.plusDays(windowDays - 1);

        List<Map<String, Object>> beforeRows = warehouseQueryService.getOrderRangeSummary(
                beforeStart, beforeEnd, regionName, categoryName);
        List<Map<String, Object>> afterRows = warehouseQueryService.getOrderRangeSummary(
                releaseDate, afterEnd, regionName, categoryName);

        Map<String, Object> before = beforeRows.isEmpty() ? Map.of("order_count", 0, "gmv", 0.0, "avg_order_value", 0.0) : beforeRows.get(0);
        Map<String, Object> after = afterRows.isEmpty() ? Map.of("order_count", 0, "gmv", 0.0, "avg_order_value", 0.0) : afterRows.get(0);

        double beforeGmv = toDouble(before.get("gmv"));
        double afterGmv = toDouble(after.get("gmv"));
        double beforeOrders = toDouble(before.get("order_count"));
        double afterOrders = toDouble(after.get("order_count"));

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("gmv_change", afterGmv - beforeGmv);
        delta.put("gmv_change_pct", beforeGmv == 0 ? null : (afterGmv - beforeGmv) / beforeGmv * 100);
        delta.put("order_count_change", afterOrders - beforeOrders);
        delta.put("order_count_change_pct", beforeOrders == 0 ? null : (afterOrders - beforeOrders) / beforeOrders * 100);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("analysis_space", "release_impact");
        result.put("query_type", "release_before_after_comparison");
        result.put("release_date", releaseDate.toString());
        result.put("window_days", windowDays);
        result.put("before_period", beforeStart + " ~ " + beforeEnd);
        result.put("after_period", releaseDate + " ~ " + afterEnd);
        if (regionName != null) result.put("region_name", regionName);
        if (categoryName != null) result.put("category_l1", categoryName);
        result.put("data_source", before.getOrDefault("source_tag", "demo_seed").toString());
        result.put("before", before);
        result.put("after", after);
        result.put("delta", delta);
        return result;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0.0;
        try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException e) { return 0.0; }
    }
}
