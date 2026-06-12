package com.alibaba.assistant.agent.start.tool;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import com.alibaba.assistant.agent.common.tools.CodeExample;

@Component
public class DateRangeCalcTool extends AbstractWarehouseQueryCodeactTool {
    public DateRangeCalcTool() {
        super(
            "DateRangeCalcTool",
            "计算两个日期之间相差的天数",
            List.of(
                requiredString("start_date", "开始日期，格式 YYYY-MM-DD"),
                requiredString("end_date",   "结束日期，格式 YYYY-MM-DD")
            ),
            "date_tools",
            "日期计算工具集合",
            new CodeExample(
                "计算两个日期相差天数",
                "result = DateRangeCalcTool(start_date=\"2024-01-01\", end_date=\"2024-12-31\")\nprint(result['days'])",
                "返回两个日期之间的天数差"
            )
        );
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        String startDate = (String) params.get("start_date");
        String endDate = (String) params.get("end_date");
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        long days = ChronoUnit.DAYS.between(start, end);
        return Map.of("success", true, "days", days);
    }
}
